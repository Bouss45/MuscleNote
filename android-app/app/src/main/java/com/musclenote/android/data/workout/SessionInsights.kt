package com.musclenote.android.data.workout

import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import kotlin.math.roundToLong

/**
 * Séance de référence d'un exercice (SPEC § 15.7.2) : la séance terminée la plus récente où il a
 * au moins une série validée. [completedSets] = ses séries validées, dans l'ordre.
 */
data class ReferenceSession(val workout: Workout, val exercise: WorkoutExercise) {
    val completedSets: List<WorkoutSet> = exercise.sets.filter { it.completed }

    /** Colonne « Préc. » de la ligne d'indice [index] (0 = première série) ; `null` → « — ». */
    fun previous(index: Int): WorkoutSet? = completedSets.getOrNull(index)
}

/**
 * Suggestion de charge (SPEC § 15.7.3) : +2,5 kg — +1 rep au [bodyWeight] — si [increase], sinon
 * la même charge (les mêmes reps).
 */
data class LoadSuggestion(val bodyWeight: Boolean, val increase: Boolean) {
    /** Ajout à la charge de la série « Préc. » (kg). */
    val weightDelta: Double get() = if (increase && !bodyWeight) WEIGHT_STEP else 0.0

    /** Ajout aux reps de la série « Préc. ». */
    val repsDelta: Int get() = if (increase && bodyWeight) REPS_STEP else 0

    companion object {
        const val WEIGHT_STEP = 2.5
        const val REPS_STEP = 1
    }
}

/**
 * Ce que la séance en cours tire de l'historique (SPEC § 15.7, 🔄 D32) : colonne « Préc. »,
 * suggestion de charge, place du repos. Fonctions pures ; l'historique passé en paramètre est celui
 * du dépôt, trié de la plus récente à la plus ancienne (comme pour le pré-remplissage, § 3.2).
 */
object SessionInsights {

    /** Séance de référence de [exerciseId] dans [history] (§ 15.7.2) ; `null` s'il n'y en a pas. */
    fun reference(history: List<Workout>, exerciseId: String): ReferenceSession? =
        history.firstNotNullOfOrNull { workout ->
            workout.exercises
                .firstOrNull { it.exerciseId == exerciseId && it.sets.any { set -> set.completed } }
                ?.let { ReferenceSession(workout, it) }
        }

    /**
     * Toucher « Préc. » (§ 15.7.2) : [target] prend les valeurs de [previous] (reps, charge, durée,
     * distance, vitesse, inclinaison) et garde son id et sa validation.
     */
    fun copyPrevious(target: WorkoutSet, previous: WorkoutSet): WorkoutSet = target.copy(
        reps = previous.reps,
        weight = previous.weight,
        duration = previous.duration,
        distance = previous.distance,
        speed = previous.speed,
        incline = previous.incline,
    )

    /**
     * Suggestion de charge d'un exercice de force (§ 15.7.3) ; `null` en cardio ou sans séance de
     * référence. Reps tenues : toutes les séries de l'exercice validées dans la séance de référence,
     * chacune avec au moins les reps de la première. Poids du corps : séries validées toutes à 0 kg.
     */
    fun suggestion(reference: ReferenceSession?, isCardio: Boolean): LoadSuggestion? {
        if (reference == null || isCardio) return null
        val done = reference.completedSets
        val target = done.first().reps
        val held = reference.exercise.sets.all { it.completed } && done.all { it.reps >= target }
        return LoadSuggestion(bodyWeight = done.all { it.weight == 0.0 }, increase = held)
    }

    /**
     * Toucher la suggestion (§ 15.7.3) : chaque série **non validée** de [sets] dont le rang a une
     * série « Préc. » prend sa charge + [LoadSuggestion.weightDelta] (au poids du corps : ses reps +
     * [LoadSuggestion.repsDelta]). Les séries validées ne changent pas ; appliquer deux fois donne le
     * même résultat. Renvoie [sets] lui-même si rien ne change.
     */
    fun applySuggestion(sets: List<WorkoutSet>, reference: ReferenceSession, suggestion: LoadSuggestion): List<WorkoutSet> {
        val applied = sets.mapIndexed { index, set ->
            val previous = reference.previous(index)
            when {
                set.completed || previous == null -> set
                suggestion.bodyWeight -> set.copy(reps = previous.reps + suggestion.repsDelta)
                else -> set.copy(weight = roundHundredth(previous.weight + suggestion.weightDelta))
            }
        }
        return if (applied == sets) sets else applied
    }

    /**
     * Place du bandeau de repos (§ 15.7.1) : id de la dernière série validée dans l'ordre de la
     * séance, parmi les exercices affichés ([isShown]) ; `null` → en haut de la liste.
     */
    fun restAnchor(workout: Workout, isShown: (String) -> Boolean = { true }): String? =
        workout.exercises
            .filter { isShown(it.exerciseId) }
            .flatMap { it.sets }
            .lastOrNull { it.completed }
            ?.id

    private fun roundHundredth(value: Double): Double = (value * 100).roundToLong() / 100.0
}
