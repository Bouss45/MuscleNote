package com.musclenote.android.data.repository

import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Superséries de bout en bout (SPEC § 15.20, 🔄 D46) : repos de transition ou repos normal après une
 * validation — case, pavé, notification, chrono —, liens qui suivent les exercices, reprise à la séance
 * suivante, réglage et anciens fichiers relus.
 */
class SupersetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T17:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")

    private suspend fun container() = AppContainer(dataDir, time).also { it.awaitReady() }

    /** Séance Push : développé couché lié aux tractions, puis dips seul, 3 séries chacun. */
    private suspend fun AppContainer.startSuperset() {
        workouts.startWorkout(80.0, PresetId.Push, "Push")
        for (id in listOf("bench", "pullup", "dips")) {
            workouts.addExerciseToWorkout(id)
            repeat(3) { workouts.addSet(id) }
        }
        workouts.linkToNext("bench")
    }

    private fun AppContainer.sets(exerciseId: String): List<WorkoutSet> =
        workouts.currentWorkout.value!!.exercises.single { it.exerciseId == exerciseId }.sets

    private fun AppContainer.set(exerciseId: String, rank: Int): String = sets(exerciseId)[rank - 1].id

    private fun AppContainer.supersetIds(): List<String?> = workouts.currentWorkout.value!!.exercises.map { it.supersetId }

    /** Les séries validées, dans l'ordre où elles l'ont été : (exercice, rang). */
    private fun AppContainer.completed(): Set<Pair<String, Int>> =
        workouts.currentWorkout.value!!.exercises.flatMap { e -> e.sets.mapIndexedNotNull { i, s -> (e.exerciseId to i + 1).takeIf { s.completed } } }.toSet()

    private val defaultRest = Preferences.DEFAULT_REST_SECONDS

    /** Attend qu'une écriture de fond ait rendu [condition] vraie. */
    private fun eventually(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition())
    }

    // ─── Repos après une validation ──────────────────────────────────────────────

    @Test
    fun `valider la série d'un exercice lié qui n'est pas le dernier ne lance pas de repos et arrête le repos en cours`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        val ids = container.supersetIds()
        assertNotNull(ids[0])
        assertEquals(listOf(ids[0], ids[0], null), ids)
        container.restTimer.start("dips")
        assertTrue(container.completeSet("bench", container.set("bench", 1)))
        assertNull(container.restTimer.state.value)
    }

    @Test
    fun `repos de transition réglé - un repos de cette durée au nom de l'exercice suivant`() = runBlocking<Unit> {
        val container = container()
        container.preferences.setSupersetRestSeconds(15)
        container.startSuperset()
        container.completeSet("bench", container.set("bench", 1))
        val rest = container.restTimer.state.value!!
        assertEquals(15, rest.total)
        assertEquals("pullup", rest.exerciseName)
        assertEquals(clock.toEpochMilli() + 15_000, rest.endsAt)
    }

    @Test
    fun `la série du dernier exercice lance le repos normal au nom du premier, hors supersérie aussi`() = runBlocking<Unit> {
        val container = container()
        container.preferences.setSupersetRestSeconds(15)
        container.startSuperset()
        container.completeSet("bench", container.set("bench", 1))
        container.completeSet("pullup", container.set("pullup", 1))
        assertEquals(defaultRest, container.restTimer.state.value!!.total)
        assertEquals("bench", container.restTimer.state.value!!.exerciseName)

        container.completeSet("dips", container.set("dips", 1))
        assertEquals(defaultRest, container.restTimer.state.value!!.total)
        assertEquals("dips", container.restTimer.state.value!!.exerciseName)
    }

    @Test
    fun `la case enchaîne comme le pavé, dévalider ne fait rien`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        container.restTimer.start("dips")
        assertEquals(true, container.toggleSetCompleted("bench", container.set("bench", 1)))
        assertNull(container.restTimer.state.value)
        assertEquals(true, container.toggleSetCompleted("pullup", container.set("pullup", 1)))
        assertEquals(defaultRest, container.restTimer.state.value!!.total)
        assertEquals(false, container.toggleSetCompleted("pullup", container.set("pullup", 1)))
        assertNotNull(container.restTimer.state.value)
    }

    @Test
    fun `la notification valide la série suivante de la supersérie et enchaîne`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        container.completeNextSet()
        assertEquals(setOf("bench" to 1), container.completed())
        assertNull(container.restTimer.state.value)
        container.completeNextSet()
        assertEquals(setOf("bench" to 1, "pullup" to 1), container.completed())
        assertEquals("bench", container.restTimer.state.value!!.exerciseName)
        container.completeNextSet()
        assertTrue("bench" to 2 in container.completed())
        assertNull(container.restTimer.state.value)
    }

    @Test
    fun `un circuit de trois aux nombres de séries différents`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        container.workouts.linkToNext("pullup")
        container.workouts.removeSet("dips", container.set("dips", 3))
        // Repos attendu après chaque validation par la notification : null = aucun repos.
        val expected = listOf(
            "bench" to 1 to null, "pullup" to 1 to null, "dips" to 1 to "bench",
            "bench" to 2 to null, "pullup" to 2 to null, "dips" to 2 to "bench",
            // Les dips n'ont pas de 3e série : le tour finit aux tractions.
            // Plus rien à faire dans la supersérie : le repos garde le nom des tractions.
            "bench" to 3 to null, "pullup" to 3 to "pullup",
        )
        val validated = mutableListOf<Pair<String, Int>>()
        for ((set, restName) in expected) {
            container.completeNextSet()
            val now = container.completed()
            val added = now.single { it !in validated }
            validated += added
            assertEquals(set, added)
            assertEquals(restName, container.restTimer.state.value?.exerciseName)
            container.restTimer.stop()
        }
        assertFalse(container.workouts.hasNextSet())
    }

    @Test
    fun `le chrono d'une série au temps liée enchaîne aussi`() = runBlocking<Unit> {
        val container = container()
        container.workouts.startWorkout(0.0, null, "Abdos")
        container.workouts.addExerciseToWorkout("plank")
        container.workouts.addSet("plank")
        container.workouts.addExerciseToWorkout("crunch")
        container.workouts.addSet("crunch")
        container.workouts.linkToNext("plank")
        container.restTimer.start("crunch")
        assertTrue(container.startSetTimer(container.set("plank", 1)))
        clock = clock.plusSeconds(45)
        container.stopSetTimer(validate = true)
        assertTrue(container.sets("plank")[0].completed)
        assertNull(container.restTimer.state.value)
    }

    @Test
    fun `une série de chauffe validée lance le repos normal, même dans une supersérie`() = runBlocking<Unit> {
        val container = container()
        container.preferences.setSupersetRestSeconds(15)
        container.startSuperset()
        val first = container.set("bench", 1)
        container.workouts.updateSet("bench", first) { it.copy(warmup = true) }
        container.completeSet("bench", first)
        assertEquals(defaultRest, container.restTimer.state.value!!.total)
        assertEquals("bench", container.restTimer.state.value!!.exerciseName)
    }

    // ─── Liens ───────────────────────────────────────────────────────────────────

    @Test
    fun `lier, délier et retirer un exercice lié - les liens suivent les voisins`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        container.workouts.linkToNext("pullup")
        val circuit = container.supersetIds()
        assertNotNull(circuit[0])
        assertEquals(listOf(circuit[0], circuit[0], circuit[0]), circuit)

        container.workouts.removeExerciseFromWorkout("pullup")
        assertEquals(listOf(circuit[0], circuit[0]), container.supersetIds())

        container.workouts.unlink("dips")
        assertEquals(listOf<String?>(null, null), container.supersetIds())

        container.workouts.linkToNext("bench")
        container.workouts.reorderExercises(listOf("dips", "bench"))
        assertEquals(2, container.supersetIds().filterNotNull().size)
    }

    @Test
    fun `la séance suivante du même type reprend les liens, pas l'ajout d'un exercice`() = runBlocking<Unit> {
        val container = container()
        container.startSuperset()
        container.completeSet("bench", container.set("bench", 1))
        container.endWorkout()

        clock = clock.plusSeconds(2 * 86_400)
        container.workouts.startWorkout(80.0, PresetId.Push, "Push")
        val ids = container.supersetIds()
        assertNotNull(ids[0])
        assertEquals(listOf(ids[0], ids[0], null), ids)
        container.endWorkout()

        // Autre type : le développé couché ajouté seul ne reprend pas son lien.
        container.workouts.startWorkout(80.0, PresetId.Legs, "Jambes")
        container.workouts.addExerciseToWorkout("bench")
        assertEquals(listOf<String?>(null), container.supersetIds())
        assertEquals(3, container.sets("bench").size)
    }

    // ─── Réglage et fichiers ─────────────────────────────────────────────────────

    @Test
    fun `le repos de transition va de 0 à 60 s, est enregistré et relu`() = runBlocking<Unit> {
        val container = container()
        assertEquals(0, container.preferences.preferences.value.supersetRestSeconds)
        container.preferences.setSupersetRestSeconds(61)
        container.preferences.setSupersetRestSeconds(-1)
        assertEquals(0, container.preferences.preferences.value.supersetRestSeconds)
        container.preferences.setSupersetRestSeconds(45)
        assertEquals(45, container.preferences.preferences.value.supersetRestSeconds)
        val file = File(dataDir, "preferences.json")
        // L'écriture se fait en arrière-plan : le fichier peut ne pas encore exister.
        eventually { file.exists() && file.readText().contains(""""supersetRestSeconds":45""") }
        assertEquals(45, container().preferences.preferences.value.supersetRestSeconds)
    }

    @Test
    fun `d'anciens fichiers et des valeurs invalides se relisent sans lien ni chauffe, sans rien mettre de côté`() = runBlocking<Unit> {
        dataDir.mkdirs()
        File(dataDir, "current_workout.json").writeText(
            """{"id":"w","name":"Push","preset":"push","date":"2026-11-12T16:30:00.000Z","bodyWeight":80,"exercises":[""" +
                """{"exerciseId":"bench","supersetId":"s1","sets":[{"id":"b1","reps":8,"weight":80,"completed":false,"warmup":"oui"}]},""" +
                """{"exerciseId":"pullup","supersetId":"s1","sets":[{"id":"p1","reps":8,"weight":0,"completed":false,"warmup":true}]},""" +
                """{"exerciseId":"dips","supersetId":12,"sets":[]},""" +
                """{"exerciseId":"squat","supersetId":"","sets":[{"id":"q1","reps":5,"weight":100,"completed":false}]}]}""",
        )
        File(dataDir, "preferences.json").writeText("""{"restSeconds":120,"theme":"dark","supersetRestSeconds":"x"}""")
        val container = container()
        assertEquals(listOf("s1", "s1", null, null), container.supersetIds())
        assertEquals(listOf(false), container.sets("bench").map { it.warmup })
        assertEquals(listOf(true), container.sets("pullup").map { it.warmup })
        assertEquals(0, container.preferences.preferences.value.supersetRestSeconds)
        assertEquals(120, container.preferences.preferences.value.restSeconds)
        assertTrue(dataDir.listFiles()!!.none { it.name.contains("corrupt") })

        File(dataDir, "preferences.json").writeText("""{"restSeconds":120,"theme":"dark","supersetRestSeconds":90}""")
        assertEquals(0, container().preferences.preferences.value.supersetRestSeconds)
    }

    @Test
    fun `un exercice lié resté seul dans un ancien fichier perd son lien au chargement`() = runBlocking<Unit> {
        dataDir.mkdirs()
        File(dataDir, "current_workout.json").writeText(
            """{"id":"w","name":"Push","date":"2026-11-12T16:30:00.000Z","bodyWeight":80,"exercises":[""" +
                """{"exerciseId":"bench","supersetId":"s1","sets":[]},{"exerciseId":"dips","sets":[]},{"exerciseId":"pullup","supersetId":"s1","sets":[]}]}""",
        )
        assertEquals(listOf<String?>(null, null, null), container().supersetIds())
    }
}
