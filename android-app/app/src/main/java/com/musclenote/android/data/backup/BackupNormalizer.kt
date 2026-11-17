package com.musclenote.android.data.backup

import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.catalog.MuscleRules
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.ExerciseType
import com.musclenote.android.data.model.HoldSecondsSerializer
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RemovedWorkoutType
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.SupplementStock
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.nutrition.ProteinRules
import com.musclenote.android.data.nutrition.StockRules
import com.musclenote.android.data.nutrition.WeighInReminderRules
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.isDayKey
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutTypeRules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId

/**
 * Normalisation d'une sauvegarde au format courant (après validation et migrations) vers le
 * modèle typé (SPEC § 5.3 « Normalisation v2 », § 4.1).
 *
 * Elle reste tolérante pour un fichier v2 retouché à la main : champs optionnels `null` →
 * absents, nombres arrondis là où le modèle attend un entier, valeurs inutilisables écartées.
 * Les dates sont réécrites au format canonique (`…T…:…:….SSSZ`) et les listes triées.
 */
internal class BackupNormalizer(private val ctx: MigrationContext, private val zone: ZoneId) {

    private val today = dayKey(ctx.today)

    fun normalize(root: JsonObject): BackupData {
        val supplements = root[BackupKeys.SUPPLEMENTS]?.let { supplements(it as JsonArray) }
        return BackupData(
            workouts = root[BackupKeys.WORKOUTS]?.let { workouts(it as JsonArray) },
            exercises = root[BackupKeys.EXERCISES]?.let { exercises(it as JsonArray) },
            bodyWeights = root[BackupKeys.BODY_WEIGHTS]?.let { bodyWeights(it as JsonArray) },
            supplements = supplements,
            supplementsTaken = root[BackupKeys.SUPPLEMENTS_TAKEN]?.let {
                taken(it as JsonObject, supplements?.mapTo(HashSet()) { s -> s.id })
            },
            dailyTracker = root[BackupKeys.DAILY_TRACKER]?.let { tracker(it as JsonObject) },
            preferences = root[BackupKeys.PREFERENCES]?.let { preferences(it as JsonObject) },
        )
    }

    // ─── Séances ────────────────────────────────────────────────────────────────

    private fun workouts(array: JsonArray): List<Workout> =
        array.map { workout(it as JsonObject) }
            .distinctBy { it.first.id }
            .sortedByDescending { it.second }
            .map { it.first }

    /** Séance + son instant (pour le tri). */
    private fun workout(w: JsonObject): Pair<Workout, Instant> {
        val instant = instant(w["date"])
        val workout = Workout(
            id = w["id"].stringOrNull()!!,
            name = w["name"].stringOrNull() ?: "",
            preset = w["preset"].stringOrNull()?.let(PresetId::fromId), // inconnu → retiré
            date = formatInstant(instant),
            bodyWeight = w["bodyWeight"].numberOrNull() ?: 0.0,
            exercises = (w["exercises"] as JsonArray).map { workoutExercise(it as JsonObject) },
            durationMinutes = w["durationMinutes"].intOrNull(),
        )
        return workout to instant
    }

    private fun workoutExercise(e: JsonObject) = WorkoutExercise(
        exerciseId = e["exerciseId"].stringOrNull()!!,
        note = e["note"].stringOrNull(),
        sets = (e["sets"] as JsonArray).map { workoutSet(it as JsonObject) },
    )

    private fun workoutSet(s: JsonObject) = WorkoutSet(
        id = s["id"].stringOrNull()?.takeIf { it.isNotEmpty() } ?: ctx.newId(),
        reps = s["reps"].intOrNull() ?: 0,
        weight = s["weight"].numberOrNull() ?: 0.0,
        completed = s["completed"].booleanOrNull() ?: false,
        duration = s["duration"].numberOrNull(),
        distance = s["distance"].numberOrNull(),
        speed = s["speed"].numberOrNull(),
        incline = s["incline"].numberOrNull(),
        // 🔄 D44 : lue sans refuser l'import, arrondie à l'entier, négative → 0 (§ 15.18.8).
        durationSeconds = HoldSecondsSerializer.secondsOf(s["durationSeconds"] as? JsonPrimitive),
    )

