package com.musclenote.android.data.backup

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Export v2 (SPEC § 5.2, § 5.6) : `version`, `exportedAt`, puis toutes les clés dans l'ordre de
 * [BackupKeys.ALL] (documents vides si besoin), JSON indenté de 2 espaces. La séance en cours
 * n'est jamais exportée. Une clé absente de [data] est écrite avec sa valeur vide / par défaut.
 */
object BackupWriter {

    fun write(data: BackupData, exportedAt: Instant): String {
        val json = AppJson.export
        val root = buildJsonObject {
            put("version", JsonPrimitive(CURRENT_BACKUP_VERSION))
            put("exportedAt", JsonPrimitive(formatInstant(exportedAt)))
            put(BackupKeys.WORKOUTS, json.encodeToJsonElement(ListSerializer(Workout.serializer()), data.workouts.orEmpty()))
            put(BackupKeys.EXERCISES, json.encodeToJsonElement(ListSerializer(Exercise.serializer()), data.exercises.orEmpty()))
            put(BackupKeys.BODY_WEIGHTS, json.encodeToJsonElement(ListSerializer(BodyWeightEntry.serializer()), data.bodyWeights.orEmpty()))
            put(BackupKeys.SUPPLEMENTS, json.encodeToJsonElement(ListSerializer(Supplement.serializer()), data.supplements.orEmpty()))
            put(BackupKeys.SUPPLEMENTS_TAKEN, json.encodeToJsonElement(MapSerializer(String.serializer(), String.serializer()), data.supplementsTaken.orEmpty()))
            put(BackupKeys.DAILY_TRACKER, json.encodeToJsonElement(DailyTracker.serializer(), data.dailyTracker ?: DailyTracker.DEFAULT))
            put(BackupKeys.PREFERENCES, json.encodeToJsonElement(Preferences.serializer(), data.preferences ?: Preferences.DEFAULT))
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }
}
