package com.musclenote.android.data.workout

import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet

/** Une série de la séance en cours et son exercice. */
data class LocatedSet(val exercise: WorkoutExercise, val set: WorkoutSet)

/**
 * Chronomètre d'une série au temps (SPEC § 15.18.4, 🔄 D44), en fonctions pures : un seul à la fois,
 * sur une série **non validée** de la séance en cours ; seul l'instant de départ est gardé, le temps
 * écoulé se recalcule.
 */
object SetTimerRules {

    /** Temps écoulé à [nowMillis], en secondes entières (partie entière), entre 0 et 99:59. */
    fun elapsedSeconds(state: SetTimerState, nowMillis: Long): Int =
        ((nowMillis - state.startedAt) / 1_000).coerceIn(0, HoldDuration.MAX_SECONDS.toLong()).toInt()

    /** La série [setId] de [workout] et son exercice ; `null` si elle n'y est pas. */
    fun locate(workout: Workout?, setId: String): LocatedSet? {
        workout ?: return null
        for (exercise in workout.exercises) {
            exercise.sets.firstOrNull { it.id == setId }?.let { return LocatedSet(exercise, it) }
        }
        return null
    }

    /**
     * [state] s'il vaut toujours : sa série est dans [workout] et n'est pas validée. Sinon (série
     * supprimée, exercice retiré, séance finie, série validée autrement) le chrono ne compte plus.
     */
    fun activeOn(state: SetTimerState?, workout: Workout?): SetTimerState? {
        state ?: return null
        val located = locate(workout, state.setId) ?: return null
        return state.takeUnless { located.set.completed }
    }

    /** Démarrer est permis sur une série non validée, sans autre chrono en cours. */
    fun canStart(running: SetTimerState?, set: WorkoutSet): Boolean = running == null && !set.completed

    /**
     * Arrêt (§ 15.18.4) : [set] prend le temps écoulé comme durée ; « Arrêter » (ou la case) le
     * valide aussi ([validate]), l'ouverture du pavé non.
     */
    fun stopped(set: WorkoutSet, state: SetTimerState, nowMillis: Long, validate: Boolean): WorkoutSet =
        set.copy(durationSeconds = elapsedSeconds(state, nowMillis), completed = set.completed || validate)
}
