package com.musclenote.android.data.repository

import android.util.Log
import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.health.HealthSyncState
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accès à Health Connect (le SDK sur l'appareil, un faux dans les tests). Les méthodes `suspend`
 * lèvent une exception si Health Connect est indisponible ou refuse l'opération.
 */
interface HealthConnectGateway {
    /** Health Connect utilisable sur ce téléphone, relu à chaque appel. */
    fun isAvailable(): Boolean

    /** Types dont l'écriture est autorisée. */
    suspend fun grantedTypes(): Set<HealthRecordType>

    /**
     * Écrit [records] (un seul type par appel) ; un `clientRecordId` déjà présent est remplacé
     * ([version] plus grande). Renvoie le nombre d'enregistrements que Health Connect confirme,
     * dans l'ordre de [records].
     */
    suspend fun upsert(records: List<HealthRecordSpec>, version: Long): Int

    /** Supprime les enregistrements de MuscleNote de ce type et de ces `clientRecordId`. */
    suspend fun delete(type: HealthRecordType, clientRecordIds: List<String>)
}

/** Issue de « Envoyer l'historique ». */
sealed interface HealthHistoryResult {
    /**
     * Envoi traité : [sessions] et [weights] = écritures confirmées par Health Connect ;
     * [failures] = message d'erreur par type en échec ; [notGranted] = types en attente
     * d'autorisation.
     */
    data class Done(
        val sessions: Int,
        val weights: Int,
        val failures: Map<HealthRecordType, String> = emptyMap(),
        val notGranted: Set<HealthRecordType> = emptySet(),
    ) : HealthHistoryResult

    /** Rien n'a pu être tenté (Health Connect ne répond pas). */
    data class Failed(val detail: String) : HealthHistoryResult
}

/** Health Connect a confirmé moins d'écritures que demandé. */
class IncompleteHealthWriteException(confirmed: Int, requested: Int) : Exception("$confirmed/$requested")

/**
 * Envoi des séances et du poids à Health Connect (SPEC § 15.3, 🔄 D29, D30).
 *
 * Les changements détectés ([enqueue]) mettent des `clientRecordId` en attente, enregistrés sur
 * disque ; [sync] traite la file **type par type** (séances, puis poids : suppressions puis
 * écritures, par lots de [BATCH]) et ne garde dans le registre que ce que Health Connect a
 * confirmé, avec l'empreinte de ce qui a été écrit. Un type en échec n'empêche pas l'autre ; ce
 * qui n'est pas confirmé reste en attente pour le prochain essai. Un envoi à la fois. [records] =
 * enregistrements attendus d'après les données actuelles.
 */
class HealthSyncRepository(
    private val doc: JsonDocument<HealthSyncState>,
    private val time: TimeSource,
    private val gateway: HealthConnectGateway?,
    private val records: () -> Map<String, HealthRecordSpec>,
) {
    private val mutex = Mutex()
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(HealthSyncState.DEFAULT)
    val state: StateFlow<HealthSyncState> = _state.asStateFlow()

    private val _granted = MutableStateFlow<Set<HealthRecordType>>(emptySet())

    /** Types autorisés, relus à chaque envoi et à chaque affichage de Paramètres. */
    val granted: StateFlow<Set<HealthRecordType>> = _granted.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /** Envoi en cours (indicateur de « Envoyer l'historique »). */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _historyResult = MutableStateFlow<HealthHistoryResult?>(null)

    /** Résultat du dernier « Envoyer l'historique » de ce processus (encart sous la carte). */
    val historyResult: StateFlow<HealthHistoryResult?> = _historyResult.asStateFlow()

    /** Health Connect est utilisable : sinon l'option est masquée et rien n'est envoyé. */
    fun isAvailable(): Boolean = gateway?.isAvailable() == true

    suspend fun load() {
        mutex.withLock { _state.value = doc.read() ?: HealthSyncState.DEFAULT }
    }

    /** Relit les autorisations (retour sur Paramètres) ; inchangées si Health Connect ne répond pas. */
    suspend fun refreshPermissions(): Set<HealthRecordType> {
        val gw = gateway ?: return emptySet()
        if (!gw.isAvailable()) return emptySet()
        return try {
            gw.grantedTypes().also { _granted.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Autorisations illisibles", e)
            _granted.value
        }
    }

    /**
     * Retour de l'écran d'autorisation : au moins un type accordé → option activée et file
     * traitée (`true`) ; aucun → rien ne change (`false`).
     */
    suspend fun onPermissionsResult(granted: Set<HealthRecordType>): Boolean {
        _granted.value = granted
        if (granted.isEmpty()) return false
        update { it.copy(enabled = true) }
        sync()
        return true
    }

    /** Interrupteur coupé : plus aucun envoi ; Health Connect garde ce qui y est déjà. */
    suspend fun disable() {
        update { it.copy(enabled = false, pending = emptyList()) }
        _historyResult.value = null
    }

    /** Séances ou pesées modifiées : leurs enregistrements sont mis en attente (option activée). */
    suspend fun enqueue(clientRecordIds: Collection<String>) {
        if (clientRecordIds.isEmpty()) return
        update { state ->
            if (!state.enabled) state else state.copy(pending = (state.pending + clientRecordIds).distinct())
        }
    }

    /**
     * « Envoyer l'historique » : toutes les séances terminées et tous les poids, comptés d'après
     * ce que Health Connect confirme pendant cet envoi.
     */
    suspend fun sendHistory(): HealthHistoryResult {
        val result = syncMutex.withLock {
            enqueue(records().keys)
            val report = syncLocked()
            if (report == null) {
                HealthHistoryResult.Failed(_state.value.lastError ?: "Health Connect")
            } else {
                HealthHistoryResult.Done(
                    sessions = report.written[HealthRecordType.Session] ?: 0,
                    weights = report.written[HealthRecordType.Weight] ?: 0,
                    failures = report.failures,
                    notGranted = report.notGranted,
                )
            }
        }
        _historyResult.value = result
        return result
    }

    /**
     * Traite la file (option activée) : d'abord les enregistrements écrits dont la source a changé
     * ou disparu depuis (modifiés pendant que l'option était coupée, ou anciens identifiants) sont
     * remis en attente, avec les poids du jour qui remplacent ceux de la v1.0.5 ; puis, pour chaque
     * type autorisé, suppressions et écritures. Un type non autorisé reste en attente. `false` si
     * l'envoi n'a pas pu être tenté ou si un type a échoué.
     */
    suspend fun sync(): Boolean = syncMutex.withLock { syncLocked()?.failures?.isEmpty() == true }

    /** Ce qu'un envoi a fait : écritures et suppressions confirmées, échecs et types non autorisés. */
    private class SyncReport(
        val written: Map<HealthRecordType, Int> = emptyMap(),
        val deleted: Map<HealthRecordType, Int> = emptyMap(),
        val failures: Map<HealthRecordType, String> = emptyMap(),
        val notGranted: Set<HealthRecordType> = emptySet(),
    )

    /** Sous [syncMutex]. `null` : rien n'a pu être tenté (pas de Health Connect, option coupée, autorisations illisibles). */
    private suspend fun syncLocked(): SyncReport? {
        val gw = gateway ?: return null
        if (!_state.value.enabled) return null
        _syncing.value = true
        try {
            val desired = records()
            val sentBefore = _state.value.sent
            val stale = sentBefore.filter { (id, fingerprint) -> desired[id]?.fingerprint != fingerprint }.keys
            val replacements = HealthRecords.legacyWeightReplacements(sentBefore, time.zone()).filter { it in desired }
            enqueue(stale + replacements)

            val pending = _state.value.pending
            if (pending.isEmpty()) return SyncReport()
            val granted = try {
                gw.grantedTypes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect indisponible", e)
                val now = time.now().toEpochMilli()
                update { it.copy(lastError = e.describe(), lastErrorAt = now, lastErrorType = null) }
                return null
            }
            _granted.value = granted

            val version = maxOf(_state.value.lastVersion + 1, time.now().toEpochMilli())
            val sent = _state.value.sent.toMutableMap()
            // Identifiants d'autres apps : abandonnés.
            val done = pending.filter { HealthRecords.typeOf(it) == null }.toHashSet()
            val written = HashMap<HealthRecordType, Int>()
            val deleted = HashMap<HealthRecordType, Int>()
            val failures = LinkedHashMap<HealthRecordType, String>()
            val notGranted = LinkedHashSet<HealthRecordType>()

            for (type in HealthRecordType.entries) {
                val ofType = pending.filter { HealthRecords.typeOf(it) == type }
                if (ofType.isEmpty()) continue
                if (type !in granted) {
                    notGranted += type
                    continue
                }
                val toWrite = ofType.mapNotNull { desired[it] }
                // Absent des données : à supprimer s'il a été écrit, sinon il n'y a rien à faire.
                val gone = ofType.filter { it !in desired }
                done += gone.filter { it !in sent }
                try {
                    for (batch in gone.filter { it in sent }.chunked(BATCH)) {
                        gw.delete(type, batch)
                        batch.forEach { sent.remove(it) }
                        done += batch
                        deleted[type] = (deleted[type] ?: 0) + batch.size
                    }
                    for (batch in toWrite.chunked(BATCH)) {
                        val confirmed = gw.upsert(batch, version).coerceIn(0, batch.size)
                        for (record in batch.take(confirmed)) {
                            sent[record.clientRecordId] = record.fingerprint
                            done += record.clientRecordId
                        }
                        written[type] = (written[type] ?: 0) + confirmed
                        if (confirmed < batch.size) throw IncompleteHealthWriteException(confirmed, batch.size)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Envoi à Health Connect impossible ($type)", e)
                    failures[type] = e.describe()
                }
            }

            val now = time.now().toEpochMilli()
            val wrote = written.values.any { it > 0 } || deleted.values.any { it > 0 }
            val failure = failures.entries.firstOrNull()
            update { state ->
                state.copy(
                    sent = sent,
                    pending = state.pending.filterNot { it in done },
                    lastVersion = if (wrote) version else state.lastVersion,
                    lastSyncAt = if (wrote) now else state.lastSyncAt,
                    lastError = failure?.value ?: state.lastError.takeIf { !wrote },
                    lastErrorAt = if (failure != null) now else state.lastErrorAt.takeIf { !wrote },
                    lastErrorType = failure?.key ?: state.lastErrorType.takeIf { !wrote },
                )
            }
            return SyncReport(written, deleted, failures, notGranted)
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun update(transform: (HealthSyncState) -> HealthSyncState) {
        mutex.withLock {
            val current = _state.value
            val next = transform(current)
            if (next != current) {
                _state.value = next
                doc.save(next)
            }
        }
    }

    private fun Exception.describe(): String = localizedMessage ?: javaClass.simpleName

    companion object {
        /** Enregistrements par appel à Health Connect. */
        const val BATCH = 100
        private const val TAG = "HealthConnect"
    }
}
