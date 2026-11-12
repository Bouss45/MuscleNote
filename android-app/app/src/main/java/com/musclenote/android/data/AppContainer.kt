package com.musclenote.android.data

import com.musclenote.android.data.backup.BackupData
import com.musclenote.android.data.backup.BackupWriter
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.nutrition.ReminderRef
import com.musclenote.android.data.nutrition.SupplementReminders
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
import com.musclenote.android.data.repository.UpdateRepository
import com.musclenote.android.data.repository.WorkoutRepository
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.storage.StorageMeta
import com.musclenote.android.data.util.CurrentDay
import com.musclenote.android.data.util.TimeSource
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
    /** `versionName` de l'app installée, comparé à la dernière Release (§ 3.17). */
    currentVersion: String = "0",
    releaseSource: ReleaseSource = ReleaseSource.None,
    /**
     * Vérification des mises à jour active : app installée sans gestionnaire de mises à jour
     * (Obtainium s'en charge sinon), § 3.17, 🔄 D25.
     */
    val updateChecksEnabled: Boolean = false,
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
    val preferences = PreferencesRepository(files.preferences, files.localPreferences)
    val restTimer = RestTimerRepository(files.restTimer, time, { preferences.preferences.value.restSeconds }, restTimerEffects)
    val updates = UpdateRepository(files.updateCheck, time, currentVersion, releaseSource)
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
        // Notification de la séance en cours (§ 15.5), republiée après un redémarrage du téléphone.
        watchWorkoutNotification()
        // Resynchronisation complète des rappels au démarrage (§ 7.3).
        supplementReminders.sync(emptyList()) { nutrition.supplements.value }
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
     * une séance finalisée ou abandonnée arrête le repos en cours. Puis vérification des mises à
     * jour si elle est due (§ 3.17).
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
        // Au plus une fois par jour, et seulement sans gestionnaire de mises à jour (§ 3.17).
        if (updateChecksEnabled) {
            appScope.launch {
                awaitReady()
                updates.checkIfDue()
            }
        }
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
            if (workout == null) workoutNotifications.cancel()
            else workoutNotifications.show(workout, restTimer.state.value, workouts.customExercises.value)
        }
    }

    /**
     * Séance en cours, repos ou exercices perso modifiés → notification republiée, au plus une fois
     * par [NOTIFICATION_THROTTLE_MS] (une frappe dans une série en change le texte ; Android limite
     * le rythme des mises à jour) ; le dernier état est toujours publié.
     */
    private fun watchWorkoutNotification() {
        appScope.launch {
            combine(workouts.currentWorkout, restTimer.state, workouts.customExercises) { _, _, _ -> }
                .conflate()
                .collect {
                    refreshWorkoutNotification()
                    delay(NOTIFICATION_THROTTLE_MS)
                }
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
    }

    /** Sonnerie d'un rappel de complément (récepteur, § 7.3). */
    suspend fun onSupplementReminderAlarm(ref: ReminderRef) {
        awaitReady()
        supplementReminders.onAlarm(ref) { nutrition.supplements.value }
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
            launch { updates.load() }
            launch { feedback.load() }
            workouts.load()
        }
        // Séance de plus de 24 h finalisée ou abandonnée au chargement : plus de repos (§ 3.4).
        if (finalized != null) restTimer.stop()
        return finalized
    }

    private companion object {
        /** Intervalle minimal entre deux publications de la notification de séance. */
        const val NOTIFICATION_THROTTLE_MS = 250L
    }
}
