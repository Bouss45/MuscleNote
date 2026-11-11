package com.musclenote.android.data

import com.musclenote.android.data.backup.BackupData
import com.musclenote.android.data.backup.BackupWriter
import com.musclenote.android.data.repository.NutritionRepository
import com.musclenote.android.data.repository.PreferencesRepository
import com.musclenote.android.data.repository.WorkoutRepository
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.storage.StorageMeta
import com.musclenote.android.data.util.TimeSource
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
) {
    /** Portée de l'application : écritures disque et tâches de fond. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val files = DataFiles(dataDir, appScope, ioDispatcher)

    val workouts = WorkoutRepository(files.workouts, files.exercises, files.currentWorkout, time)
    val nutrition = NutritionRepository(files.bodyWeights, files.supplements, files.supplementsTaken, files.dailyTracker, time)
    val preferences = PreferencesRepository(files.preferences, files.localPreferences)

    private val initialLoad: Deferred<Unit> = appScope.async {
        if (files.meta.read() == null) files.meta.saveNow(StorageMeta(StorageMeta.CURRENT_SCHEMA_VERSION))
        loadAll()
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

    /** Retour de l'app au premier plan (`ON_START`) : contrôle des séances de plus de 24 h (§ 3.4). */
    fun onAppForeground() {
        appScope.launch {
            awaitReady()
            workouts.finalizeStaleSession()
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
        workouts.replaceFromBackup(data.workouts, data.exercises)
        nutrition.replaceFromBackup(data.bodyWeights, data.supplements, data.supplementsTaken, data.dailyTracker)
        preferences.replaceFromBackup(data.preferences)
        loadAll()
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
        coroutineScope {
            launch { workouts.load() }
            launch { nutrition.load() }
            launch { preferences.load() }
        }
    }
}
