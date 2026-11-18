package com.musclenote.android.data.workout

import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.model.counted
import kotlin.math.roundToLong

/**
 * Séance de référence d'un exercice (SPEC § 15.7.2) : la séance terminée la plus récente où il a
 * au moins une série comptée (validée, pas de chauffe, 🔄 D47). [completedSets] = ses séries de travail
 * validées, dans l'ordre ; [completedWarmups] = ses séries de chauffe validées.
 */
data class ReferenceSession(val workout: Workout, val exercise: WorkoutExercise) {
    val completedSets: List<WorkoutSet> = exercise.sets.filter { it.counted }
    val completedWarmups: List<WorkoutSet> = exercise.sets.filter { it.completed && it.warmup }

    /** Colonne « Préc. » de la série de travail d'indice [index] (0 = la première) ; `null` → « — ». */
    fun previous(index: Int): WorkoutSet? = completedSets.getOrNull(index)

    /** Colonne « Préc. » d'une série numérotée [number] (§ 15.21.4) : rangs séparés pour les chauffes. */
    fun previous(number: SetNumber): WorkoutSet? =
        (if (number.warmup) completedWarmups else completedSets).getOrNull(number.rank - 1)
}

/**
 * Suggestion de charge (SPEC § 15.7.3) : +2,5 kg — +1 rep au [bodyWeight] — si [increase], sinon
 * la même charge (les mêmes reps). [hold] : suggestion de durée d'un exercice au temps, +5 s ou la
 * même durée (§ 15.18.2, 🔄 D44).
 */
data class LoadSuggestion(val bodyWeight: Boolean, val increase: Boolean, val hold: Boolean = false) {
    /** Ajout à la charge de la série « Préc. » (kg). */
    val weightDelta: Double get() = if (increase && !bodyWeight && !hold) WEIGHT_STEP else 0.0

    /** Ajout aux reps de la série « Préc. ». */
    val repsDelta: Int get() = if (increase && bodyWeight && !hold) REPS_STEP else 0

    /** Ajout à la durée de la série « Préc. » (s). */
    val secondsDelta: Int get() = if (increase && hold) HoldDuration.SUGGESTION_SECONDS else 0

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
                .firstOrNull { it.exerciseId == exerciseId && it.sets.any { set -> set.counted } }
                ?.let { ReferenceSession(workout, it) }
        }

    /**
     * Toucher « Préc. » (§ 15.7.2) : [target] prend les valeurs de [previous] (reps, charge, durée,
     * distance, vitesse, inclinaison, durée d'une série au temps) et garde son id et sa validation.
     */
    fun copyPrevious(target: WorkoutSet, previous: WorkoutSet): WorkoutSet = target.copy(
        reps = previous.reps,
        weight = previous.weight,
        duration = previous.duration,
        distance = previous.distance,
        speed = previous.speed,
        incline = previous.incline,
        durationSeconds = previous.durationSeconds,
    )

    /**
     * Suggestion de charge d'un exercice de force (§ 15.7.3) ; `null` en cardio ou sans séance de
     * référence. Reps tenues : toutes les séries de l'exercice validées dans la séance de référence,
     * chacune avec au moins les reps de la première. Poids du corps : séries validées toutes à 0 kg.
     * Au temps (§ 15.18.2) : durée tenue de la même façon, chaque série au moins aussi longue que la
     * première ; aucune suggestion si une série validée de la référence n'a pas de durée.
     */
    fun suggestion(reference: ReferenceSession?, kind: SetKind): LoadSuggestion? {
        if (reference == null || kind == SetKind.Cardio) return null
        // 🔄 D47 : seules les séries de travail comptent (§ 15.21.4).
        val done = reference.completedSets
        val allDone = reference.exercise.sets.filterNot { it.warmup }.all { it.completed }
        if (kind == SetKind.Timed) {
            val durations = done.map { it.durationSeconds ?: return null }
            return LoadSuggestion(bodyWeight = false, increase = allDone && durations.all { it >= durations.first() }, hold = true)
        }
        val target = done.first().reps
        val held = allDone && done.all { it.reps >= target }
        return LoadSuggestion(bodyWeight = done.all { it.weight == 0.0 }, increase = held)
    }

    /**
     * Toucher la suggestion (§ 15.7.3) : chaque série **non validée** de [sets] dont le rang a une
     * série « Préc. » prend sa charge + [LoadSuggestion.weightDelta] (au poids du corps : ses reps +
     * [LoadSuggestion.repsDelta] ; au temps : sa durée + [LoadSuggestion.secondsDelta], § 15.18.2). Les séries validées ne changent pas ; appliquer deux fois donne le
     * même résultat. Renvoie [sets] lui-même si rien ne change.
     */
    fun applySuggestion(sets: List<WorkoutSet>, reference: ReferenceSession, suggestion: LoadSuggestion): List<WorkoutSet> {
        val numbers = SetNumbering.of(sets)
        val applied = sets.mapIndexed { index, set ->
            val previous = reference.previous(numbers[index])
            when {
                // 🔄 D47 : les séries de chauffe ne bougent pas (§ 15.21.4).
                set.completed || set.warmup || previous == null -> set
                suggestion.hold -> set.copy(durationSeconds = HoldDuration.clamp((previous.durationSeconds ?: 0) + suggestion.secondsDelta))
                suggestion.bodyWeight -> set.copy(reps = previous.reps + suggestion.repsDelta)
                else -> set.copy(weight = roundHundredth(previous.weight + suggestion.weightDelta))
            }
        }
        return if (applied == sets) sets else applied
    }

    /**
     * Place du bandeau de repos (§ 15.7.1) : id de la dernière série validée dans l'ordre d'enchaînement
     * (🔄 D46, § 15.20.3 : l'ordre de la séance sans supersérie), parmi les exercices affichés
     * ([isShown]) ; `null` → en haut de la liste.
     */
    fun restAnchor(workout: Workout, isShown: (String) -> Boolean = { true }): String? =
        SupersetRules.chainOrder(workout.exercises.filter { isShown(it.exerciseId) })
            .lastOrNull { it.set.completed }
            ?.set
            ?.id

    private fun roundHundredth(value: Double): Double = (value * 100).roundToLong() / 100.0
}
