package com.musclenote.android.data.backup

import com.musclenote.android.data.util.parseInstantOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId

/**
 * Validation de forme d'une clé de sauvegarde (SPEC § 5.3), identique en v1 et v2. Elle
 * précède la migration : celle-ci peut donc compter sur ces formes. Une seule clé invalide
 * fait refuser tout l'import.
 *
 * Écarts assumés par rapport au validateur de l'app Expo : les ids (séance, exercice,
 * complément) doivent être non vides (§ 3.15) et la date d'une pesée doit être une date.
 */
internal class BackupValidator(private val zone: ZoneId) {

    fun isValid(key: String, value: JsonElement): Boolean = when (key) {
        BackupKeys.WORKOUTS -> value.isArrayOf(::isWorkout)
        BackupKeys.EXERCISES -> value.isArrayOf { e ->
            e is JsonObject && e["id"].isNonEmptyString() && e["name"].isString() && e["muscleGroup"].isString()
        }
        BackupKeys.BODY_WEIGHTS -> value.isArrayOf { e ->
            e is JsonObject && isInstant(e["date"]) && e["weight"].isFiniteNumber()
        }
        BackupKeys.SUPPLEMENTS -> value.isArrayOf { e ->
            e is JsonObject && e["id"].isNonEmptyString() && e["name"].isString()
        }
        BackupKeys.SUPPLEMENTS_TAKEN, BackupKeys.DAILY_TRACKER, BackupKeys.PREFERENCES -> value is JsonObject
        else -> false
    }

    private fun isWorkout(w: JsonElement): Boolean =
        w is JsonObject &&
            w["id"].isNonEmptyString() &&
            isInstant(w["date"]) &&
            (w["name"].isAbsentOrNull() || w["name"].isString()) &&
            (w["bodyWeight"].isAbsentOrNull() || w["bodyWeight"].isFiniteNumber()) &&
            (w["preset"].isAbsentOrNull() || w["preset"].isString()) &&
            w["exercises"].isArrayOf { e ->
                e is JsonObject && e["exerciseId"].isString() && e["sets"].isArrayOf(::isSet)
            }

    private fun isSet(s: JsonElement): Boolean =
        s is JsonObject &&
            s["reps"].isFiniteNumber() &&
            s["weight"].isFiniteNumber() &&
            CARDIO_FIELDS.all { s[it].isAbsentOrNull() || s[it].isFiniteNumber() }

    private fun isInstant(v: JsonElement?): Boolean =
        v.stringOrNull()?.let { parseInstantOrNull(it, zone) } != null

    private inline fun JsonElement?.isArrayOf(check: (JsonElement) -> Boolean): Boolean =
        this is JsonArray && all(check)

    companion object {
        val CARDIO_FIELDS = listOf("duration", "distance", "speed", "incline")
    }
}
