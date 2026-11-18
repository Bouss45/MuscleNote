package com.musclenote.android.data.workout

import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.model.counted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Séries de chauffe (SPEC § 15.21, 🔄 D47) : hors des statistiques, numérotées à part, « Préc. » et
 * suggestion par rangs séparés, marquage repris au pré-remplissage ; la fin de séance ne change pas.
 */
class WarmupSetsTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val definitions = exerciseDefinitions(emptyList())

    private fun warm(id: String, reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet(id, reps, weight, completed, warmup = true)

    private fun work(id: String, reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet(id, reps, weight, completed)

    private fun workout(id: String, day: String, vararg exercises: WorkoutExercise) =
        Workout(id, "Push", PresetId.Push, "${day}T17:00:00.000Z", 80.0, exercises.toList(), 60)

    private fun bench(vararg sets: WorkoutSet) = WorkoutExercise("bench", sets = sets.toList())

    /** Une chauffe lourde validée (10 × 100), puis 8 × 80 validée et 8 × 80 à faire. */
    private val today = workout("today", "2026-11-12", bench(warm("w1", 10, 100.0), work("s1", 8, 80.0), work("s2", 8, 80.0, completed = false)))

    // ─── Statistiques ────────────────────────────────────────────────────────────

    @Test
    fun `une série comptée est validée et pas de chauffe`() {
        assertTrue(work("a", 8, 80.0).counted)
        assertFalse(warm("b", 8, 80.0).counted)
        assertFalse(work("c", 8, 80.0, completed = false).counted)
    }

    @Test
    fun `une chauffe validée ne compte ni dans le volume, ni dans le 1RM, ni dans les séries`() {
        val sets = today.exercises.single().sets
        assertEquals(640.0, WorkoutStats.volume(sets), 0.0)
        assertEquals(WorkoutStats.estimateOneRepMax(80.0, 8), WorkoutStats.bestOneRepMax(sets), 0.0)
        assertEquals(1, WorkoutStats.completedSetCount(today))
        assertEquals(0, WorkoutStats.exercisesWithCompletedSets(workout("w", "2026-11-12", bench(warm("w1", 10, 40.0)))))
        val plank = WorkoutExercise("plank", sets = listOf(WorkoutSet("p0", 0, 0.0, true, durationSeconds = 30, warmup = true), WorkoutSet("p1", 0, 0.0, true, durationSeconds = 60)))
        assertEquals(60, WorkoutStats.timeUnderTension(workout("p", "2026-11-12", plank)))
    }

    @Test
    fun `l'historique d'un exercice ignore les chauffes, une séance qui n'a que de la chauffe en est absente`() {
        val warmOnly = workout("warm", "2026-11-10", bench(warm("w0", 10, 40.0)))
        val history = WorkoutStats.exerciseHistory(listOf(today, warmOnly), "bench", zone)
        assertEquals(listOf("today"), history.map { it.workoutId })
        assertEquals(listOf("s1"), history.single().sets.map { it.id })
        assertEquals(80.0, history.single().maxWeight, 0.0)
    }

    @Test
    fun `le résumé et le Journal ne comptent pas la chauffe, ni pour les records`() {
        val older = workout("older", "2026-11-05", bench(work("o1", 8, 90.0)))
        val summary = WorkoutSummaryRules.summary(listOf(today, older), "today", definitions)!!
        assertEquals(1, summary.completedSets)
        assertEquals(640L, summary.volumeKg)
        val exercise = summary.exercises.single()
        assertEquals(listOf("s1"), exercise.sets.map { it.id })
        assertEquals("s1", exercise.bestSet!!.id)
        // La chauffe à 100 kg dépasse le record de 90 kg, mais ne compte pas.
        assertTrue(exercise.records.isEmpty())
        assertEquals(1, JournalRules.summary(today, zone).completedSets)
        assertEquals(640L, JournalRules.summary(today, zone).volumeKg)
    }

    @Test
    fun `une séance dont seule une chauffe est validée est enregistrée`() {
        val current = workout("w", "2026-11-12", bench(warm("w1", 10, 40.0), work("s1", 8, 80.0, completed = false))).copy(durationMinutes = null)
        assertTrue(WorkoutRules.end(current, Instant.parse("2026-11-12T18:00:00Z"), zone) is WorkoutEnd.Finished)
    }

    // ─── Numéros ─────────────────────────────────────────────────────────────────

    @Test
    fun `les séries de travail sont numérotées sans les chauffes`() {
        val sets = listOf(warm("w1", 10, 40.0), warm("w2", 6, 60.0), work("s1", 8, 80.0), work("s2", 8, 80.0))
        assertEquals(
            listOf(SetNumber(true, 1), SetNumber(true, 2), SetNumber(false, 1), SetNumber(false, 2)),
            SetNumbering.of(sets),
        )
        assertEquals(SetNumber(false, 2), SetNumbering.of(sets, "s2"))
        assertNull(SetNumbering.of(sets, "absente"))
        // Marquer ne déplace rien : une chauffe au milieu garde sa place.
        assertEquals(
            listOf(SetNumber(false, 1), SetNumber(true, 1), SetNumber(false, 2)),
            SetNumbering.of(listOf(work("a", 8, 80.0), warm("b", 8, 40.0), work("c", 8, 80.0))),
        )
    }

    // ─── « Préc. » et suggestion ─────────────────────────────────────────────────

    @Test
    fun `Préc par rangs séparés, la séance de référence a une série comptée`() {
        val reference = workout("ref", "2026-11-05", bench(warm("rw", 10, 40.0), work("r1", 8, 80.0), work("r2", 7, 80.0)))
        val warmOnly = workout("warm", "2026-11-08", bench(warm("x", 12, 30.0)))
        val found = SessionInsights.reference(listOf(warmOnly, reference), "bench")!!
        assertEquals("ref", found.workout.id)
        assertEquals("r1", found.previous(SetNumber(false, 1))!!.id)
        assertEquals("r2", found.previous(SetNumber(false, 2))!!.id)
        assertEquals("rw", found.previous(SetNumber(true, 1))!!.id)
        assertNull(found.previous(SetNumber(true, 2)))
        // L'ancien accès par rang vise les séries de travail.
        assertEquals("r1", found.previous(0)!!.id)
    }

    @Test
    fun `la suggestion ne regarde et ne change que les séries de travail`() {
        // Chauffe à 5 reps et chauffe non validée : les séries de travail ont tenu leurs 8 reps.
        val reference = SessionInsights.reference(
            listOf(workout("ref", "2026-11-05", bench(warm("rw", 5, 40.0), warm("rx", 5, 60.0, completed = false), work("r1", 8, 80.0), work("r2", 8, 80.0)))),
            "bench",
        )!!
        val suggestion = SessionInsights.suggestion(reference, SetKind.Strength)!!
        assertTrue(suggestion.increase)
        val current = listOf(warm("cw", 10, 40.0, completed = false), work("c1", 8, 80.0, completed = false), work("c2", 8, 80.0, completed = false))
        val applied = SessionInsights.applySuggestion(current, reference, suggestion)
        assertEquals(listOf(40.0, 82.5, 82.5), applied.map { it.weight })
    }

    @Test
    fun `une référence sans série de travail validée ne donne aucune suggestion`() {
        assertNull(SessionInsights.reference(listOf(workout("warm", "2026-11-08", bench(warm("x", 12, 30.0)))), "bench"))
    }

    // ─── Reprise ─────────────────────────────────────────────────────────────────

    @Test
    fun `le pré-remplissage reprend le marquage des chauffes`() {
        var n = 0
        val started = WorkoutRules.newWorkout(listOf(today), 80.0, PresetId.Push, "Push", Instant.parse("2026-11-14T17:00:00Z")) { "n${++n}" }
        assertEquals(listOf(true, false, false), started.exercises.single().sets.map { it.warmup })
        assertTrue(started.exercises.single().sets.none { it.completed })
    }

    @Test
    fun `une série ajoutée est une série de travail`() {
        assertFalse(WorkoutRules.newSet(warm("w1", 10, 40.0), SetKind.Strength, "new").warmup)
    }
}
