package com.musclenote.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modèle de données (SPEC § 4.1). La forme JSON est celle du format de sauvegarde v2 (§ 5.2) :
// les champs requis n'ont pas de valeur par défaut (toujours écrits) ; les champs optionnels
// ont une valeur par défaut et sont omis quand ils la valent (jamais de `null` écrit).

/** Exercice personnalisé (les exercices intégrés ne sont jamais stockés, § 6.1). */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    /** `true` = supprimé mais encore référencé par une séance (§ 3.9). */
    val archived: Boolean = false,
)

/** Série. Les champs cardio sont en minutes, km, km/h et %. */
@Serializable
data class WorkoutSet(
    val id: String,
    val reps: Int,
    val weight: Double,
    val completed: Boolean,
    val duration: Double? = null,
    val distance: Double? = null,
    val speed: Double? = null,
    val incline: Double? = null,
)

@Serializable
data class WorkoutExercise(
    val exerciseId: String,
    val note: String? = null,
    val sets: List<WorkoutSet>,
)

/** Séance. `date` = début de séance, instant ISO 8601 UTC avec millisecondes. */
@Serializable
data class Workout(
    val id: String,
    val name: String,
    val preset: PresetId? = null,
    val date: String,
    /** kg ; 0 = inconnu. */
    val bodyWeight: Double,
    val exercises: List<WorkoutExercise>,
    /** Absent si en cours ou auto-finalisée (§ 3.4). */
    val durationMinutes: Int? = null,
)

/** Pesée : `date` = midi local du jour, instant ISO UTC (§ 3.11). */
@Serializable
data class BodyWeightEntry(
    val date: String,
    val weight: Double,
)

@Serializable
data class Reminder(
    val id: String,
    val hour: Int,
    val minute: Int,
)

@Serializable
data class Supplement(
    val id: String,
    val name: String,
    val dosage: String,
    val unit: String,
    val reminderEnabled: Boolean,
    val reminders: List<Reminder>,
)

/** `supplementId` → jour local (`"AAAA-MM-JJ"`) de la dernière prise. */
typealias TakenRecord = Map<String, String>

/**
 * Suivi calories / eau (§ 3.12). Les historiques d'objectif associent à un jour local l'objectif
 * en vigueur à partir de ce jour ; ils contiennent toujours [GOAL_HISTORY_ORIGIN].
 */
@Serializable
data class DailyTracker(
    val calorieGoal: Int,
    /** ml. */
    val waterGoal: Int,
    val calorieGoalHistory: Map<String, Int>,
    val waterGoalHistory: Map<String, Int>,
    val caloriesLog: Map<String, Int>,
    /** ml. */
    val waterLog: Map<String, Int>,
) {
    companion object {
        const val DEFAULT_CALORIE_GOAL = 2000
        const val DEFAULT_WATER_GOAL = 2000

        /** Clé d'historique valable pour tout le passé. */
        const val GOAL_HISTORY_ORIGIN = "0000-01-01"

        val DEFAULT = DailyTracker(
            calorieGoal = DEFAULT_CALORIE_GOAL,
            waterGoal = DEFAULT_WATER_GOAL,
            calorieGoalHistory = mapOf(GOAL_HISTORY_ORIGIN to DEFAULT_CALORIE_GOAL),
            waterGoalHistory = mapOf(GOAL_HISTORY_ORIGIN to DEFAULT_WATER_GOAL),
            caloriesLog = emptyMap(),
            waterLog = emptyMap(),
        )

        /** Objectif en vigueur le jour [day] : valeur de la plus grande clé ≤ [day]. */
        fun goalFor(history: Map<String, Int>, day: String): Int? =
            history.entries.filter { it.key <= day }.maxByOrNull { it.key }?.value
    }
}

@Serializable
enum class ThemeMode {
    @SerialName("system") System,
    @SerialName("light") Light,
    @SerialName("dark") Dark,
}

@Serializable
data class Preferences(
    val restSeconds: Int,
    val theme: ThemeMode,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 180
        val DEFAULT = Preferences(restSeconds = DEFAULT_REST_SECONDS, theme = ThemeMode.System)
    }
}

/**
 * Préférences locales à l'appareil, **hors sauvegarde** (§ 3.14) : langue choisie, carte de transfert de
 * l'Accueil masquée (§ 2.2).
 */
@Serializable
data class LocalPreferences(
    val migrateCardDismissed: Boolean = false,
    /**
     * Langue choisie dans l'app (`"fr"`, `"en"`, absente = système) : copie de celle d'AppCompat,
     * qu'AppCompat ne connaît, avant Android 13, qu'une fois une activité créée (§ 9.4).
     */
    val appLanguage: String? = null,
) {
    companion object {
        val DEFAULT = LocalPreferences()
    }
}

/** État du minuteur de repos (non sauvegardé, § 7.2). */
@Serializable
data class RestTimerState(
    /** Instant de fin, en ms depuis l'époque. */
    val endsAt: Long,
    /** Durée totale, en secondes. */
    val total: Int,
    val exerciseName: String,
)
