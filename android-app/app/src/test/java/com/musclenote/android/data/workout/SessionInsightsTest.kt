package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colonne « Préc. », suggestion de charge et place du repos dans la séance en cours (§ 15.7). */
class SessionInsightsTest {

    private var nextId = 0

    private fun set(reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet("s${++nextId}", reps, weight, completed)

    private fun cardio(duration: Double, distance: Double? = null, speed: Double? = null, incline: Double? = null, completed: Boolean = true) =
        WorkoutSet("c${++nextId}", 0, 0.0, completed, duration, distance, speed, incline)

    private fun exercise(id: String, vararg sets: WorkoutSet) = WorkoutExercise(id, sets = sets.toList())

    private fun workout(day: String, vararg exercises: WorkoutExercise, preset: PresetId? = PresetId.Push) =
        Workout("w${++nextId}", "", preset, "${day}T17:00:00.000Z", 80.0, exercises.toList(), 60)

    /** Historique du dépôt : du plus récent au plus ancien. */
    private fun history(vararg workouts: Workout) = workouts.sortedByDescending { it.date }

    // ─── Séance de référence et colonne « Préc. » ──────────────────────────────────────

    @Test
    fun `la référence est la dernière séance où l'exercice a une série validée, quelle que soit sa clé`() {
        val push = workout("2026-11-01", exercise("bench", set(8, 80.0)))
        val upper = workout("2026-11-05", exercise("bench", set(6, 85.0)), preset = null)
        // Plus récente, mais l'exercice n'y a aucune série validée : ignorée.
        val skipped = workout("2026-11-08", exercise("bench", set(8, 87.5, completed = false)))
        val all = history(push, upper, skipped)
        assertEquals(upper.id, SessionInsights.reference(all, "bench")?.workout?.id)
        assertNull(SessionInsights.reference(all, "squat"))
        assertNull(SessionInsights.reference(emptyList(), "bench"))
    }

    @Test
    fun `Préc de rang n = n-ième série validée de la référence`() {
        val last = workout(
            "2026-11-05",
            exercise("bench", set(8, 80.0), set(8, 82.5, completed = false), set(7, 82.5), set(6, 82.5)),
        )
        val reference = SessionInsights.reference(history(last), "bench")!!
        assertEquals(listOf(8, 7, 6), reference.completedSets.map { it.reps })
        assertEquals(80.0, reference.previous(0)!!.weight, 0.0)
        assertEquals(7, reference.previous(1)!!.reps)
        assertEquals(6, reference.previous(2)!!.reps)
        assertNull(reference.previous(3))
        assertNull(reference.previous(-1))
    }

    @Test
    fun `recopier Préc remplace les valeurs mais garde l'id et la validation`() {
        val target = set(10, 20.0, completed = true)
        val copied = SessionInsights.copyPrevious(target, set(8, 82.5))
        assertEquals(target.id, copied.id)
        assertTrue(copied.completed)
        assertEquals(8, copied.reps)
        assertEquals(82.5, copied.weight, 0.0)

        val run = SessionInsights.copyPrevious(cardio(20.0, completed = false), cardio(30.0, 5.2, 10.0, 2.0))
        assertEquals(30.0, run.duration!!, 0.0)
        assertEquals(5.2, run.distance!!, 0.0)
        assertEquals(10.0, run.speed!!, 0.0)
        assertEquals(2.0, run.incline!!, 0.0)
        assertFalse(run.completed)
    }

    // ─── Suggestion de charge ────────────────────────────────────────────────────────

    private fun suggestionFor(vararg sets: WorkoutSet, isCardio: Boolean = false): LoadSuggestion? =
        SessionInsights.suggestion(SessionInsights.reference(history(workout("2026-11-05", exercise("bench", *sets))), "bench"), isCardio)

    @Test
    fun `toutes les séries ont tenu les reps de la première, +2,5 kg`() {
        assertEquals(LoadSuggestion(bodyWeight = false, increase = true), suggestionFor(set(8, 80.0), set(8, 80.0), set(8, 80.0)))
        // Faire plus que la première compte comme tenu.
        assertEquals(LoadSuggestion(bodyWeight = false, increase = true), suggestionFor(set(8, 80.0), set(9, 80.0)))
        assertEquals(LoadSuggestion(bodyWeight = false, increase = true), suggestionFor(set(5, 100.0)))
    }

    @Test
    fun `une série sous les reps de la première, même charge`() {
        assertEquals(LoadSuggestion(bodyWeight = false, increase = false), suggestionFor(set(8, 80.0), set(8, 80.0), set(6, 80.0)))
        // Séries à reps décroissantes : même charge (limite connue, § 15.7.3).
        assertEquals(LoadSuggestion(bodyWeight = false, increase = false), suggestionFor(set(12, 60.0), set(10, 65.0), set(8, 70.0)))
    }

    @Test
    fun `une série prévue non validée, même charge`() {
        assertEquals(LoadSuggestion(bodyWeight = false, increase = false), suggestionFor(set(8, 80.0), set(8, 80.0), set(8, 80.0, completed = false)))
    }

    @Test
    fun `poids du corps, +1 rep ou mêmes reps`() {
        assertEquals(LoadSuggestion(bodyWeight = true, increase = true), suggestionFor(set(12, 0.0), set(12, 0.0)))
        assertEquals(LoadSuggestion(bodyWeight = true, increase = false), suggestionFor(set(12, 0.0), set(10, 0.0)))
        // Une seule série lestée : ce n'est plus du poids du corps.
        assertEquals(LoadSuggestion(bodyWeight = false, increase = true), suggestionFor(set(10, 0.0), set(10, 10.0)))
    }

    @Test
    fun `ni suggestion en cardio ni sans référence`() {
        assertNull(suggestionFor(cardio(30.0), isCardio = true))
        assertNull(SessionInsights.suggestion(null, isCardio = false))
    }

    @Test
    fun `pas de la suggestion`() {
        val up = LoadSuggestion(bodyWeight = false, increase = true)
        assertEquals(2.5, up.weightDelta, 0.0)
        assertEquals(0, up.repsDelta)
        val reps = LoadSuggestion(bodyWeight = true, increase = true)
        assertEquals(0.0, reps.weightDelta, 0.0)
        assertEquals(1, reps.repsDelta)
        val same = LoadSuggestion(bodyWeight = false, increase = false)
        assertEquals(0.0, same.weightDelta, 0.0)
        assertEquals(0, same.repsDelta)
    }

    @Test
    fun `appliquer la suggestion, séries non validées avec un Préc seulement, sans cumul`() {
        val last = workout("2026-11-05", exercise("bench", set(8, 80.0), set(8, 81.25), set(8, 80.0)))
        val reference = SessionInsights.reference(history(last), "bench")!!
        val suggestion = SessionInsights.suggestion(reference, isCardio = false)!!
        val current = listOf(
            set(8, 82.5, completed = true), // validée : jamais touchée
            set(6, 80.0, completed = false),
            set(8, 70.0, completed = false),
            set(8, 80.0, completed = false), // rang 4 : pas de Préc
        )
        val applied = SessionInsights.applySuggestion(current, reference, suggestion)
        assertEquals(listOf(82.5, 83.75, 82.5, 80.0), applied.map { it.weight })
        // Reps inchangées, ids et validation gardés.
        assertEquals(listOf(8, 6, 8, 8), applied.map { it.reps })
        assertEquals(current.map { it.id }, applied.map { it.id })
        assertEquals(current.map { it.completed }, applied.map { it.completed })
        // Toucher deux fois donne le même résultat.
        assertEquals(applied, SessionInsights.applySuggestion(applied, reference, suggestion))
    }

    @Test
    fun `appliquer mêmes reps ou +1 rep au poids du corps`() {
        val last = workout("2026-11-05", exercise("pullup", set(10, 0.0), set(10, 0.0)))
        val reference = SessionInsights.reference(history(last), "pullup")!!
        val current = listOf(set(8, 0.0, completed = false), set(8, 0.0, completed = false))
        val plusOne = SessionInsights.applySuggestion(current, reference, LoadSuggestion(bodyWeight = true, increase = true))
        assertEquals(listOf(11, 11), plusOne.map { it.reps })
        assertEquals(listOf(0.0, 0.0), plusOne.map { it.weight })
        val same = SessionInsights.applySuggestion(current, reference, LoadSuggestion(bodyWeight = true, increase = false))
        assertEquals(listOf(10, 10), same.map { it.reps })
    }

    @Test
    fun `appliquer même charge reprend la charge de Préc`() {
        val last = workout("2026-11-05", exercise("bench", set(8, 80.0), set(6, 80.0)))
        val reference = SessionInsights.reference(history(last), "bench")!!
        val current = listOf(set(8, 85.0, completed = false), set(8, 85.0, completed = false))
        val applied = SessionInsights.applySuggestion(current, reference, SessionInsights.suggestion(reference, isCardio = false)!!)
        assertEquals(listOf(80.0, 80.0), applied.map { it.weight })
    }

    @Test
    fun `rien à appliquer, la même liste`() {
        val last = workout("2026-11-05", exercise("bench", set(8, 80.0)))
        val reference = SessionInsights.reference(history(last), "bench")!!
        val current = listOf(set(8, 82.5, completed = true))
        assertSame(current, SessionInsights.applySuggestion(current, reference, LoadSuggestion(bodyWeight = false, increase = true)))
    }

    // ─── Place du repos ──────────────────────────────────────────────────────────────

    @Test
    fun `le repos suit la dernière série validée dans l'ordre de la séance`() {
        val a1 = set(8, 80.0, completed = true)
        val a2 = set(8, 80.0, completed = false)
        val b1 = set(10, 60.0, completed = true)
        val b2 = set(10, 60.0, completed = false)
        val current = workout("2026-11-12", exercise("bench", a1, a2), exercise("row", b1, b2))
        assertEquals(b1.id, SessionInsights.restAnchor(current))
        // Un exercice masqué (inconnu) ne compte pas.
        assertEquals(a1.id, SessionInsights.restAnchor(current) { it != "row" })
        // Aucune série validée : en haut de la liste.
        val fresh = workout("2026-11-12", exercise("bench", set(8, 80.0, completed = false)))
        assertNull(SessionInsights.restAnchor(fresh))
    }
}
