package com.musclenote.android.data.repository

import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.workout.CustomExerciseDeletion
import com.musclenote.android.data.workout.WorkoutEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
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
 * Mutations du dépôt des séances et leur persistance (§ 3.1 – 3.4, § 3.9, § 4.2). Chaque bloc
 * `coroutineScope { … }` attend les écritures qu'il a lancées ; un nouveau dépôt qui relit le
 * disque joue le rôle d'un redémarrage de l'app.
 */
class WorkoutRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-03-14T17:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")
    private val currentFile get() = File(dataDir, "current_workout.json")
    private var nextId = 0

    private suspend fun CoroutineScope.repository(): WorkoutRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return WorkoutRepository(files.workouts, files.exercises, files.currentWorkout, time) { "id-${++nextId}" }
            .also { it.load() }
    }

    private fun writeHistory(vararg workouts: Workout) {
        dataDir.mkdirs()
        File(dataDir, "workouts.json").writeText(AppJson.storage.encodeToString(ListSerializer(Workout.serializer()), workouts.toList()))
    }

    private fun pastPush() = Workout(
        id = "past",
        name = "Push",
        preset = PresetId.Push,
        date = "2026-03-12T17:00:00.000Z",
        bodyWeight = 79.0,
        exercises = listOf(WorkoutExercise("bench", "Prise serrée", listOf(WorkoutSet("s1", 8, 80.0, true)))),
        durationMinutes = 60,
    )

    private fun WorkoutRepository.sets(exerciseId: String): List<WorkoutSet> =
        currentWorkout.value!!.exercises.first { it.exerciseId == exerciseId }.sets

    /** Séance en cours avec un soulevé de terre (absent de l'historique) et une série, validée ou non. */
    private suspend fun WorkoutRepository.startWithOneSet(completed: Boolean) {
        startWorkout(80.0, null, "Haut du corps")
        addExerciseToWorkout(LIFT)
        addSet(LIFT)
        if (completed) toggleSetCompleted(LIFT, sets(LIFT).single().id)
    }

    private companion object {
        const val LIFT = "deadlift"
    }

    // ─── § 3.1 : démarrer ────────────────────────────────────────────────────────

    @Test
    fun `une séance démarrée est pré-remplie, exposée et enregistrée`() = runBlocking<Unit> {
        writeHistory(pastPush())
        val started = coroutineScope {
            val repo = repository()
            val started = repo.startWorkout(78.5, PresetId.Push, "Push")
            assertEquals(started, repo.currentWorkout.value)
            assertEquals("Prise serrée", started.exercises.single().note)
            assertEquals(listOf(WorkoutSet("id-2", 8, 80.0, false)), started.exercises.single().sets)
            started
        }
        // Écriture différée faite ; relue par un « nouveau processus ».
        assertTrue(currentFile.exists())
        coroutineScope { assertEquals(started, repository().currentWorkout.value) }
    }

    @Test
    fun `l'écriture de la séance en cours peut être forcée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWorkout(80.0, null, "Bras")
            repo.flushPendingWrites()
            assertTrue(currentFile.readText().contains("\"name\":\"Bras\""))
        }
    }

    // ─── § 3.3 : terminer ou abandonner ─────────────────────────────────────────

    @Test
    fun `terminer une séance l'ajoute en tête de l'historique avec sa durée et toutes ses séries`() = runBlocking<Unit> {
        writeHistory(pastPush())
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = true)
            repo.addSet(LIFT) // seconde série, non validée
            clock = clock.plusSeconds(65 * 60)

            val end = repo.endWorkout()

            assertTrue(end is WorkoutEnd.Finished)
            assertNull(repo.currentWorkout.value)
            assertEquals(listOf("id-1", "past"), repo.workouts.value.map { it.id })
            val finished = repo.workouts.value.first()
            assertEquals(65, finished.durationMinutes)
            assertEquals(listOf(true, false), finished.exercises.single().sets.map { it.completed })
        }
        assertFalse(currentFile.exists())
        coroutineScope {
            val repo = repository()
            assertNull(repo.currentWorkout.value)
            assertEquals(listOf("id-1", "past"), repo.workouts.value.map { it.id })
        }
    }

    @Test
    fun `terminer une séance sans série validée l'abandonne sans historique`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = false)
            assertEquals(WorkoutEnd.Abandoned, repo.endWorkout())
            assertNull(repo.currentWorkout.value)
            assertTrue(repo.workouts.value.isEmpty())
            assertNull(repo.endWorkout())
        }
        assertFalse(currentFile.exists())
        assertFalse(File(dataDir, "workouts.json").exists())
    }

    // ─── § 3.4 : finalisation au retour au premier plan ───────────────────────────

    @Test
    fun `au retour au premier plan une séance de plus de 24 h est finalisée sans durée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = true)
            clock = clock.plusSeconds(23 * 3_600)
            assertNull(repo.finalizeStaleSession())
            assertNotNull(repo.currentWorkout.value)

            clock = clock.plusSeconds(2 * 3_600)
            val end = repo.finalizeStaleSession()

            assertTrue(end is WorkoutEnd.Finished)
            assertNull(repo.currentWorkout.value)
            assertNull(repo.workouts.value.single().durationMinutes)
        }
        assertFalse(currentFile.exists())
    }

    @Test
    fun `au retour au premier plan une séance de plus de 24 h sans série validée est abandonnée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = false)
            clock = clock.plusSeconds(25 * 3_600)
            assertEquals(WorkoutEnd.Abandoned, repo.finalizeStaleSession())
            assertNull(repo.currentWorkout.value)
            assertTrue(repo.workouts.value.isEmpty())
        }
    }

    // ─── Séances terminées : date, suppression, édition ───────────────────────────────

    private fun pastLegs() = pastPush().copy(id = "legs", name = "Legs", preset = PresetId.Legs, date = "2026-03-10T17:00:00.000Z")

    @Test
    fun `changer la date d'une séance retrie l'historique et l'enregistre`() = runBlocking<Unit> {
        writeHistory(pastLegs(), pastPush()) // 10 puis 12 mars : ordre volontairement faux
        coroutineScope {
            val repo = repository()
            assertEquals(listOf("past", "legs"), repo.workouts.value.map { it.id })
            repo.updateWorkoutDate("past", Instant.parse("2026-03-01T09:30:00Z"))
            assertEquals(listOf("legs", "past"), repo.workouts.value.map { it.id })
            assertEquals("2026-03-01T09:30:00.000Z", repo.workouts.value.last().date)
        }
        coroutineScope { assertEquals(listOf("legs", "past"), repository().workouts.value.map { it.id }) }
    }

    @Test
    fun `supprimer une séance terminée l'enlève de l'historique`() = runBlocking<Unit> {
        writeHistory(pastPush(), pastLegs())
        coroutineScope {
            val repo = repository()
            repo.deleteWorkout("legs")
            repo.deleteWorkout("inconnue")
            assertEquals(listOf("past"), repo.workouts.value.map { it.id })
        }
        coroutineScope { assertEquals(listOf("past"), repository().workouts.value.map { it.id }) }
    }

    @Test
    fun `l'éditeur remplace exercices, nom et préréglage, et garde l'ancien poids si le nouveau est nul`() = runBlocking<Unit> {
        writeHistory(pastPush())
        val exercises = listOf(WorkoutExercise("ohp", sets = listOf(WorkoutSet("e1", 5, 50.0, true))))
        coroutineScope {
            val repo = repository()
            repo.patchWorkout("past", exercises, bodyWeight = 0.0, name = " Épaules ", preset = null)
            val patched = repo.workouts.value.single()
            assertEquals(exercises, patched.exercises)
            assertEquals(79.0, patched.bodyWeight, 0.0)
            assertEquals("Épaules", patched.name)
            assertNull(patched.preset)
            // Date et durée inchangées.
            assertEquals(pastPush().date, patched.date)
            assertEquals(60, patched.durationMinutes)

            repo.patchWorkout("past", exercises, bodyWeight = 81.5, name = "Pull", preset = PresetId.Pull)
            assertEquals(81.5, repo.workouts.value.single().bodyWeight, 0.0)
            assertEquals(PresetId.Pull, repo.workouts.value.single().preset)
            repo.patchWorkout("inconnue", emptyList(), 70.0, "X", null)
        }
        coroutineScope { assertEquals(PresetId.Pull, repository().workouts.value.single().preset) }
    }

    // ─── Séance en cours ─────────────────────────────────────────────────────────

    @Test
    fun `une série ajoutée suit le groupe de l'exercice et la dernière série`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val rower = repo.addCustomExercise("Rameur", MuscleGroup.Cardio)!!
            repo.startWorkout(80.0, null, "Mix")
            repo.addExerciseToWorkout(rower.id)
            repo.addSet(rower.id)
            val cardio = repo.sets(rower.id).single()
            assertEquals(WorkoutSet(cardio.id, 0, 0.0, false, duration = 30.0, distance = 0.0, speed = 0.0, incline = 0.0), cardio)

            repo.addExerciseToWorkout("bench")
            repo.addSet("bench")
            val first = repo.sets("bench").single()
            assertEquals(WorkoutSet(first.id, 10, 20.0, false), first)
            repo.updateSet("bench", first.id) { it.copy(weight = 62.5) }
            repo.addSet("bench")
            assertEquals(listOf(62.5, 62.5), repo.sets("bench").map { it.weight })

            repo.removeSet("bench", first.id)
            assertEquals(1, repo.sets("bench").size)
            repo.removeExerciseFromWorkout(rower.id)
            assertEquals(listOf("bench"), repo.currentWorkout.value!!.exercises.map { it.exerciseId })
        }
    }

    @Test
    fun `valider une série bascule son état`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = false)
            val id = repo.sets(LIFT).single().id
            assertEquals(true, repo.toggleSetCompleted(LIFT, id))
            assertEquals(false, repo.toggleSetCompleted(LIFT, id))
            assertNull(repo.toggleSetCompleted(LIFT, "inconnue"))
        }
    }

    @Test
    fun `la note est enregistrée et une note vidée est retirée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWithOneSet(completed = false)
            repo.updateExerciseNote(LIFT, "Dos plat")
            assertEquals("Dos plat", repo.currentWorkout.value!!.exercises.single().note)
            repo.updateExerciseNote(LIFT, "")
            assertNull(repo.currentWorkout.value!!.exercises.single().note)
        }
    }

    @Test
    fun `réordonner change l'ordre des exercices de la séance`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.startWorkout(80.0, null, "Pecs")
            listOf("bench", "fly", "dips").forEach { repo.addExerciseToWorkout(it) }
            repo.addExerciseToWorkout("bench") // déjà présent : rien
            repo.reorderExercises(listOf("dips", "bench", "fly"))
            assertEquals(listOf("dips", "bench", "fly"), repo.currentWorkout.value!!.exercises.map { it.exerciseId })
        }
    }

    @Test
    fun `les mutations lancées depuis l'UI s'appliquent dans l'ordre des appels`() = runBlocking<Unit> {
        val container = AppContainer(dataDir, time).also { it.awaitReady() }
        val repo = container.workouts
        repo.startWithOneSet(completed = false)
        val setId = repo.sets(LIFT).single().id
        // Un rechargement garde le verrou du dépôt pendant ses lectures : les frappes suivantes
        // attendent leur tour.
        val reload = container.launchMutation { repo.load() }
        val keystrokes = (1..30).map { reps ->
            container.launchMutation { repo.updateSet(LIFT, setId) { it.copy(reps = reps) } }
        }
        (keystrokes + reload).joinAll()
        assertEquals(30, repo.sets(LIFT).single().reps)
    }

    // ─── § 3.9 : exercices perso ───────────────────────────────────────────────────

    @Test
    fun `un exercice perso utilisé dans l'historique est archivé, pas supprimé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val hipThrust = repo.addCustomExercise("  Hip thrust ", MuscleGroup.Jambes)!!
            assertEquals("Hip thrust", hipThrust.name)
            repo.startWorkout(80.0, null, "Jambes")
            repo.addExerciseToWorkout(hipThrust.id) // référencé même sans série
            repo.addExerciseToWorkout("squat")
            repo.addSet("squat")
            repo.toggleSetCompleted("squat", repo.sets("squat").single().id)
            repo.endWorkout()

            assertEquals(CustomExerciseDeletion.Archived, repo.deleteCustomExercise(hipThrust.id))
            assertTrue(repo.customExercises.value.single().archived)
        }
        coroutineScope { assertTrue(repository().customExercises.value.single().archived) }
    }

    @Test
    fun `un exercice perso seulement dans la séance en cours est archivé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val shrug = repo.addCustomExercise("Shrug", MuscleGroup.Dos)!!
            repo.startWorkout(80.0, null, "Dos")
            repo.addExerciseToWorkout(shrug.id)
            assertEquals(CustomExerciseDeletion.Archived, repo.deleteCustomExercise(shrug.id))
            // L'archivage n'est jamais défait par une modification.
            assertTrue(repo.updateCustomExercise(shrug.id, "Shrug haltères", MuscleGroup.Epaules))
            assertEquals(listOf(true), repo.customExercises.value.map { it.archived })
            assertEquals("Shrug haltères", repo.customExercises.value.single().name)
        }
    }

    @Test
    fun `un exercice perso jamais utilisé est supprimé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val a = repo.addCustomExercise("A", MuscleGroup.Biceps)!!
            val b = repo.addCustomExercise("B", MuscleGroup.Triceps)!!
            assertEquals(CustomExerciseDeletion.Deleted, repo.deleteCustomExercise(a.id))
            assertEquals(listOf(b), repo.customExercises.value)
            assertNull(repo.deleteCustomExercise(a.id))
        }
        coroutineScope { assertEquals(listOf("B"), repository().customExercises.value.map { it.name }) }
    }

    @Test
    fun `les exercices intégrés ne sont ni modifiables ni supprimables, un nom vide ne fait rien`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertNull(repo.deleteCustomExercise("bench"))
            assertFalse(repo.updateCustomExercise("bench", "Couché", MuscleGroup.Pecs))
            assertNull(repo.addCustomExercise("   ", MuscleGroup.Dos))
            val curl = repo.addCustomExercise("Curl pupitre", MuscleGroup.Biceps)!!
            assertFalse(repo.updateCustomExercise(curl.id, " ", MuscleGroup.Dos))
            assertEquals(listOf(curl), repo.customExercises.value)
        }
    }
}
