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
import java.time.Instant

/** Où lire la dernière Release (l'API GitHub sur l'appareil, un faux dans les tests). */
interface ReleaseSource {
    /** Dernière Release publiée ; lève une exception si elle ne peut pas être lue. */
    suspend fun latest(): LatestRelease

    companion object {
        /** Aucune source : la vérification échoue toujours (tests, aperçus). */
        val None: ReleaseSource = object : ReleaseSource {
            override suspend fun latest(): LatestRelease = throw IllegalStateException("Aucune source de Release")
        }
    }
}

/**
 * Notification « MuscleNote {version} disponible » (§ 15.14.4) : un seul identifiant, donc une
 * nouvelle version remplace celle qui est affichée.
 */
interface UpdateNotifications {
    /** Affiche (ou remplace) la notification de [release] ; `false` si elle n'a pas pu s'afficher. */
    fun show(release: LatestRelease): Boolean

    /** Retire la notification affichée, s'il y en a une. */
    fun cancel()

    companion object {
        val None: UpdateNotifications = object : UpdateNotifications {
            override fun show(release: LatestRelease): Boolean = false

            override fun cancel() = Unit
        }
    }
}

/** Tâche de la vérification quotidienne (§ 15.14.2) : WorkManager sur l'appareil. */
interface UpdateCheckScheduler {
    /** Comment programmer la prochaine vérification par rapport à celle qui existe déjà. */
    enum class Mode {
        /** Démarrage de l'app : garder la tâche déjà programmée, sinon programmer celle-ci. */
        KeepExisting,

        /** Changement du réglage : remplacer la tâche programmée. */
        Replace,

        /** Depuis la tâche elle-même : la suivante démarre une fois celle-ci terminée. */
        AfterCurrentRun,
    }

    /** Programme la vérification à [at] (réseau requis). */
    fun schedule(at: Instant, mode: Mode)

    /** Annule la vérification programmée. */
    fun cancel()

    companion object {
        val None: UpdateCheckScheduler = object : UpdateCheckScheduler {
            override fun schedule(at: Instant, mode: Mode) = Unit

            override fun cancel() = Unit
        }
    }
}

/** Issue d'une vérification. */
enum class UpdateCheckResult { UpToDate, Available, Failed }

/**
 * Vérification et notification des mises à jour (SPEC § 3.17, § 15.14, 🔄 D25, D39). Une
 * vérification à la fois ; la requête réseau se fait hors du verrou des données.
 * [currentVersion] / [currentVersionCode] = `versionName` / `versionCode` de l'app installée ;
 * [installedByObtainium] donne le réglage par défaut ; [notificationsAvailable] faux dans la
 * variante preview (ni tâche ni notification).
 */
class UpdateRepository(
    private val doc: JsonDocument<UpdateCheckState>,
    private val time: TimeSource,
    val currentVersion: String,
    private val source: ReleaseSource,
    val currentVersionCode: Int = 0,
    val installedByObtainium: Boolean = false,
    val notificationsAvailable: Boolean = false,
    private val notifications: UpdateNotifications = UpdateNotifications.None,
    private val scheduler: UpdateCheckScheduler = UpdateCheckScheduler.None,
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

    suspend fun load() {
        mutex.withLock { _state.value = doc.read() ?: UpdateCheckState.DEFAULT }
    }

    /**
     * Réglage effectif « Nouvelle version disponible » (§ 15.14.1) : le choix enregistré, sinon
     * le défaut selon la source d'installation ; toujours faux dans la preview.
     */
    fun notifyEnabled(state: UpdateCheckState = _state.value): Boolean =
        notificationsAvailable && UpdateRules.notifyEnabled(state.notifyEnabled, installedByObtainium)

    /** Mise à jour à proposer d'après la dernière vérification réussie (Accueil, Paramètres). */
    fun available(state: UpdateCheckState = _state.value): LatestRelease? =
        UpdateRules.available(state, currentVersion, currentVersionCode)

    /**
     * Démarrage de l'app : retire la notification d'une version désormais installée, puis
     * programme la vérification quotidienne (en gardant celle qui existe) ou l'annule.
     */
    suspend fun onAppStart() {
        if (UpdateRules.isNotificationStale(_state.value, currentVersion, currentVersionCode)) notifications.cancel()
        schedule(UpdateCheckScheduler.Mode.KeepExisting)
    }

    /** Ligne « Nouvelle version disponible » de Paramètres : choix enregistré, tâche reprogrammée. */
    suspend fun setNotifyEnabled(enabled: Boolean) {
        update { it.copy(notifyEnabled = enabled) }
        if (!notifyEnabled()) notifications.cancel()
        schedule(UpdateCheckScheduler.Mode.Replace)
    }

    /**
     * Vérification quotidienne (tâche de 18 h, § 15.14.2) : seulement si le réglage est activé,
     * sinon aucune requête et la tâche est annulée. Notifie une nouvelle version (§ 15.14.4) puis
     * programme la vérification du lendemain, réussie ou non. `null` si rien n'a été vérifié.
     */
    suspend fun dailyCheck(): UpdateCheckResult? = checkMutex.withLock {
        if (!notifyEnabled()) {
            scheduler.cancel()
            return null
        }
        try {
            val release = checkLocked() ?: return UpdateCheckResult.Failed
            val state = _state.value
            if (UpdateRules.shouldNotify(release.version, state, currentVersion, currentVersionCode, notifyEnabled(state)) &&
                notifications.show(release)
            ) {
                update { it.copy(notifiedVersion = release.version) }
            }
            if (UpdateRules.isNewerThanInstalled(release.version, currentVersion, currentVersionCode)) UpdateCheckResult.Available else UpdateCheckResult.UpToDate
        } finally {
            scheduler.schedule(UpdateRules.nextCheckAt(time.now(), time.zone()).toInstant(), UpdateCheckScheduler.Mode.AfterCurrentRun)
        }
    }

    /** « Vérifier maintenant » dans Paramètres : tout de suite, quel que soit le réglage, sans notifier. */
    suspend fun checkNow(): UpdateCheckResult = checkMutex.withLock {
        val release = checkLocked() ?: return UpdateCheckResult.Failed
        if (UpdateRules.isNewerThanInstalled(release.version, currentVersion, currentVersionCode)) UpdateCheckResult.Available else UpdateCheckResult.UpToDate
    }

    /** Lit la dernière Release et l'enregistre ; `null` après un échec (rien d'enregistré ne change). */
    private suspend fun checkLocked(): LatestRelease? {
        _checking.value = true
        try {
            val release = try {
                source.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastCheckFailed.value = true
                return null
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
            return release
        } finally {
            _checking.value = false
        }
    }

    /**
     * « Plus tard » de l'Accueil ou « Ignorer cette version » de la notification (§ 15.14.4) :
     * ni annonce ni notification pour [version] ; la notification affichée pour elle est retirée.
     */
    suspend fun dismiss(version: String) {
        update { it.copy(dismissedVersion = version) }
        if (_state.value.notifiedVersion == version) notifications.cancel()
    }

    private fun schedule(mode: UpdateCheckScheduler.Mode) {
        if (notifyEnabled()) {
            scheduler.schedule(UpdateRules.nextCheckAt(time.now(), time.zone()).toInstant(), mode)
        } else {
            scheduler.cancel()
        }
    }

    private suspend fun update(transform: (UpdateCheckState) -> UpdateCheckState) {
        mutex.withLock {
            val next = transform(_state.value)
            if (next == _state.value) return
            _state.value = next
            doc.save(next)
        }
    }
}
