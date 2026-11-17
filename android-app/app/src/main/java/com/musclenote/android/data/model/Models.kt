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
    /** Muscles principaux (§ 15.17.3, 🔄 D43), dans l'ordre de [Muscle] ; facultatif, omis vide. */
    @Serializable(with = MuscleListSerializer::class)
    val primaryMuscles: List<Muscle> = emptyList(),
    /** Muscles secondaires, jamais un principal ; facultatif, omis vide. */
    @Serializable(with = MuscleListSerializer::class)
    val secondaryMuscles: List<Muscle> = emptyList(),
    /** « Charge × reps » ou « au temps » (§ 15.18.1, 🔄 D44) ; omis au défaut, inconnu → défaut. */
    val type: ExerciseType = ExerciseType.Reps,
)

/**
 * Série. Les champs cardio sont en minutes, km, km/h et %. [durationSeconds] : durée d'une série au
 * temps, en secondes (§ 15.18.8, 🔄 D44) — une série qui en a une est une « série au temps » ; omise
 * absente, lue sans jamais refuser le fichier.
 */
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
    @Serializable(with = HoldSecondsSerializer::class)
    val durationSeconds: Int? = null,
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
    /**
     * Historique des prises (§ 15.15.4, 🔄 D41) : jour local (`"AAAA-MM-JJ"`) → nombre de doses
     * (≥ 1), un seul enregistrement par jour. Facultatif, omis quand il est vide ; un ancien fichier
     * sans ce champ se relit avec un historique vide.
     */
    val intakes: Map<String, Int> = emptyMap(),
    /**
     * Doses comptées par une coche « pris aujourd'hui » (§ 15.16.4, 🔄 D42), 1 à 20 ; omis à 1.
     */
    val dosesPerDay: Int = DEFAULT_DOSES_PER_DAY,
    /** Stock suivi (§ 15.16.8, 🔄 D42) ; absent = stock non suivi. */
    val stock: SupplementStock? = null,
    /** Seuil du stock bas, en jours restants (§ 15.16.4, 🔄 D42), 0 à 99 ; omis à 7. */
    val lowStockDays: Int = DEFAULT_LOW_STOCK_DAYS,
) {
    companion object {
        const val DEFAULT_DOSES_PER_DAY = 1
        const val DEFAULT_LOW_STOCK_DAYS = 7
    }
}

/**
 * Stock d'un complément (§ 15.16.8, 🔄 D42) : [doses] comptées le jour local [day], après [dayIntake]
 * doses déjà prises ce jour-là (omis à 0). Le stock restant se déduit de l'historique des prises.
 */
@Serializable
data class SupplementStock(
    val doses: Int,
    val day: String,
    val dayIntake: Int = 0,
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
    /** Protéines du jour, en grammes (§ 15.16.1, 🔄 D42) ; omis s'il est vide. */
    val proteinLog: Map<String, Int> = emptyMap(),
    /** Objectif de protéines, non daté (§ 15.16.1, 🔄 D42) ; omis au défaut. */
    val proteinGoal: ProteinGoal = ProteinGoal.DEFAULT,
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

/** Mode de l'objectif de protéines (§ 15.16.1, 🔄 D42). */
@Serializable
enum class ProteinGoalMode {
    /** Poids de la dernière pesée connue × [ProteinGoal.perKg]. */
    @SerialName("perKg") PerKg,

    /** [ProteinGoal.grams], chaque jour. */
    @SerialName("fixed") Fixed,
}

/**
 * Objectif de protéines (§ 15.16.1, 🔄 D42) : le [mode], le facteur [perKg] (g par kg, 1,2 à 2,2) et
 * la valeur fixe [grams] (absente tant qu'elle n'a pas été saisie). Le mode et le facteur sont écrits
 * avec l'objectif (il est omis au défaut : par kg, 1,6).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProteinGoal(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val mode: ProteinGoalMode = ProteinGoalMode.PerKg,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val perKg: Double = DEFAULT_PER_KG,
    val grams: Int? = null,
) {
    companion object {
        const val DEFAULT_PER_KG = 1.6
        const val MIN_PER_KG = 1.2
        const val MAX_PER_KG = 2.2

        /** Par kg, 1,6. */
        val DEFAULT = ProteinGoal()
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

/**
 * Chrono d'une série au temps (§ 15.18.4, 🔄 D44) : la série [setId] de la séance en cours, partie à
 * [startedAt] (ms depuis l'époque). Propre à l'appareil, hors sauvegarde (`set_timer.json`).
 */
@Serializable
data class SetTimerState(
    val setId: String,
    val startedAt: Long,
)

/** État du minuteur de repos (non sauvegardé, § 7.2). */
@Serializable
data class RestTimerState(
    /** Instant de fin, en ms depuis l'époque. */
    val endsAt: Long,
    /** Durée totale, en secondes. */
    val total: Int,
    val exerciseName: String,
)
