package com.musclenote.android.data.repository

import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.data.update.UpdateCheckState
import com.musclenote.android.data.update.UpdateRules
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Où lire la dernière Release (l'API GitHub sur l'appareil, un faux dans les tests). */
interface ReleaseSource {
    /** Dernière Release publiée ; lève une exception si elle ne peut pas être lue. */
    suspend fun latest(): LatestRelease

    companion object {
        /** Aucune source : la vérification échoue toujours (tests, vérification désactivée). */
        val None: ReleaseSource = object : ReleaseSource {
            override suspend fun latest(): LatestRelease = throw IllegalStateException("Aucune source de Release")
        }
    }
}

/** Issue d'une vérification. */
enum class UpdateCheckResult { UpToDate, Available, Failed }

/**
 * Vérification des mises à jour (SPEC § 3.17, 🔄 D25), pour une app installée sans gestionnaire
 * de mises à jour (Obtainium). Une vérification à la fois ; la requête réseau se fait hors du
 * verrou des données. [currentVersion] = `versionName` de l'app installée.
 */
class UpdateRepository(
    private val doc: JsonDocument<UpdateCheckState>,
    private val time: TimeSource,
    val currentVersion: String,
    private val source: ReleaseSource,
) {
    private val mutex = Mutex()
    private val checkMutex = Mutex()

    private val _state = MutableStateFlow(UpdateCheckState.DEFAULT)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)

    /** Vérification en cours (indicateur de Paramètres). */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _lastCheckFailed = MutableStateFlow(false)

    /** La dernière vérification de ce processus a échoué (pas de réseau, réponse illisible…). */
    val lastCheckFailed: StateFlow<Boolean> = _lastCheckFailed.asStateFlow()

    /** Dernier essai, réussi ou non (ms) : limite les nouveaux essais après un échec. */
    private var lastAttemptAt = 0L

    suspend fun load() {
        mutex.withLock { _state.value = doc.read() ?: UpdateCheckState.DEFAULT }
    }

    /**
     * Vérification automatique (retour au premier plan) : seulement si la dernière réussie date de
     * 24 h ou plus, et pas plus d'un essai par heure après un échec. `null` si rien n'a été fait.
     */
    suspend fun checkIfDue(): UpdateCheckResult? = checkMutex.withLock {
        val now = time.now().toEpochMilli()
        val retryBlocked = lastAttemptAt != 0L && now >= lastAttemptAt && now - lastAttemptAt < UpdateRules.RETRY_INTERVAL_MS
        if (!UpdateRules.isDue(_state.value.checkedAt, now) || retryBlocked) return null
        checkLocked()
    }

    /** « Vérifier » dans Paramètres : tout de suite. */
    suspend fun checkNow(): UpdateCheckResult = checkMutex.withLock { checkLocked() }

    private suspend fun checkLocked(): UpdateCheckResult {
        lastAttemptAt = time.now().toEpochMilli()
        _checking.value = true
        try {
            val release = try {
                source.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastCheckFailed.value = true
                return UpdateCheckResult.Failed
            }
            _lastCheckFailed.value = false
            update {
                it.copy(
                    checkedAt = time.now().toEpochMilli(),
                    latestVersion = release.version,
                    pageUrl = release.pageUrl,
                    apkUrl = release.apkUrl,
                )
            }
            return if (UpdateRules.isNewer(release.version, currentVersion)) UpdateCheckResult.Available else UpdateCheckResult.UpToDate
        } finally {
            _checking.value = false
        }
    }

    /** ✕ de l'annonce de l'Accueil : masquée jusqu'à la version suivante. */
    suspend fun dismiss(version: String) = update { it.copy(dismissedVersion = version) }

    private suspend fun update(transform: (UpdateCheckState) -> UpdateCheckState) {
        mutex.withLock {
            val next = transform(_state.value)
            if (next == _state.value) return
            _state.value = next
            doc.save(next)
        }
    }
}
