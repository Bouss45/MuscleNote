package com.musclenote.android.data.repository

import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.workout.WorkoutEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Dépôt du minuteur de repos (§ 7.2) : un seul repos, persisté, alarme de fin seule source de
 * l'événement « terminé ». Les effets système sont enregistrés par un faux.
 */
class RestTimerRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-03-14T17:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }
    private val nowMillis get() = clock.toEpochMilli()

    private val dataDir get() = File(tmp.root, "data")
    private val timerFile get() = File(dataDir, "rest_timer.json")
    private var restSeconds = 90

    private class RecordingEffects : RestTimerEffects {
        val calls = mutableListOf<String>()
        override fun show(state: RestTimerState) {
            calls += "show:${state.exerciseName}:${state.endsAt}"
        }
        override fun cancel() {
            calls += "cancel"
        }
        override fun finished(state: RestTimerState, inForeground: Boolean) {
            calls += "finished:${state.exerciseName}:${if (inForeground) "foreground" else "background"}"
        }
    }

    private val effects = RecordingEffects()

    private suspend fun CoroutineScope.repository(): RestTimerRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return RestTimerRepository(files.restTimer, time, { restSeconds }, effects).also { it.load() }
    }

    @Test
    fun `démarrer un repos l'expose, l'enregistre et lance notification et alarme`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.start("Squat")
            val expected = RestTimerState(endsAt = nowMillis + 90_000, total = 90, exerciseName = "Squat")
            assertEquals(expected, repo.state.value)
            assertEquals(listOf("show:Squat:${expected.endsAt}"), effects.calls)
        }
        assertTrue(timerFile.exists())
    }

    @Test
    fun `la durée est celle de la préférence au moment du démarrage`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            restSeconds = 60
            repo.start("Dips")
            assertEquals(60, repo.state.value?.total)
        }
    }

    @Test
    fun `un nouveau repos remplace le précédent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.start("Squat")
            clock = clock.plusSeconds(30)
            repo.start("Presse")
            assertEquals("Presse", repo.state.value?.exerciseName)
            assertEquals(nowMillis + 90_000, repo.state.value?.endsAt)
        }
    }

    @Test
    fun `un repos en cours survit à la mort du processus et retrouve notification et alarme`() = runBlocking<Unit> {
        val started = coroutineScope {
            val repo = repository()
            repo.start("Squat")
            repo.state.value
        }
        effects.calls.clear()
        clock = clock.plusSeconds(20)
        coroutineScope {
            val repo = repository()
            assertEquals(started, repo.state.value)
            assertEquals(listOf("show:Squat:${started?.endsAt}"), effects.calls)
        }
    }

    @Test
    fun `passer le repos efface tout`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.start("Squat")
            repo.stop()
            assertNull(repo.state.value)
            assertEquals("cancel", effects.calls.last())
        }
        assertFalse(timerFile.exists())
        coroutineScope { assertNull(repository().state.value) }
    }

    @Test
    fun `l'alarme termine le repos qu'elle concerne, avec vibration ou notification`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.start("Squat")
            val endsAt = repo.state.value!!.endsAt
            clock = clock.plusSeconds(90)
            assertTrue(repo.finish(endsAt, inForeground = false))
            assertNull(repo.state.value)
            assertEquals("finished:Squat:background", effects.calls.last())
            // Une seconde livraison de la même alarme ne fait rien.
            assertFalse(repo.finish(endsAt, inForeground = false))
        }
        assertFalse(timerFile.exists())
    }

    @Test
    fun `une alarme dépassée par un nouveau repos ou un arrêt est ignorée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.start("Squat")
            val oldEndsAt = repo.state.value!!.endsAt
            clock = clock.plusSeconds(10)
            repo.start("Presse")
            assertFalse(repo.finish(oldEndsAt, inForeground = true))
            assertEquals("Presse", repo.state.value?.exerciseName)

            repo.stop()
            assertFalse(repo.finish(oldEndsAt, inForeground = true))
            assertTrue(effects.calls.none { it.startsWith("finished") })
        }
    }

    @Test
    fun `un repos fini mais récent attend l'alarme qui a relancé le processus`() = runBlocking<Unit> {
        val endsAt = coroutineScope {
            val repo = repository()
            repo.start("Squat")
            repo.state.value!!.endsAt
        }
        effects.calls.clear()
        // Processus tué pendant le repos, relancé par l'alarme de fin.
        clock = clock.plusSeconds(95)
        coroutineScope {
            val repo = repository()
            // Fini : ni notification en cours ni alarme reprogrammées au chargement…
            assertTrue(effects.calls.isEmpty())
            // … mais l'alarme peut encore le terminer et prévenir.
            assertTrue(repo.finish(endsAt, inForeground = false))
            assertEquals(listOf("finished:Squat:background"), effects.calls)
        }
    }

    @Test
    fun `un état périmé est effacé au chargement sans alerte`() = runBlocking<Unit> {
        coroutineScope { repository().start("Squat") }
        effects.calls.clear()
        clock = clock.plusSeconds(2 * 3_600)
        coroutineScope {
            val repo = repository()
            assertNull(repo.state.value)
            assertTrue(effects.calls.isEmpty())
        }
        assertFalse(timerFile.exists())
    }

    @Test
    fun `terminer ou abandonner la séance arrête le repos`() = runBlocking<Unit> {
        val container = AppContainer(dataDir, time, restTimerEffects = effects).also { it.awaitReady() }
        container.workouts.startWorkout(80.0, null, "Jambes")
        container.restTimer.start("Squat")
        assertEquals(WorkoutEnd.Abandoned, container.endWorkout())
        assertNull(container.restTimer.state.value)
        assertEquals("cancel", effects.calls.last())
    }

    @Test
    fun `une séance de plus de 24 h finalisée au retour au premier plan arrête le repos`() = runBlocking<Unit> {
        val container = AppContainer(dataDir, time, restTimerEffects = effects).also { it.awaitReady() }
        container.workouts.startWorkout(80.0, null, "Jambes")
        clock = clock.plusSeconds(25 * 3_600)
        container.restTimer.start("Squat")
        container.onAppForeground()
        // onAppForeground lance son contrôle en tâche de fond : on attend qu'il ait arrêté le repos.
        repeat(200) {
            if (container.restTimer.state.value == null) return@runBlocking
            Thread.sleep(10)
        }
        assertNull(container.restTimer.state.value)
    }

    @Test
    fun `la durée choisie dans Paramètres est enregistrée et sert aux repos suivants`() = runBlocking<Unit> {
        val container = AppContainer(dataDir, time, restTimerEffects = effects).also { it.awaitReady() }
        container.preferences.setRestSeconds(60)
        container.preferences.setRestSeconds(0) // ignorée : > 0 seulement
        assertEquals(60, container.preferences.preferences.value.restSeconds)
        container.restTimer.start("Dips")
        assertEquals(60, container.restTimer.state.value?.total)

        // Écriture des préférences en tâche de fond : on attend qu'elle soit sur disque.
        val file = File(dataDir, "preferences.json")
        repeat(200) {
            if (file.exists() && file.readText().contains("\"restSeconds\":60")) return@runBlocking
            Thread.sleep(10)
        }
        assertTrue(file.readText().contains("\"restSeconds\":60"))
    }
}
