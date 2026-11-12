package com.musclenote.android.data.storage

import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.backup.AutoBackupState
import com.musclenote.android.data.feedback.FeedbackThrottle
import com.musclenote.android.data.health.HealthSyncState
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.LocalPreferences
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.update.UpdateCheckState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** `meta.json` : version du schéma de stockage, pour de futures migrations à la lecture. */
@Serializable
data class StorageMeta(val schemaVersion: Int) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/** Les fichiers de `filesDir/data/` (SPEC § 4.2), même forme que les valeurs de sauvegarde. */
class DataFiles(
    private val dir: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private fun <T : Any> document(fileName: String, serializer: KSerializer<T>) =
        JsonDocument(File(dir, fileName), serializer, AppJson.storage, scope, ioDispatcher)

    val workouts = document("workouts.json", ListSerializer(Workout.serializer()))
    val exercises = document("exercises.json", ListSerializer(Exercise.serializer()))
    val currentWorkout = document("current_workout.json", Workout.serializer())
    val bodyWeights = document("body_weights.json", ListSerializer(BodyWeightEntry.serializer()))
    val supplements = document("supplements.json", ListSerializer(Supplement.serializer()))
    val supplementsTaken = document("supplements_taken.json", MapSerializer(String.serializer(), String.serializer()))
    val dailyTracker = document("daily_tracker.json", DailyTracker.serializer())
    val preferences = document("preferences.json", Preferences.serializer())

    /** Préférences propres à l'appareil, jamais exportées ni remplacées par un import. */
    val localPreferences = document("local_preferences.json", LocalPreferences.serializer())

    /** Repos en cours (§ 7.2) ; fichier absent hors repos. */
    val restTimer = document("rest_timer.json", RestTimerState.serializer())

    /** Vérification des mises à jour (§ 3.17), propre à l'appareil, hors sauvegarde. */
    val updateCheck = document("update_check.json", UpdateCheckState.serializer())

    /** Délais entre deux avis (§ 14.3), propres à l'appareil, hors sauvegarde. */
    val feedback = document("feedback.json", FeedbackThrottle.serializer())

    /** Sauvegarde automatique (§ 15.2) : réglage et dernier résultat, hors sauvegarde. */
    val autoBackup = document("auto_backup.json", AutoBackupState.serializer())

    /** Envois à Health Connect (§ 15.3) : option, registre et file d'attente, hors sauvegarde. */
    val healthConnect = document("health_connect.json", HealthSyncState.serializer())
    val meta = document("meta.json", StorageMeta.serializer())
}