    // ─── Exercices perso ────────────────────────────────────────────────────────

    /**
     * 🔄 D16 : groupe inconnu → normalisé, sinon `dos`. Un id du catalogue est ignoré (§ 3.9). 🔄 D43 :
     * muscles lus sans jamais refuser l'import (§ 15.17.8).
     */
    private fun exercises(array: JsonArray): List<Exercise> =
        array.map { it as JsonObject }
            .map { e ->
                Exercise(
                    id = e["id"].stringOrNull()!!,
                    name = e["name"].stringOrNull()!!,
                    muscleGroup = MuscleGroup.fromLegacy(e["muscleGroup"].stringOrNull()!!),
                    archived = e["archived"].booleanOrNull() ?: false,
                    primaryMuscles = muscles(e["primaryMuscles"]),
                    secondaryMuscles = muscles(e["secondaryMuscles"]),
                    // 🔄 D44 : absent ou inconnu → « charge × reps » (§ 15.18.8).
                    type = ExerciseType.fromId(e["type"].stringOrNull()),
                ).let(MuscleRules::normalized)
            }
            .filterNot { ExerciseCatalog.isBuiltIn(it.id) }
            .distinctBy { it.id }

    /** Tableau d'ids de muscles ; pas un tableau → vide, éléments inconnus écartés. */
    private fun muscles(value: JsonElement?): List<Muscle> =
        Muscle.fromIds((value as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })

    // ─── Nutrition ──────────────────────────────────────────────────────────────

    private fun bodyWeights(array: JsonArray): List<BodyWeightEntry> =
        array.map { it as JsonObject }
            .map { e -> instant(e["date"]) to e["weight"].numberOrNull()!! }
            .sortedByDescending { it.first }
            .map { (instant, weight) -> BodyWeightEntry(date = formatInstant(instant), weight = weight) }

    private fun supplements(array: JsonArray): List<Supplement> =
        array.map { it as JsonObject }
            .map { s ->
                Supplement(
                    id = s["id"].stringOrNull()!!,
                    name = s["name"].stringOrNull()!!,
                    dosage = dosage(s["dosage"]),
                    unit = s["unit"].stringOrNull() ?: "g",
                    reminderEnabled = s["reminderEnabled"].booleanOrNull() ?: false,
                    reminders = s["reminders"].arrayOrNull().orEmpty().mapNotNull(::reminder),
                    intakes = intakes(s["intakes"]),
                    dosesPerDay = StockRules.normalizedDosesPerDay(s["dosesPerDay"].intOrNull()),
                    stock = stock(s["stock"]),
                    lowStockDays = StockRules.normalizedLowStockDays(s["lowStockDays"].intOrNull()),
                )
            }
            .distinctBy { it.id }

