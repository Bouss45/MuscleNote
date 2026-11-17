package com.musclenote.android.data

import com.musclenote.android.data.backup.BackupData
import com.musclenote.android.data.backup.BackupWriter
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.nutrition.ReminderRef
import com.musclenote.android.data.nutrition.SupplementReminders
import com.musclenote.android.data.nutrition.WeighInReminderRules
import com.musclenote.android.data.nutrition.WeighInReminders
import com.musclenote.android.data.repository.AutoBackupRepository
import com.musclenote.android.data.repository.AutoBackupScheduler
import com.musclenote.android.data.repository.BackupFolders
import com.musclenote.android.data.repository.FeedbackRepository
import com.musclenote.android.data.repository.FeedbackSender
import com.musclenote.android.data.repository.HealthConnectGateway
import com.musclenote.android.data.repository.HealthSyncRepository
import com.musclenote.android.data.repository.NutritionRepository
import com.musclenote.android.data.repository.PreferencesRepository
import com.musclenote.android.data.repository.ReleaseSource
import com.musclenote.android.data.repository.RestTimerEffects
import com.musclenote.android.data.repository.RestTimerRepository
import com.musclenote.android.data.repository.SetTimerRepository
import com.musclenote.android.data.repository.UpdateCheckScheduler
import com.musclenote.android.data.repository.UpdateNotifications
import com.musclenote.android.data.repository.UpdateRepository
import com.musclenote.android.data.repository.WorkoutRepository
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.storage.StorageMeta
import com.musclenote.android.data.util.CurrentDay
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.workout.SetTimerRules
import com.musclenote.android.data.workout.WorkoutEnd
import com.musclenote.android.data.workout.WorkoutNotifications
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Singletons de données de l'application (SPEC § 4.2) : un dépôt par domaine, partagés par
 * tous les écrans. Le chargement démarre dès la création ; [awaitReady] le termine.
 */
