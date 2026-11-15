package com.musclenote.android.data.workout

import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.progress.ProgressStats
import kotlin.math.roundToLong

/** Un record de la fiche exercice (§ 15.9.1, point 6) battu dans une séance. */
enum class SummaryRecord {
    /** Record personnel : la métrique de la fiche (charge max, reps au poids du corps, durée en cardio). */
    Personal,

    /** Meilleur 1RM estimé (Epley, § 3.5), force seulement. */
    OneRepMax,
}

/** La séance précédente du même programme et la variation du volume, au kg (§ 15.12.3). */
data class VolumeComparison(val previous: Workout, val variationKg: Long)

/**
 * Un exercice du résumé : ses séries **validées**, la meilleure (§ 15.9.1, point 7), son meilleur
 * 1RM (0 en cardio ou sans charge) et les records battus.
 */
data class SummaryExercise(
    val definition: ExerciseDefinition,
    val sets: List<WorkoutSet>,
    val bestSet: WorkoutSet?,
    val bestOneRepMax: Double,
    val records: Set<SummaryRecord>,
) {
    val brokeRecord: Boolean get() = records.isNotEmpty()
}

/**
 * Résumé d'une séance terminée (§ 15.12.2) : volume arrondi au kg, séries validées, comparaison au
 * même programme (`null` la première fois) et exercices ayant au moins une série validée.
 */
data class WorkoutSummary(
    val workout: Workout,
    val volumeKg: Long,
    val completedSets: Int,
    val comparison: VolumeComparison?,
    val exercises: List<SummaryExercise>,
) {
    /** Nombre d'exercices qui ont battu au moins un record (« Records battus · n »). */
    val recordCount: Int get() = exercises.count { it.brokeRecord }
}

/**
 * Règles du résumé de fin de séance (SPEC § 15.12.3), pures et testées sur la JVM. [workouts] :
 * l'historique du dépôt, trié de la séance la plus récente à la plus ancienne ; une séance n'est
 * comparée qu'aux séances placées **après** elle (plus anciennes), pour que son résumé reste celui
 * du jour où elle a été faite. Seules les séries validées comptent (§ 3.16).
 */
object WorkoutSummaryRules {

    /** Résumé de la séance [workoutId] ; `null` si elle n'est pas dans [workouts]. */
    fun summary(workouts: List<Workout>, workoutId: String, definitions: List<ExerciseDefinition>): WorkoutSummary? {
        val index = workouts.indexOfFirst { it.id == workoutId }
        if (index < 0) return null
        val workout = workouts[index]
        val older = workouts.subList(index + 1, workouts.size)
        val byId = definitions.associateBy { it.id }
        val exercises = workout.exercises.mapNotNull { exercise ->
            // Exercice inconnu masqué (§ 10.5) ; sans série validée, il n'a pas été fait.
            val definition = byId[exercise.exerciseId] ?: return@mapNotNull null
            val sets = exercise.sets.filter { it.completed }
            if (sets.isEmpty()) return@mapNotNull null
            SummaryExercise(
                definition = definition,
                sets = sets,
                bestSet = ProgressStats.bestSet(sets, definition.isCardio),
                bestOneRepMax = if (definition.isCardio) 0.0 else WorkoutStats.bestOneRepMax(sets),
                records = recordsBroken(sets, definition.id, definition.isCardio, older),
            )
        }
        return WorkoutSummary(
            workout = workout,
            volumeKg = volumeKg(workout),
            completedSets = WorkoutStats.completedSetCount(workout),
            comparison = compareVolume(workout, older),
            exercises = exercises,
        )
    }

    /**
     * Comparaison à la plus récente des séances [older] de même clé de séance (§ 3.1 : préréglage,
     * sinon nom normalisé) : volume de [workout] − volume de celle-ci, chacun arrondi au kg.
     */
    fun compareVolume(workout: Workout, older: List<Workout>): VolumeComparison? {
        val key = WorkoutRules.sessionKey(workout)
        val previous = older.firstOrNull { WorkoutRules.sessionKey(it) == key } ?: return null
        return VolumeComparison(previous, volumeKg(workout) - volumeKg(previous))
    }

    /**
     * Records battus par les séries validées [sets] d'un exercice, contre ses séances [older] où il
     * a au moins une série validée : strictement au-dessus, jamais à égalité ; aucun la première fois.
     */
    fun recordsBroken(sets: List<WorkoutSet>, exerciseId: String, isCardio: Boolean, older: List<Workout>): Set<SummaryRecord> {
        val done = sets.filter { it.completed }
        val before = older.mapNotNull { workout ->
            workout.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets?.filter { it.completed }?.takeIf { it.isNotEmpty() }
        }
        if (done.isEmpty() || before.isEmpty()) return emptySet()

        // Charge ou reps : décidé sur les séances plus anciennes et celle-ci (un premier lest compte en kg).
        val metric = ProgressStats.metricOfSessions(isCardio, before + listOf(done))
        val records = mutableSetOf<SummaryRecord>()
        val best = before.maxOf { ProgressStats.metricValue(it, metric) }
        if (beats(ProgressStats.metricValue(done, metric), best)) records += SummaryRecord.Personal
        if (!isCardio) {
            val oneRepMax = WorkoutStats.bestOneRepMax(done)
            if (oneRepMax > 0 && beats(oneRepMax, before.maxOf { WorkoutStats.bestOneRepMax(it) })) records += SummaryRecord.OneRepMax
        }
        return records
    }

    /** Volume des séries validées (§ 3.8), arrondi au kg (demi vers le haut, § 3.5). */
    private fun volumeKg(workout: Workout): Long = WorkoutStats.volume(workout).roundToLong()

    /**
     * [value] strictement au-dessus de [record], comparés au centième (les saisies ont 2 décimales
     * au plus, § 15.7.4) : une somme de durées comme 10,1 + 20,2 égale bien 30,3.
     */
    private fun beats(value: Double, record: Double): Boolean = (value * 100).roundToLong() > (record * 100).roundToLong()
}
