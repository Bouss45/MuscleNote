package com.musclenote.android.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Workout(
    val id: String,
    val name: String,
    val preset: PresetId? = null,
    val date: String,
    /**
     * kg ; 0 = non saisi (pesée passée au début de la séance, ou jour déjà pesé, § 15.13). Lu absent
     * comme 0 mais **toujours écrit** : les versions précédentes de l'app l'exigent (🔄 D38).
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val bodyWeight: Double = 0.0,
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

/**
 * Rappel de pesée (§ 15.13.3, 🔄 D38) : activé ou non, heure locale, jours ISO (1 = lundi … 7 =
 * dimanche). Tous les champs sont écrits quand le rappel l'est (il est omis au défaut).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WeighInReminder(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val enabled: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val hour: Int = DEFAULT_HOUR,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val minute: Int = DEFAULT_MINUTE,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val days: List<Int> = ALL_DAYS,
) {
    companion object {
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 30
        val ALL_DAYS: List<Int> = (1..7).toList()

        /** Désactivé, 07:30, tous les jours. */
        val DEFAULT = WeighInReminder()
    }
}

@Serializable
data class Preferences(
    val restSeconds: Int,
    val theme: ThemeMode,
    /** Omis quand il vaut [WeighInReminder.DEFAULT] (§ 15.13.5). */
    val weighInReminder: WeighInReminder = WeighInReminder.DEFAULT,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 180
        /** Thème sombre par défaut (🔄 D35, § 15.10.4) ; un choix enregistré est toujours gardé. */
        val DEFAULT_THEME = ThemeMode.Dark
        val DEFAULT = Preferences(restSeconds = DEFAULT_REST_SECONDS, theme = DEFAULT_THEME)
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
