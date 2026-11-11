package com.musclenote.android.data.backup

import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.util.dayKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/** Ce dont les migrations ont besoin, injecté pour les tests. */
internal class MigrationContext(
    val today: LocalDate,
    val newId: () -> String,
)

/**
 * Chaîne de migrations du format de sauvegarde (🔄 D18) : chaque étape `vN → vN+1` transforme
 * l'objet racine (déjà validé, § 5.3) ; l'import les applique jusqu'à la version courante.
 */
internal object BackupMigrations {

    private val steps: Map<Int, (JsonObject, MigrationContext) -> JsonObject> = mapOf(
        1 to ::v1ToV2,
    )

    fun migrate(root: JsonObject, fromVersion: Int, ctx: MigrationContext): JsonObject {
        var current = root
        for (version in fromVersion until CURRENT_BACKUP_VERSION) {
            val step = checkNotNull(steps[version]) { "Pas de migration depuis la version $version" }
            current = step(current, ctx)
        }
        return current
    }

    /** v1 (app Expo) → v2 (§ 5.3). La normalisation commune vient ensuite. */
    private fun v1ToV2(root: JsonObject, ctx: MigrationContext): JsonObject = root.edit {
        put("version", JsonPrimitive(2))
        root[BackupKeys.WORKOUTS]?.let { put(BackupKeys.WORKOUTS, JsonArray((it as JsonArray).map { w -> workout(w as JsonObject, ctx) })) }
        root[BackupKeys.SUPPLEMENTS]?.let { put(BackupKeys.SUPPLEMENTS, JsonArray((it as JsonArray).map { s -> supplement(s as JsonObject, ctx) })) }
        root[BackupKeys.SUPPLEMENTS_TAKEN]?.let { put(BackupKeys.SUPPLEMENTS_TAKEN, taken(it as JsonObject, ctx)) }
        root[BackupKeys.DAILY_TRACKER]?.let { put(BackupKeys.DAILY_TRACKER, tracker(it as JsonObject)) }
        root[BackupKeys.PREFERENCES]?.let { put(BackupKeys.PREFERENCES, preferences(it as JsonObject)) }
    }

    /**
     * Séance : `name` / `bodyWeight` absents → `""` / `0` ; `preset` déduit du nom (🔄 D3) ;
     * séries sans `id` → id généré ; **toutes les séries marquées validées** (D21 : l'app Expo
     * les comptait toutes, les statistiques passées ne changent donc pas).
     */
    private fun workout(w: JsonObject, ctx: MigrationContext): JsonObject {
        val name = w["name"].stringOrNull() ?: ""
        // Un fichier v2 réexporté par l'app Expo redevient « v1 » mais garde son préréglage.
        val preset = w["preset"].stringOrNull()?.let(PresetId::fromId) ?: PresetId.fromLegacyName(name)
        return w.edit {
            put("name", JsonPrimitive(name))
            if (w["bodyWeight"].isAbsentOrNull()) put("bodyWeight", JsonPrimitive(0))
            if (preset != null) put("preset", JsonPrimitive(preset.id)) else remove("preset")
            put("exercises", JsonArray((w["exercises"] as JsonArray).map { e ->
                (e as JsonObject).edit {
                    put("sets", JsonArray((e["sets"] as JsonArray).map { s ->
                        (s as JsonObject).edit {
                            if (s["id"].stringOrNull().isNullOrEmpty()) put("id", JsonPrimitive(ctx.newId()))
                            put("completed", JsonPrimitive(true))
                        }
                    }))
                }
            }))
        }
    }

    /**
     * Complément : l'ancien format (un seul rappel `reminderHour` / `reminderMinute`, sans
     * liste `reminders`) devient une liste ; `notifId` disparaît (le modèle ne l'a plus).
     */
    private fun supplement(s: JsonObject, ctx: MigrationContext): JsonObject {
        if (s["reminders"] is JsonArray) return s
        val enabled = s["reminderEnabled"].booleanOrNull() ?: false
        return s.edit {
            put("dosage", s["dosage"].takeUnless { it.isAbsentOrNull() } ?: JsonPrimitive(""))
            put("unit", s["unit"].takeUnless { it.isAbsentOrNull() } ?: JsonPrimitive("g"))
            put("reminderEnabled", JsonPrimitive(enabled))
            val reminders = if (enabled) {
                listOf(buildJsonObject {
                    put("id", JsonPrimitive(ctx.newId()))
                    put("hour", JsonPrimitive(s["reminderHour"].intOrNull() ?: 8))
                    put("minute", JsonPrimitive(s["reminderMinute"].intOrNull() ?: 0))
                })
            } else {
                emptyList()
            }
            put("reminders", JsonArray(reminders))
            remove("reminderHour")
            remove("reminderMinute")
            remove("notifId")
        }
    }

    /** « Pris aujourd'hui » : seules les entrées datées d'aujourd'hui sont gardées. */
    private fun taken(t: JsonObject, ctx: MigrationContext): JsonObject {
        val today = dayKey(ctx.today)
        return JsonObject(t.filterValues { it.stringOrNull() == today })
    }

    /**
     * Suivi : ancien format « verres » (`bottleSize` absent et `waterGoal` ≤ 30) converti en ml
     * (× 250) ; `bottleSize` supprimé ; historiques d'objectifs créés à partir des objectifs
     * courants s'ils n'existent pas encore.
     */
    private fun tracker(t: JsonObject): JsonObject {
        val oldWaterGoal = t["waterGoal"].numberOrNull()
        val inGlasses = t["bottleSize"] == null && oldWaterGoal != null && oldWaterGoal <= GLASSES_FORMAT_MAX
        return t.edit {
            if (inGlasses) {
                put("waterGoal", JsonPrimitive(oldWaterGoal!! * ML_PER_GLASS))
                t["waterLog"].objectOrNull()?.let { log ->
                    put("waterLog", JsonObject(log.mapValues { (_, v) ->
                        v.numberOrNull()?.let { JsonPrimitive(it * ML_PER_GLASS) } ?: v
                    }))
                }
            }
            remove("bottleSize")
            val calorieGoal = this["calorieGoal"].numberOrNull() ?: DailyTracker.DEFAULT_CALORIE_GOAL.toDouble()
            val waterGoal = this["waterGoal"].numberOrNull() ?: DailyTracker.DEFAULT_WATER_GOAL.toDouble()
            if (t["calorieGoalHistory"] !is JsonObject) put("calorieGoalHistory", originHistory(calorieGoal))
            if (t["waterGoalHistory"] !is JsonObject) put("waterGoalHistory", originHistory(waterGoal))
        }
    }

    private fun originHistory(goal: Double) =
        JsonObject(mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to JsonPrimitive(goal)))

    /** Préférences : la langue n'en fait plus partie (🔄 D9) ; thème « système » par défaut. */
    private fun preferences(p: JsonObject): JsonObject = p.edit {
        remove("language")
        if (p["theme"].isAbsentOrNull()) put("theme", JsonPrimitive("system"))
    }

    private const val GLASSES_FORMAT_MAX = 30.0
    private const val ML_PER_GLASS = 250
}
