package com.musclenote.android.data

import com.musclenote.android.data.backup.BackupData
import com.musclenote.android.data.backup.BackupWriter
import com.musclenote.android.data.nutrition.ReminderRef
import com.musclenote.android.data.nutrition.SupplementReminders
import com.musclenote.android.data.repository.AutoBackupRepository
import com.musclenote.android.data.repository.AutoBackupScheduler
import com.musclenote.android.data.repository.BackupFolders
import com.musclenote.android.data.repository.FeedbackRepository
import com.musclenote.android.data.repository.FeedbackSender
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    private val initialLoad: Deferred<Unit> = appScope.async {
        if (files.meta.read() == null) files.meta.saveNow(StorageMeta(StorageMeta.CURRENT_SCHEMA_VERSION))
        loadAll()
        // Réglages propres à l'appareil, jamais remplacés par un import : lus une seule fois.
        autoBackup.load()
        autoBackup.ensureScheduled()
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
        return end
    }

    /** Alarme de fin de repos (récepteur) : attend le chargement, puis termine le repos (§ 7.2). */
    suspend fun onRestTimerAlarm(endsAt: Long, inForeground: Boolean) {
        awaitReady()
        restTimer.finish(endsAt, inForeground)
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

    /** « Passer » depuis la notification en cours (§ 7.2). */
    suspend fun skipRest() {
        awaitReady()
        restTimer.stop()
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

    private suspend fun loadAll() {
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
    }
}
