package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chronomètre d'une série au temps (SPEC § 15.18.4, 🔄 D44). */
class SetTimerRulesTest {

    private val startedAt = 1_800_000_000_000L
    private val timer = SetTimerState(setId = "h2", startedAt = startedAt)

    private fun hold(id: String, seconds: Int? = 30, weight: Double = 0.0, completed: Boolean = false) =
        WorkoutSet(id, 0, weight, completed, durationSeconds = seconds)

    private val workout = Workout(
        id = "w",
        name = "Abdos",
        preset = PresetId.Isolation,
        date = "2026-11-12T17:00:00.000Z",
        bodyWeight = 0.0,
        exercises = listOf(
            WorkoutExercise("crunch", sets = listOf(WorkoutSet("c1", 15, 0.0, true))),
            WorkoutExercise("plank", sets = listOf(hold("h1", 60, completed = true), hold("h2"), hold("h3"))),
        ),
    )

    @Test
    fun `temps écoulé en secondes entières, partie entière`() {
        assertEquals(0, SetTimerRules.elapsedSeconds(timer, startedAt))
        assertEquals(0, SetTimerRules.elapsedSeconds(timer, startedAt + 999))
        assertEquals(1, SetTimerRules.elapsedSeconds(timer, startedAt + 1_000))
        assertEquals(64, SetTimerRules.elapsedSeconds(timer, startedAt + 64_999))
    }

    @Test
    fun `temps écoulé borné à 99 59, jamais négatif`() {
        assertEquals(5999, SetTimerRules.elapsedSeconds(timer, startedAt + 7_200_000))
        // Horloge reculée.
        assertEquals(0, SetTimerRules.elapsedSeconds(timer, startedAt - 5_000))
    }

    @Test
    fun `le chrono ne vaut que sur une série non validée de la séance en cours`() {
        assertSame(timer, SetTimerRules.activeOn(timer, workout))
        assertEquals("plank", SetTimerRules.locate(workout, "h2")!!.exercise.exerciseId)
        // Série validée, supprimée, exercice retiré ou plus de séance : plus de chrono.
        assertNull(SetTimerRules.activeOn(timer.copy(setId = "h1"), workout))
        assertNull(SetTimerRules.activeOn(timer.copy(setId = "absente"), workout))
        assertNull(SetTimerRules.activeOn(timer, workout.copy(exercises = workout.exercises.take(1))))
        assertNull(SetTimerRules.activeOn(timer, null))
        assertNull(SetTimerRules.activeOn(null, workout))
    }

    @Test
    fun `Arrêter reporte la durée et valide la série, le lest ne bouge pas`() {
        val set = hold("h2", seconds = 30, weight = 10.0)
        val stopped = SetTimerRules.stopped(set, timer, startedAt + 72_400, validate = true)
        assertEquals(72, stopped.durationSeconds)
        assertTrue(stopped.completed)
        assertEquals(10.0, stopped.weight, 0.0)
        assertEquals("h2", stopped.id)
    }

    @Test
    fun `ouvrir le pavé arrête le chrono sans valider`() {
        val stopped = SetTimerRules.stopped(hold("h2"), timer, startedAt + 45_000, validate = false)
        assertEquals(45, stopped.durationSeconds)
        assertFalse(stopped.completed)
    }

    @Test
    fun `un seul chrono à la fois, jamais sur une série validée`() {
        assertTrue(SetTimerRules.canStart(null, hold("h3")))
        assertFalse(SetTimerRules.canStart(timer, hold("h3")))
        assertFalse(SetTimerRules.canStart(null, hold("h1", completed = true)))
    }
}