class AppContainer(
    dataDir: File,
    val time: TimeSource = TimeSource.System,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    restTimerEffects: RestTimerEffects = RestTimerEffects.None,
    private val supplementReminders: SupplementReminders = SupplementReminders.None,
    /** Rappel de pesée (§ 15.13.4) : même mécanisme que les rappels de compléments. */
    private val weighInReminders: WeighInReminders = WeighInReminders.None,
    /** `versionName` / `versionCode` de l'app installée, comparés à la dernière Release (§ 15.14.3). */
    currentVersion: String = "0",
    currentVersionCode: Int = 0,
    releaseSource: ReleaseSource = ReleaseSource.None,
    /**
     * App installée (ou mise à jour) par Obtainium (§ 3.17) : notification de mise à jour coupée par
     * défaut (§ 15.14.1), pas d'annonce sur l'Accueil.
     */
    installedByObtainium: Boolean = false,
    /** Notification et vérification quotidienne possibles : faux dans la variante preview (§ 15.14.1). */
    updateNotificationsAvailable: Boolean = false,
    /** Notification « MuscleNote {version} disponible » (§ 15.14.4). */
    updateNotifications: UpdateNotifications = UpdateNotifications.None,
    /** Tâche de la vérification quotidienne vers 18 h (§ 15.14.2). */
    updateCheckScheduler: UpdateCheckScheduler = UpdateCheckScheduler.None,
    /** Webhook de l'envoi d'un avis (§ 14) ; `null` = pas d'URL au build, section masquée. */
    feedbackSender: FeedbackSender? = null,
    /** Dossier de la sauvegarde automatique (§ 15.2) : Storage Access Framework sur l'appareil. */
    backupFolders: BackupFolders = BackupFolders.None,
    autoBackupScheduler: AutoBackupScheduler = AutoBackupScheduler.None,
    /** Health Connect (§ 15.3) ; `null` = jamais disponible (tests). */
    healthConnect: HealthConnectGateway? = null,
    /** Notification « Séance en cours » avec actions (§ 15.5). */
    private val workoutNotifications: WorkoutNotifications = WorkoutNotifications.None,
) {
    /** Portée de l'application : écritures disque et tâches de fond. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val files = DataFiles(dataDir, appScope, ioDispatcher)

    val workouts = WorkoutRepository(files.workouts, files.exercises, files.currentWorkout, time)
    val nutrition = NutritionRepository(files.bodyWeights, files.supplements, files.supplementsTaken, files.dailyTracker, time, supplementReminders)

    /** Jour local courant (§ 3.12) : minuit, retour au premier plan, changement de fuseau. */
    val currentDay = CurrentDay(time, appScope)
    val preferences = PreferencesRepository(files.preferences, files.localPreferences, weighInReminders)
    val restTimer = RestTimerRepository(files.restTimer, time, { preferences.preferences.value.restSeconds }, restTimerEffects)

    /** Chrono d'une série au temps (§ 15.18.4) ; il ne vaut que sur une série non validée de la séance en cours. */
    val setTimer = SetTimerRepository(files.setTimer)
    val updates = UpdateRepository(
        files.updateCheck,
        time,
        currentVersion,
        releaseSource,
        currentVersionCode = currentVersionCode,
        installedByObtainium = installedByObtainium,
        notificationsAvailable = updateNotificationsAvailable,
        notifications = updateNotifications,
        scheduler = updateCheckScheduler,
    )
    val feedback = FeedbackRepository(files.feedback, time, feedbackSender)

    /** Sauvegarde automatique quotidienne (§ 15.2) : même contenu que « Exporter mes données ». */
    val autoBackup = AutoBackupRepository(files.autoBackup, time, backupFolders, autoBackupScheduler) { exportBackupJson() }

    /** Envoi des séances et du poids à Health Connect (§ 15.3), d'après les données actuelles. */
    val health = HealthSyncRepository(files.healthConnect, time, healthConnect) {
        HealthRecords.all(workouts.workouts.value, nutrition.bodyWeights.value, time.zone())
    }

    /** Une publication de la notification de séance à la fois (§ 15.5) ; déclaré avant le chargement qui s'en sert. */
    private val notificationMutex = Mutex()

    private val initialLoad: Deferred<Unit> = appScope.async {
        if (files.meta.read() == null) files.meta.saveNow(StorageMeta(StorageMeta.CURRENT_SCHEMA_VERSION))
        val finalized = loadAll()
        // Notification d'une version désormais installée retirée, vérification quotidienne
        // programmée seulement si « Nouvelle version disponible » est activé (§ 15.14.2).
        updates.onAppStart()
        // Réglages propres à l'appareil, jamais remplacés par un import : lus une seule fois.
        autoBackup.load()
        autoBackup.ensureScheduled()
        health.load()
        watchHealthChanges()
        // Séance de plus de 24 h finalisée au chargement : un changement comme un autre (§ 15.3).
        if (finalized is WorkoutEnd.Finished) {
            health.enqueue(HealthRecords.idsFor(finalized.workout, time.zone()))
        }
        // Hors du chargement : awaitReady() n'attend pas Health Connect.
        appScope.launch { health.sync() }
        // Chrono d'une série supprimée, validée ou d'une séance finie : retiré (§ 15.18.4).
        watchSetTimer()
        // Notification de la séance en cours (§ 15.5), republiée après un redémarrage du téléphone.
        watchWorkoutNotification()
        // Resynchronisation complète des rappels au démarrage (§ 7.3, § 15.13.4).
        supplementReminders.sync(emptyList()) { nutrition.supplements.value }
        weighInReminders.sync { preferences.preferences.value.weighInReminder }
    }

    /** Attend la fin du chargement initial : rien ne doit être exporté ou importé avant. */
    suspend fun awaitReady() = initialLoad.await()

    /**
     * Lance une mutation de dépôt depuis l'UI. Démarrage immédiat (`UNDISPATCHED`) : la mutation
     * demande le `Mutex` du dépôt dès l'appel, et ce `Mutex` sert les demandes dans leur ordre
     * d'arrivée — deux frappes successives sont donc appliquées dans l'ordre. Sans attente, la
     * mutation s'applique même avant le retour de l'appel. Portée de l'application : quitter
     * l'écran ne l'annule pas.
     */
    fun launchMutation(block: suspend () -> Unit): Job =
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { block() }

    /**
     * Retour de l'app au premier plan (`ON_START`) : contrôle des séances de plus de 24 h (§ 3.4) ;
     * une séance finalisée ou abandonnée arrête le repos en cours. Les mises à jour ne sont plus
     * vérifiées ici (🔄 D39 : tâche quotidienne, § 15.14.2).
     */
    fun onAppForeground() {
        currentDay.refresh()
        appScope.launch {
            awaitReady()
            if (workouts.finalizeStaleSession() != null) restTimer.stop()
        }
        // Envois à Health Connect en attente (échec précédent, autorisation rendue…), § 15.3.
        appScope.launch {
            awaitReady()
            if (health.state.value.enabled) {
                health.refreshPermissions()
                health.sync()
            }
        }
    }

    /** Tâche de 18 h (§ 15.14.2) : vérification quotidienne et, s'il y a lieu, notification. */
    suspend fun runDailyUpdateCheck() {
        awaitReady()
        updates.dailyCheck()
    }

    /**
     * Fin de la séance en cours (§ 3.3), ou abandon sans série validée : le repos en cours
     * s'arrête aussi (§ 7.2).
     */
    suspend fun endWorkout(): WorkoutEnd? {
        val end = workouts.endWorkout()
        restTimer.stop()
        refreshWorkoutNotification()
        return end
    }

    /**
     * Alarme de fin de repos (récepteur) : attend le chargement, termine le repos (§ 7.2) — l'alerte
     * propose « Valider la série » s'il en reste une — et remet la notification de séance hors
     * repos (§ 15.5).
     */
    suspend fun onRestTimerAlarm(endsAt: Long, inForeground: Boolean) {
        awaitReady()
        restTimer.finish(endsAt, inForeground, canCompleteSet = workouts.hasNextSet())
        refreshWorkoutNotification()
    }

    /**
     * « Valider la série » (notification de séance ou alerte de fin de repos, § 15.5) : valide la
     * prochaine série et lance le repos, comme un appui dans l'écran de séance.
     */
    suspend fun completeNextSet() {
        awaitReady()
        val exerciseId = workouts.completeNextSet()
        if (exerciseId != null) restTimer.start(workoutNotifications.exerciseName(exerciseId, workouts.customExercises.value))
        refreshWorkoutNotification()
    }

    /**
     * « Démarrer » le chrono de la série [setId] (§ 15.18.4) : seulement sur une série non validée de
     * la séance en cours, sans autre chrono ; le repos en cours s'arrête. `true` s'il a démarré.
     */
    suspend fun startSetTimer(setId: String): Boolean {
        awaitReady()
        val located = SetTimerRules.locate(workouts.currentWorkout.value, setId) ?: return false
        val running = SetTimerRules.activeOn(setTimer.state.value, workouts.currentWorkout.value)
        if (!SetTimerRules.canStart(running, located.set)) return false
        // Un chrono resté sur une série disparue ne compte plus.
        if (running == null) setTimer.clear()
        if (!setTimer.start(setId, time.now().toEpochMilli())) return false
        restTimer.stop()
        refreshWorkoutNotification()
        return true
    }

    /**
     * Arrêt du chrono en cours (§ 15.18.4) : sa série prend la durée écoulée ; « Arrêter » (ligne,
     * case ou notification) la valide aussi et lance le repos ([validate]), l'ouverture du pavé non.
     * [nowMillis] : l'instant de l'arrêt, celui que l'écran a déjà montré dans le pavé.
     */
    suspend fun stopSetTimer(validate: Boolean = true, nowMillis: Long = time.now().toEpochMilli()) {
        awaitReady()
        val state = setTimer.state.value ?: return
        val cleared = setTimer.clear(state) ?: return
        val located = SetTimerRules.locate(workouts.currentWorkout.value, cleared.setId)
        if (located != null && !located.set.completed) {
            val seconds = SetTimerRules.elapsedSeconds(cleared, nowMillis)
            val exerciseId = workouts.recordHold(cleared.setId, seconds, validate)
            if (exerciseId != null) restTimer.start(workoutNotifications.exerciseName(exerciseId, workouts.customExercises.value))
        }
        refreshWorkoutNotification()
    }

    /** « −30 s » / « +30 s » de la notification de séance (§ 15.5). */
    suspend fun adjustRest(deltaSeconds: Int) {
        awaitReady()
        restTimer.adjust(deltaSeconds)
        refreshWorkoutNotification()
    }

    /**
     * (Re)publie la notification « Séance en cours » d'après l'état le plus récent, ou la retire
     * sans séance en cours (§ 15.5). Une publication à la fois : la dernière montre toujours le
     * dernier état. Aussi après un changement de langue (activité recréée). Attend le chargement :
     * avant, l'absence de séance ne voudrait rien dire.
     */
    suspend fun refreshWorkoutNotification() {
        awaitReady()
        notificationMutex.withLock {
            val workout = workouts.currentWorkout.value
            if (workout == null) {
                workoutNotifications.cancel()
            } else {
                val timer = SetTimerRules.activeOn(setTimer.state.value, workout)
                workoutNotifications.show(workout, restTimer.state.value, workouts.customExercises.value, timer)
            }
        }
    }

    /**
     * Séance en cours, repos ou exercices perso modifiés → notification republiée, au plus une fois
     * par [NOTIFICATION_THROTTLE_MS] (une frappe dans une série en change le texte ; Android limite
     * le rythme des mises à jour) ; le dernier état est toujours publié.
     */
    private fun watchWorkoutNotification() {
        appScope.launch {
            combine(workouts.currentWorkout, restTimer.state, workouts.customExercises, setTimer.state) { _, _, _, _ -> }
                .conflate()
                .collect {
                    refreshWorkoutNotification()
                    delay(NOTIFICATION_THROTTLE_MS)
                }
        }
    }

    /**
     * Le chrono ne vaut que sur une série non validée de la séance en cours (§ 15.18.4) : série
     * supprimée, exercice retiré, séance terminée, abandonnée ou finalisée → retiré sans rien reporter.
     */
    private fun watchSetTimer() {
        appScope.launch {
            combine(workouts.currentWorkout, setTimer.state) { workout, timer -> timer?.takeIf { SetTimerRules.activeOn(it, workout) == null } }
                .collect { stale -> if (stale != null) setTimer.clear(stale) }
        }
    }

    /**
     * Resynchronisation complète des rappels (§ 7.3) : redémarrage du téléphone, mise à jour de
     * l'app, changement d'heure ou de fuseau, droit aux alarmes exactes. Le jour courant est relu.
     */
    suspend fun resyncReminders() {
        awaitReady()
        currentDay.refresh()
        supplementReminders.sync(emptyList()) { nutrition.supplements.value }
        weighInReminders.sync { preferences.preferences.value.weighInReminder }
    }

    /** Sonnerie d'un rappel de complément (récepteur, § 7.3). */
    suspend fun onSupplementReminderAlarm(ref: ReminderRef) {
        awaitReady()
        supplementReminders.onAlarm(ref) { nutrition.supplements.value }
    }

    /**
     * Sonnerie du rappel de pesée (récepteur, § 15.13.4) : pas de notification si le jour n'est pas
     * coché ou déjà pesé — pesée, séance terminée, ou la séance en cours avec un poids.
     */
    suspend fun onWeighInReminderAlarm() {
        awaitReady()
        weighInReminders.onAlarm(latest = { preferences.preferences.value.weighInReminder }) { reminder, day ->
            val all = workouts.workouts.value + listOfNotNull(workouts.currentWorkout.value)
            WeighInReminderRules.decide(reminder, day, all, nutrition.bodyWeights.value, time.zone())
        }
    }

    /** « Passer » depuis la notification de séance (§ 7.2, § 15.5). */
    suspend fun skipRest() {
        awaitReady()
        restTimer.stop()
        refreshWorkoutNotification()
    }

    /**
     * Copie de la langue choisie dans l'app ([tag] `null` = système), relue par les notifications
     * hors activité avant Android 13 (§ 9.4). Écrite une fois les données chargées, pour ne pas
     * écraser les préférences locales encore sur disque.
     */
    fun rememberAppLanguage(tag: String?) {
        appScope.launch {
            awaitReady()
            preferences.setAppLanguage(tag)
        }
    }

    /** Mise en arrière-plan (`ON_STOP`) : écritures différées forcées (§ 4.2). */
    fun onAppBackground() {
        appScope.launch { workouts.flushPendingWrites() }
    }

    /**
     * Import d'une sauvegarde validée (§ 5.4) : seuls les documents présents sont remplacés,
     * puis **tous** les dépôts rechargent depuis le disque.
     */
    suspend fun importBackup(data: BackupData) {
        awaitReady()
        val previousSupplements = nutrition.supplements.value
        workouts.replaceFromBackup(data.workouts, data.exercises)
        nutrition.replaceFromBackup(data.bodyWeights, data.supplements, data.supplementsTaken, data.dailyTracker)
        preferences.replaceFromBackup(data.preferences)
        loadAll()
        // § 7.3 : les rappels qui existaient avant et n'existent plus sont annulés d'abord.
        supplementReminders.sync(previousSupplements) { nutrition.supplements.value }
        // Le rappel de pesée vient des préférences importées (§ 15.13.5).
        weighInReminders.sync { preferences.preferences.value.weighInReminder }
    }

    /** Export JSON v2 de l'état courant (§ 5.2) ; la séance en cours n'en fait pas partie. */
    suspend fun exportBackupJson(): String {
        awaitReady()
        val data = BackupData(
            workouts = workouts.workouts.value,
            exercises = workouts.customExercises.value,
            bodyWeights = nutrition.bodyWeights.value,
            supplements = nutrition.supplements.value,
            supplementsTaken = nutrition.taken.value,
            dailyTracker = nutrition.tracker.value,
            preferences = preferences.preferences.value,
        )
        return BackupWriter.write(data, time.now())
    }

    /**
     * Health Connect (§ 15.3) : à chaque changement des séances terminées ou des pesées, les
     * enregistrements ajoutés, modifiés ou retirés sont mis en attente puis envoyés. L'état chargé
     * sert de point de départ : ce qui existait avant n'est envoyé que par « Envoyer
     * l'historique ». Un import passe aussi par ici.
     */
    private fun watchHealthChanges() {
        var previous = HealthRecords.fingerprints(workouts.workouts.value, nutrition.bodyWeights.value, time.zone())
        appScope.launch {
            combine(workouts.workouts, nutrition.bodyWeights) { list, weighIns -> HealthRecords.fingerprints(list, weighIns, time.zone()) }
                .collect { current ->
                    val changed = HealthRecords.changedIds(previous, current)
                    previous = current
                    if (changed.isNotEmpty() && health.state.value.enabled) {
                        health.enqueue(changed)
                        launch { health.sync() }
                    }
                }
        }
    }

    /** Recharge les documents ; renvoie l'issue d'une séance de plus de 24 h finalisée au passage. */
    private suspend fun loadAll(): WorkoutEnd? {
        val finalized = coroutineScope {
            launch { nutrition.load() }
            launch { preferences.load() }
            launch { restTimer.load() }
            launch { setTimer.load() }
            launch { updates.load() }
            launch { feedback.load() }
            workouts.load()
        }
        // Séance de plus de 24 h finalisée ou abandonnée au chargement : plus de repos (§ 3.4).
        if (finalized != null) restTimer.stop()
        // Chrono dont la série n'est plus dans la séance en cours : ignoré (§ 15.18.8).
        setTimer.state.value?.let { if (SetTimerRules.activeOn(it, workouts.currentWorkout.value) == null) setTimer.clear(it) }
        return finalized
    }

    private companion object {
        /** Intervalle minimal entre deux publications de la notification de séance. */
        const val NOTIFICATION_THROTTLE_MS = 250L
    }
}
