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

    /** Écrit [records] ; un `clientRecordId` déjà présent est remplacé ([version] plus grande). */
    suspend fun upsert(records: List<HealthRecordSpec>, version: Long)

    /** Supprime les enregistrements de MuscleNote de ce type et de ces `clientRecordId`. */
    suspend fun delete(type: HealthRecordType, clientRecordIds: List<String>)
}

/** Issue de « Envoyer l'historique ». */
sealed interface HealthHistoryResult {
    data class Sent(val sessions: Int, val weights: Int) : HealthHistoryResult
    data class Failed(val detail: String) : HealthHistoryResult
}

/**
 * Envoi des séances et du poids à Health Connect (SPEC § 15.3, 🔄 D29).
 *
 * Les changements détectés ([enqueue]) mettent des `clientRecordId` en attente, enregistrés sur
 * disque ; [sync] traite la file (suppressions puis écritures, par lots de [BATCH]) et garde dans
 * le registre l'empreinte de ce qui a été écrit. Un envoi à la fois ; un échec laisse la file
 * intacte pour le prochain essai. [records] = enregistrements attendus d'après les données
 * actuelles.
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

    /** « Envoyer l'historique » : toutes les séances terminées et toutes les pesées. */
    suspend fun sendHistory(): HealthHistoryResult {
        val all = records()
        enqueue(all.keys)
        val result = if (sync()) {
            val granted = _granted.value
            HealthHistoryResult.Sent(
                sessions = all.values.count { it.type == HealthRecordType.Session && it.type in granted },
                weights = all.values.count { it.type == HealthRecordType.Weight && it.type in granted },
            )
        } else {
            HealthHistoryResult.Failed(_state.value.lastError ?: "Health Connect")
        }
        _historyResult.value = result
        return result
    }

    /**
     * Traite la file (option activée) : d'abord les enregistrements écrits dont la source a changé
     * ou disparu depuis (modifiés pendant que l'option était coupée, par exemple) sont remis en
     * attente ; puis, pour les types autorisés, suppressions et écritures. Un type non autorisé
     * reste en attente. `false` si l'envoi a échoué.
     */
    suspend fun sync(): Boolean = syncMutex.withLock {
        val gw = gateway ?: return false
        if (!_state.value.enabled) return false
        _syncing.value = true
        try {
            val desired = records()
            val stale = _state.value.sent.filter { (id, fingerprint) -> desired[id]?.fingerprint != fingerprint }.keys
            enqueue(stale)

            val pending = _state.value.pending
            if (pending.isEmpty()) return true
            val granted = try {
                gw.grantedTypes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect indisponible", e)
                update { it.copy(lastError = e.describe()) }
                return false
            }
            _granted.value = granted

            val unknown = pending.filter { HealthRecords.typeOf(it) == null }.toSet()
            val actionable = pending.filter { HealthRecords.typeOf(it) in granted }
            val toWrite = actionable.mapNotNull { desired[it] }
            val toDelete = actionable.filter { it !in desired }
            val version = maxOf(_state.value.lastVersion + 1, time.now().toEpochMilli())

            val sent = _state.value.sent.toMutableMap()
            val done = HashSet<String>(unknown)
            var error: Exception? = null
            try {
                for (type in HealthRecordType.entries) {
                    for (batch in toDelete.filter { HealthRecords.typeOf(it) == type }.chunked(BATCH)) {
                        gw.delete(type, batch)
                        batch.forEach { sent.remove(it) }
                        done += batch
                    }
                }
                for (batch in toWrite.chunked(BATCH)) {
                    gw.upsert(batch, version)
                    batch.forEach { sent[it.clientRecordId] = it.fingerprint }
                    done += batch.map { it.clientRecordId }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Envoi à Health Connect impossible", e)
                error = e
            }
            val wrote = toWrite.isNotEmpty() || toDelete.isNotEmpty()
            update { state ->
                state.copy(
                    sent = sent,
                    pending = state.pending.filterNot { it in done },
                    lastVersion = if (wrote) version else state.lastVersion,
                    lastSyncAt = if (error == null && wrote) time.now().toEpochMilli() else state.lastSyncAt,
                    lastError = error?.describe() ?: state.lastError.takeIf { !wrote },
                )
            }
            error == null
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