    /** Le dosage est un texte libre ; un nombre (fichier retouché) est converti tel quel. */
    private fun dosage(v: JsonElement?): String {
        v.stringOrNull()?.let { return it }
        val n = v.numberOrNull() ?: return ""
        return if (n % 1.0 == 0.0 && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()
    }

    /**
     * Historique des prises (§ 15.15.4, 🔄 D41) : absent ou pas un objet → vide ; une entrée dont la
     * clé n'est pas un jour valide ou dont la valeur n'est pas un entier ≥ 1 est écartée.
     */
    private fun intakes(v: JsonElement?): Map<String, Int> {
        val o = v as? JsonObject ?: return emptyMap()
        return o.mapNotNull { (day, doses) ->
            if (!isDayKey(day)) return@mapNotNull null
            val primitive = doses as? JsonPrimitive ?: return@mapNotNull null
            if (primitive.isString) return@mapNotNull null
            val n = primitive.content.toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
            day to n
        }.toMap()
    }

    /**
     * Stock d'un complément (§ 15.16.8, 🔄 D42) : pas un objet, `doses` pas un entier ≥ 0 ou `day` pas un
     * jour valide → stock non suivi ; `dayIntake` pas un entier ≥ 0 → 0.
     */
    private fun stock(v: JsonElement?): SupplementStock? {
        val o = v.objectOrNull() ?: return null
        val doses = o["doses"].intOrNull()?.takeIf { it >= 0 } ?: return null
        val day = o["day"].stringOrNull()?.takeIf(::isDayKey) ?: return null
        return SupplementStock(doses, day, dayIntake = o["dayIntake"].intOrNull()?.takeIf { it >= 0 } ?: 0)
    }

    /** Rappel inutilisable (heure ou minute hors bornes) → écarté. `notifId` n'existe plus. */
    private fun reminder(v: JsonElement): Reminder? {
        val r = v as? JsonObject ?: return null
        val hour = r["hour"].intOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = r["minute"].intOrNull()?.takeIf { it in 0..59 } ?: return null
        return Reminder(id = r["id"].stringOrNull()?.takeIf { it.isNotEmpty() } ?: ctx.newId(), hour = hour, minute = minute)
    }

    /**
     * Entrées « pris » : valeur = jour valide ; celles d'un complément inconnu sont retirées
     * quand le fichier contient la liste des compléments ([knownIds] non `null`).
     */
    private fun taken(t: JsonObject, knownIds: Set<String>?): TakenRecord =
        t.mapNotNull { (id, v) ->
            val day = v.stringOrNull()?.takeIf(::isDayKey) ?: return@mapNotNull null
            if (knownIds != null && id !in knownIds) null else id to day
        }.toMap()

    /**
     * Objectifs : historique absent ou vide → `{ "0000-01-01": objectif courant }` ; objectif
     * courant différent de l'objectif en vigueur aujourd'hui (fichier modifié par l'app Expo)
     * → `history[aujourd'hui] = objectif courant`. Journaux : entiers ≥ 0 par jour valide.
     */
    private fun tracker(t: JsonObject): DailyTracker {
        val calorieHistory = dayIntMap(t["calorieGoalHistory"]).filterValues { it > 0 }
        val waterHistory = dayIntMap(t["waterGoalHistory"]).filterValues { it > 0 }
        val calorieGoal = t["calorieGoal"].intOrNull()?.takeIf { it > 0 }
            ?: DailyTracker.goalFor(calorieHistory, today) ?: DailyTracker.DEFAULT_CALORIE_GOAL
        val waterGoal = t["waterGoal"].intOrNull()?.takeIf { it > 0 }
            ?: DailyTracker.goalFor(waterHistory, today) ?: DailyTracker.DEFAULT_WATER_GOAL
        return DailyTracker(
            calorieGoal = calorieGoal,
            waterGoal = waterGoal,
            calorieGoalHistory = goalHistory(calorieHistory, calorieGoal),
            waterGoalHistory = goalHistory(waterHistory, waterGoal),
            caloriesLog = dayIntMap(t["caloriesLog"]).mapValues { (_, v) -> v.coerceAtLeast(0) },
            waterLog = dayIntMap(t["waterLog"]).mapValues { (_, v) -> v.coerceAtLeast(0) },
            proteinLog = dayIntMap(t["proteinLog"]).mapValues { (_, v) -> v.coerceAtLeast(0) },
            proteinGoal = proteinGoal(t["proteinGoal"]),
        )
    }

    /**
     * Objectif de protéines (§ 15.16.8, 🔄 D42) : absent ou pas un objet → par kg, 1,6 ; mode inconnu →
     * par kg ; facteur hors 1,2 – 2,2 → 1,6 ; valeur fixe pas un entier > 0 → absente.
     */
    private fun proteinGoal(v: JsonElement?): ProteinGoal {
        val o = v.objectOrNull() ?: return ProteinGoal.DEFAULT
        return ProteinRules.normalized(
            ProteinGoal(
                mode = if (o["mode"].stringOrNull() == "fixed") ProteinGoalMode.Fixed else ProteinGoalMode.PerKg,
                perKg = o["perKg"].numberOrNull() ?: ProteinGoal.DEFAULT_PER_KG,
                grams = o["grams"].intOrNull(),
            ),
        )
    }

    private fun goalHistory(history: Map<String, Int>, currentGoal: Int): Map<String, Int> {
        val result = history.toSortedMap()
        if (result.isEmpty()) result[DailyTracker.GOAL_HISTORY_ORIGIN] = currentGoal
        // L'entrée d'origine doit exister : on prolonge vers le passé la plus ancienne valeur.
        if (DailyTracker.GOAL_HISTORY_ORIGIN !in result) result[DailyTracker.GOAL_HISTORY_ORIGIN] = result.getValue(result.firstKey())
        if (DailyTracker.goalFor(result, today) != currentGoal) result[today] = currentGoal
        return result
    }

    /** Objet `{ "AAAA-MM-JJ": nombre }` → map triée ; clés non-jours et non-nombres écartés. */
    private fun dayIntMap(v: JsonElement?): Map<String, Int> =
        v.objectOrNull().orEmpty()
            .mapNotNull { (k, value) -> value.intOrNull()?.let { k to it }?.takeIf { isDayKey(k) } }
            .toMap()
            .toSortedMap()

    // ─── Préférences ────────────────────────────────────────────────────────────

    private fun preferences(p: JsonObject) = Preferences(
        restSeconds = p["restSeconds"].intOrNull()?.takeIf { it > 0 } ?: Preferences.DEFAULT_REST_SECONDS,
        theme = when (p["theme"].stringOrNull()) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            "system" -> ThemeMode.System
            // Absent ou invalide : le thème par défaut, sombre (🔄 D35).
            else -> Preferences.DEFAULT_THEME
        },
        weighInReminder = weighInReminder(p["weighInReminder"]),
        removedWorkoutTypes = removedWorkoutTypes(p["removedWorkoutTypes"]),
    )

