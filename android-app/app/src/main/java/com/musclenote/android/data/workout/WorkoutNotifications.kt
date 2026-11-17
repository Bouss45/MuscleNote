package com.musclenote.android.data.workout

import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.Workout

/**
 * Notification « Séance en cours » sur l'appareil (SPEC § 15.5, 🔄 D31), séparée des dépôts pour
 * que leur logique reste testable sur la JVM. Appelée par `AppContainer`, une publication à la
 * fois, toujours avec l'état le plus récent.
 */
interface WorkoutNotifications {
    /**
     * (Re)publie la notification de [workout], avec le repos [rest] (ou aucun) et le chrono d'une série
     * au temps [setTimer] (ou aucun, § 15.18.5) ; [custom] = exercices perso.
     */
    fun show(workout: Workout, rest: RestTimerState?, custom: List<Exercise>, setTimer: SetTimerState?)

    /** Plus de séance en cours : notification retirée. */
    fun cancel()

    /** Nom affiché de l'exercice [exerciseId] dans la langue de l'app (nom du repos lancé depuis la notification). */
    fun exerciseName(exerciseId: String, custom: List<Exercise>): String

    /** Aucun effet (tests, aperçus). */
    object None : WorkoutNotifications {
        override fun show(workout: Workout, rest: RestTimerState?, custom: List<Exercise>, setTimer: SetTimerState?) = Unit
        override fun cancel() = Unit
        override fun exerciseName(exerciseId: String, custom: List<Exercise>): String =
            custom.firstOrNull { it.id == exerciseId }?.name ?: exerciseId
    }
}
