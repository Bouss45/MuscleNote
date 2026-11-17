package com.musclenote.android.data.repository

import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.TimeSource
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Chronomètre d'une série au temps (SPEC § 15.18.4, 🔄 D44) : départ, arrêt avec ou sans validation,
 * repos, un seul à la fois, instant de départ gardé sur disque, annulé quand sa série disparaît.
 */
class SetTimerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T17:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")
    private val timerFile get() = File(dataDir, "set_timer.json")

    private suspend fun container() = AppContainer(dataDir, time).also { it.awaitReady() }

    /** Séance en cours avec deux séries de planche ; renvoie leurs ids. */
    private suspend fun AppContainer.startPlanks(): List<String> {
        workouts.startWorkout(0.0, null, "Abdos")
        workouts.addExerciseToWorkout("plank")
        workouts.addSet("plank")
        workouts.addSet("plank")
        return planks().map { it.id }
    }

    private fun AppContainer.planks(): List<WorkoutSet> =
        workouts.currentWorkout.value!!.exercises.single { it.exerciseId == "plank" }.sets

    /** Attend qu'une tâche de fond ait rendu [condition] vraie. */
    private fun eventually(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition())
    }

    @Test
    fun `Démarrer arrête le repos, Arrêter reporte les secondes, valide la série et lance le repos`() = runBlocking<Unit> {
        val container = container()
        val (first, _) = container.startPlanks()
        container.restTimer.start("Crunch")
        assertTrue(container.startSetTimer(first))
        assertNull(container.restTimer.state.value)
        assertEquals(SetTimerState(first, clock.toEpochMilli()), container.setTimer.state.value)

        clock = clock.plusMillis(64_700)
        container.stopSetTimer(validate = true)
        assertEquals(WorkoutSet(first, 0, 0.0, true, durationSeconds = 64), container.planks()[0])
        assertNull(container.setTimer.state.value)
        assertEquals("plank", container.restTimer.state.value?.exerciseName)
    }

    @Test
    fun `ouvrir le pavé arrête le chrono sans valider ni lancer de repos`() = runBlocking<Unit> {
        val container = container()
        val (first, _) = container.startPlanks()
        container.startSetTimer(first)
        clock = clock.plusSeconds(41)
        container.stopSetTimer(validate = false)
        assertEquals(WorkoutSet(first, 0, 0.0, false, durationSeconds = 41), container.planks()[0])
        assertNull(container.setTimer.state.value)
        assertNull(container.restTimer.state.value)
    }

    @Test
    fun `un seul chrono à la fois, jamais sur une série validée`() = runBlocking<Unit> {
        val container = container()
        val (first, second) = container.startPlanks()
        assertTrue(container.startSetTimer(first))
        assertFalse(container.startSetTimer(second))
        assertEquals(first, container.setTimer.state.value?.setId)
        container.stopSetTimer(validate = true)
        assertFalse(container.startSetTimer(first))
        assertFalse(container.startSetTimer("absente"))
        assertTrue(container.startSetTimer(second))
    }

    @Test
    fun `l'instant de départ est gardé sur disque, le chrono continue après un redémarrage`() = runBlocking<Unit> {
        val before = container()
        val first = before.startPlanks().first()
        before.startSetTimer(first)
        before.workouts.flushPendingWrites()
        eventually { timerFile.exists() }
        clock = clock.plusSeconds(90)
        // Nouveau processus : la séance en cours et le chrono sont relus.
        val restarted = container()
        assertEquals(first, restarted.setTimer.state.value?.setId)
        restarted.stopSetTimer(validate = true)
        assertEquals(90, restarted.planks()[0].durationSeconds)
        eventually { !timerFile.exists() }
    }

    @Test
    fun `supprimer la série ou terminer la séance arrête le chrono sans rien reporter`() = runBlocking<Unit> {
        val container = container()
        val (first, second) = container.startPlanks()
        container.startSetTimer(first)
        container.workouts.removeSet("plank", first)
        eventually { container.setTimer.state.value == null }

        container.startSetTimer(second)
        clock = clock.plusSeconds(30)
        container.endWorkout()
        assertNull(container.workouts.currentWorkout.value)
        eventually { container.setTimer.state.value == null }
        // La séance abandonnée (aucune série validée) n'a rien reçu.
        assertTrue(container.workouts.workouts.value.isEmpty())
    }

    @Test
    fun `un chrono dont la série n'est plus dans la séance est ignoré au chargement`() = runBlocking<Unit> {
        dataDir.mkdirs()
        timerFile.writeText("""{"setId":"perdue","startedAt":${clock.toEpochMilli()}}""")
        val container = container()
        assertNull(container.setTimer.state.value)
        eventually { !timerFile.exists() }
    }
}