    /**
     * Types retirés de « Mes types » (§ 15.19.5, 🔄 D45), lus sans jamais refuser l'import : pas un tableau
     * → aucun ; entrée sans `name` ou `removedAt` texte, nom vide ou instant illisible → écartée ; même nom
     * normalisé → le retrait le plus récent.
     */
    private fun removedWorkoutTypes(v: JsonElement?): List<RemovedWorkoutType> =
        WorkoutTypeRules.normalized(
            v.arrayOrNull().orEmpty().mapNotNull { item ->
                val entry = item.objectOrNull() ?: return@mapNotNull null
                val name = entry["name"].stringOrNull() ?: return@mapNotNull null
                val removedAt = entry["removedAt"].stringOrNull() ?: return@mapNotNull null
                RemovedWorkoutType(name, removedAt)
            },
        )

    /**
     * Rappel de pesée (§ 15.13.5, 🔄 D38), lu champ par champ sans jamais refuser l'import : absent ou
     * pas un objet → le défaut ; `enabled` non booléen → `false` ; heure ou minute invalide → 07:30 ;
     * jours : entiers de 1 à 7, sans doublon, triés ; absents ou pas un tableau → tous les jours.
     */
    private fun weighInReminder(v: JsonElement?): WeighInReminder {
        val r = v.objectOrNull() ?: return WeighInReminder.DEFAULT
        return WeighInReminderRules.normalized(
            WeighInReminder(
                enabled = r["enabled"].booleanOrNull() ?: false,
                hour = r["hour"].intOrNull() ?: -1,
                minute = r["minute"].intOrNull() ?: -1,
                days = r["days"].arrayOrNull()?.mapNotNull { it.intOrNull() } ?: WeighInReminder.ALL_DAYS,
            ),
        )
    }

    /** Dates déjà validées (§ 5.3) : la lecture ne peut pas échouer ici. */
    private fun instant(v: JsonElement?): Instant = checkNotNull(parseInstantOrNull(v.stringOrNull()!!, zone))
}
