package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Règles des séances (§ 3.1 – 3.4, § 3.9), sans stockage. */
class WorkoutRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val now: Instant = Instant.parse("2026-03-14T18:00:00Z")

    /** Ids prévisibles : `n-1`, `n-2`… */
    private fun ids(prefix: String = "n"): () -> String {
        var next = 0
        return { "$prefix-${++next}" }
    }

    private fun set(id: String, reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet(id, reps, weight, completed)

    private fun cardio(id: String, duration: Double?, distance: Double?, speed: Double?, incline: Double?, completed: Boolean = true) =
        WorkoutSet(id, 0, 0.0, completed, duration = duration, distance = distance, speed = speed, incline = incline)

    private fun exercise(id: String, vararg sets: WorkoutSet, note: String? = null) = WorkoutExercise(id, note, sets.toList())

    private fun workout(
        id: String,
        date: String,
        name: String = "",
        preset: PresetId? = null,
        exercises: List<WorkoutExercise> = emptyList(),
        durationMinutes: Int? = 60,
    ) = Workout(id, name, preset, date, 80.0, exercises, durationMinutes)

    // ─── § 3.1 : clé de séance et pré-remplissage ───────────────────────────────

    @Test
    fun `la clé de séance vient du préréglage, sinon du nom normalisé`() {
        assertEquals("preset:push", WorkoutRules.sessionKey(PresetId.Push, "Push"))
        assertEquals("preset:legsSplit", WorkoutRules.sessionKey(PresetId.LegsSplit, "Legs"))
        assertEquals("name:epaules / mollets", WorkoutRules.sessionKey(null, "  Épaules / Mollets "))
        // 🔄 D45 : suites d'espaces réduites ; nom d'un préréglage = ce préréglage (§ 15.19.2).
        assertEquals("name:bras / abdos", WorkoutRules.sessionKey(null, "Bras  /  ABDOS"))
        assertEquals("preset:push", WorkoutRules.sessionKey(null, "Push"))
        assertEquals("preset:upperBody", WorkoutRules.sessionKey(null, " haut du corps "))
        assertEquals("preset:glutes", WorkoutRules.sessionKey(null, "Glutes"))
        assertEquals("name:", WorkoutRules.sessionKey(null, ""))
    }

    @Test
    fun `une séance perso au nom d'un préréglage y est rattachée, son nom gardé`() {
        val old = workout("w", "2026-03-12T17:00:00.000Z", "haut du corps")
        assertEquals(PresetId.UpperBody, WorkoutRules.presetOf(old))
        assertEquals(old.copy(preset = PresetId.UpperBody), WorkoutRules.withPresetFromName(old))
        assertEquals("haut du corps", WorkoutRules.withPresetFromName(old).name)
        // Un préréglage enregistré n'est jamais remplacé ; un nom libre reste libre.
        val push = workout("p", "2026-03-12T17:00:00.000Z", "Abdos", PresetId.Push)
        assertSame(push, WorkoutRules.withPresetFromName(push))
        val custom = workout("c", "2026-03-12T17:00:00.000Z", "Bras / Abdos")
        assertSame(custom, WorkoutRules.withPresetFromName(custom))
        assertNull(WorkoutRules.presetOf(custom))
    }

    @Test
    fun `une nouvelle séance reprend le plan de la dernière séance de même clé`() {
        val history = listOf(
            workout("w3", "2026-03-13T17:00:00.000Z", "Pull", PresetId.Pull, listOf(exercise("row", set("p", 10, 50.0)))),
            workout(
                "w2", "2026-03-12T17:00:00.000Z", "Push", PresetId.Push,
                listOf(
                    exercise("bench", set("a", 8, 80.0, completed = true), set("b", 6, 82.5, completed = false), note = "Prise serrée"),
                    exercise("treadmill", cardio("c", duration = 20.0, distance = 3.2, speed = null, incline = 2.0)),
                ),
            ),
            workout("w1", "2026-03-10T17:00:00.000Z", "Push", PresetId.Push, listOf(exercise("ohp", set("o", 5, 40.0)))),
        )

        val started = WorkoutRules.newWorkout(history, 78.5, PresetId.Push, "Push", now, ids())

        assertEquals(
            Workout(
                id = "n-1",
                name = "Push",
                preset = PresetId.Push,
                date = "2026-03-14T18:00:00.000Z",
                bodyWeight = 78.5,
                exercises = listOf(
                    // Même ordre, note gardée, toutes les séries (validées ou non) clonées non validées.
                    exercise("bench", set("n-2", 8, 80.0, completed = false), set("n-3", 6, 82.5, completed = false), note = "Prise serrée"),
                    // Champs cardio conservés, un champ absent reste absent.
                    exercise("treadmill", cardio("n-4", duration = 20.0, distance = 3.2, speed = null, incline = 2.0, completed = false)),
                ),
                durationMinutes = null,
            ),
            started,
        )
        // L'historique n'est pas modifié.
        assertTrue(history[1].exercises[0].sets[0].completed)
        assertEquals("a", history[1].exercises[0].sets[0].id)
    }

    @Test
    fun `un nom perso égal à un préréglage est ce préréglage, au démarrage comme au pré-remplissage`() {
        // 🔄 D45 (§ 15.19.2) : l'ancienne séance perso « Haut du corps » sert de plan au préréglage.
        val history = listOf(
            workout("pull", "2026-03-13T17:00:00.000Z", "Pull", PresetId.Pull, listOf(exercise("row"))),
            workout("perso", "2026-03-12T17:00:00.000Z", "Haut du corps", null, listOf(exercise("bench"), exercise("curl"))),
        )
        val fromPreset = WorkoutRules.newWorkout(history, 80.0, PresetId.UpperBody, "Haut du corps", now, ids())
        assertEquals(listOf("bench", "curl"), fromPreset.exercises.map { it.exerciseId })
        assertEquals(PresetId.UpperBody, fromPreset.preset)

        // Taper « abdos » dans « Personnalisé » démarre le préréglage Abdos, nom gardé.
        val typed = WorkoutRules.newWorkout(history, 80.0, null, "  abdos ", now, ids())
        assertEquals(PresetId.Abs, typed.preset)
        assertEquals("abdos", typed.name)
        // Taper « pull » reprend le plan du préréglage Pull.
        assertEquals(listOf("row"), WorkoutRules.newWorkout(history, 80.0, null, "pull", now, ids()).exercises.map { it.exerciseId })
    }

    @Test
    fun `le renommage rattache un nom de préréglage, un nom libre efface le préréglage`() {
        val past = workout("w", "2026-03-12T17:00:00.000Z", "Push", PresetId.Push, listOf(exercise("bench")))
        val glutes = WorkoutRules.patch(past, past.exercises, 0.0, " fessiers ", null)
        assertEquals(PresetId.Glutes, glutes.preset)
        assertEquals("fessiers", glutes.name)
        val custom = WorkoutRules.patch(past, past.exercises, 0.0, "Bras / Abdos", null)
        assertNull(custom.preset)
        assertEquals(PresetId.Pull, WorkoutRules.patch(past, past.exercises, 0.0, "Pull", PresetId.Pull).preset)
    }

    @Test
    fun `un nom perso est retrouvé sans casse, accents ni espaces en trop et enregistré sans espaces autour`() {
        val history = listOf(workout("w", "2026-03-12T17:00:00.000Z", "Épaules / Mollets", null, listOf(exercise("ohp"))))
        val started = WorkoutRules.newWorkout(history, 80.0, null, "  epaules  /  MOLLETS ", now, ids())
        assertEquals("epaules  /  MOLLETS", started.name)
        assertNull(started.preset)
        assertEquals(listOf("ohp"), started.exercises.map { it.exerciseId })
    }

    @Test
    fun `sans séance de même clé la nouvelle séance est vide`() {
        val history = listOf(workout("w", "2026-03-12T17:00:00.000Z", "Pull", PresetId.Pull, listOf(exercise("row"))))
        val started = WorkoutRules.newWorkout(history, 75.0, PresetId.Legs, "Legs", now, ids())
        assertTrue(started.exercises.isEmpty())
        assertEquals(PresetId.Legs, started.preset)
        assertNull(started.durationMinutes)
    }

    // ─── § 3.2 : ajout d'exercice ────────────────────────────────────────────────

    private val current = workout("cur", "2026-03-14T17:00:00.000Z", "Push", PresetId.Push, listOf(exercise("squat")), null)

    @Test
    fun `ajouter un exercice reprend la note et les séries de la dernière séance qui le contient`() {
        val history = listOf(
            workout("w3", "2026-03-13T17:00:00.000Z", exercises = listOf(exercise("row", set("r", 10, 50.0)))),
            workout("w2", "2026-03-12T17:00:00.000Z", exercises = listOf(exercise("bench", set("a", 8, 80.0), set("b", 6, 85.0, false), note = "Coudes"))),
            workout("w1", "2026-03-10T17:00:00.000Z", exercises = listOf(exercise("bench", set("old", 12, 60.0)))),
        )
        val updated = WorkoutRules.addExercise(current, history, "bench", ids())
        assertEquals(listOf("squat", "bench"), updated.exercises.map { it.exerciseId })
        assertEquals(
            exercise("bench", set("n-1", 8, 80.0, completed = false), set("n-2", 6, 85.0, completed = false), note = "Coudes"),
            updated.exercises.last(),
        )
    }

    @Test
    fun `la séance la plus récente qui contient l'exercice compte même sans série`() {
        val history = listOf(
            workout("w2", "2026-03-12T17:00:00.000Z", exercises = listOf(exercise("dips"))),
            workout("w1", "2026-03-10T17:00:00.000Z", exercises = listOf(exercise("dips", set("d", 10, 0.0)))),
        )
        assertTrue(WorkoutRules.addExercise(current, history, "dips", ids()).exercises.last().sets.isEmpty())
    }

    @Test
    fun `un exercice jamais fait est ajouté sans série, un exercice déjà présent ne change rien`() {
        val added = WorkoutRules.addExercise(current, emptyList(), "curl", ids())
        assertEquals(exercise("curl"), added.exercises.last())
        assertSame(current, WorkoutRules.addExercise(current, emptyList(), "squat", ids()))
    }

    // ─── § 2.5 : nouvelle série ──────────────────────────────────────────────────

    @Test
    fun `une nouvelle série de force reprend la dernière, sinon 10 × 20 kg, non validée`() {
        assertEquals(set("x", 10, 20.0, completed = false), WorkoutRules.newSet(null, kind = SetKind.Strength, id = "x"))
        assertEquals(set("y", 8, 82.5, completed = false), WorkoutRules.newSet(set("a", 8, 82.5, completed = true), kind = SetKind.Strength, id = "y"))
    }

    @Test
    fun `une nouvelle série de cardio reprend la dernière champ par champ, sinon 30 min`() {
        assertEquals(
            cardio("x", duration = 30.0, distance = 0.0, speed = 0.0, incline = 0.0, completed = false),
            WorkoutRules.newSet(null, kind = SetKind.Cardio, id = "x"),
        )
        val last = cardio("a", duration = 25.0, distance = null, speed = 9.5, incline = null)
        assertEquals(
            cardio("y", duration = 25.0, distance = 0.0, speed = 9.5, incline = 0.0, completed = false),
            WorkoutRules.newSet(last, kind = SetKind.Cardio, id = "y"),
        )
    }

    @Test
    fun `une nouvelle série au temps reprend la durée et le lest de la dernière, sinon 0 30 sans lest`() {
        // § 15.18.2, 🔄 D44.
        assertEquals(WorkoutSet("x", 0, 0.0, false, durationSeconds = 30), WorkoutRules.newSet(null, kind = SetKind.Timed, id = "x"))
        val last = WorkoutSet("a", 0, 10.0, true, durationSeconds = 75)
        assertEquals(WorkoutSet("y", 0, 10.0, false, durationSeconds = 75), WorkoutRules.newSet(last, kind = SetKind.Timed, id = "y"))
        // Après une ancienne série en reps (sans durée) : 0:30, son lest.
        assertEquals(
            WorkoutSet("z", 0, 0.0, false, durationSeconds = 30),
            WorkoutRules.newSet(WorkoutSet("b", 12, 0.0, true), kind = SetKind.Timed, id = "z"),
        )
    }

    @Test
    fun `la durée d'une série au temps est reprise au pré-remplissage`() {
        val cloned = WorkoutRules.cloneSetsForReuse(listOf(WorkoutSet("a", 0, 5.0, true, durationSeconds = 60)), ids()).single()
        assertEquals(60, cloned.durationSeconds)
        assertEquals(5.0, cloned.weight, 0.0)
        assertFalse(cloned.completed)
    }

    @Test
    fun `l'éditeur de séance terminée crée des séries validées`() {
        assertTrue(WorkoutRules.newSet(null, kind = SetKind.Strength, id = "x", completed = true).completed)
        assertTrue(WorkoutRules.newSet(null, kind = SetKind.Timed, id = "x", completed = true).completed)
    }

    // ─── § 3.3 : fin de séance ───────────────────────────────────────────────────

    @Test
    fun `terminer une séance avec une série validée l'enregistre avec sa durée arrondie`() {
        val started = workout(
            "cur", "2026-03-14T16:47:30.000Z",
            exercises = listOf(exercise("bench", set("a", 8, 80.0, true), set("b", 8, 80.0, false))),
            durationMinutes = null,
        )
        // 72,5 min → 73 (demi arrondi vers le haut) ; la série non validée reste stockée.
        assertEquals(WorkoutEnd.Finished(started.copy(durationMinutes = 73)), WorkoutRules.end(started, now, zone))
        // 29 s → 0 min.
        val brief = started.copy(date = "2026-03-14T17:59:31.000Z")
        assertEquals(0, (WorkoutRules.end(brief, now, zone) as WorkoutEnd.Finished).workout.durationMinutes)
    }

    @Test
    fun `terminer une séance sans série validée l'abandonne`() {
        val started = workout("cur", "2026-03-14T17:00:00.000Z", exercises = listOf(exercise("bench", set("a", 8, 80.0, false))), durationMinutes = null)
        assertEquals(WorkoutEnd.Abandoned, WorkoutRules.end(started, now, zone))
        assertEquals(WorkoutEnd.Abandoned, WorkoutRules.end(started.copy(exercises = emptyList()), now, zone))
    }

    // ─── § 3.4 : finalisation après 24 h ──────────────────────────────────────────

    @Test
    fun `une séance en cours est périmée au-delà de 24 h`() {
        val dayAgo = workout("cur", "2026-03-13T18:00:00.000Z", durationMinutes = null)
        assertFalse(WorkoutRules.isStale(dayAgo, now, zone))
        assertTrue(WorkoutRules.isStale(dayAgo, now.plusMillis(1), zone))
        assertTrue(WorkoutRules.isStale(dayAgo.copy(date = "pas une date"), now, zone))
    }

    @Test
    fun `la finalisation automatique enregistre sans durée ou abandonne`() {
        val done = workout("cur", "2026-03-12T18:00:00.000Z", exercises = listOf(exercise("bench", set("a", 8, 80.0, true))), durationMinutes = null)
        assertEquals(WorkoutEnd.Finished(done.copy(durationMinutes = null)), WorkoutRules.finalizeStale(done))
        val notDone = done.copy(exercises = listOf(exercise("bench", set("a", 8, 80.0, false))))
        assertEquals(WorkoutEnd.Abandoned, WorkoutRules.finalizeStale(notDone))
    }

    // ─── § 3.9 : références et ordre ─────────────────────────────────────────────

    @Test
    fun `un exercice est référencé par la séance en cours ou l'historique, même sans série`() {
        val history = listOf(workout("w", "2026-03-12T17:00:00.000Z", exercises = listOf(exercise("custom-1"))))
        assertTrue(WorkoutRules.isReferenced("custom-1", null, history))
        assertTrue(WorkoutRules.isReferenced("squat", current, emptyList()))
        assertFalse(WorkoutRules.isReferenced("custom-2", current, history))
    }

    @Test
    fun `réordonner garde à la fin les exercices non listés`() {
        val w = current.copy(exercises = listOf(exercise("a"), exercise("b"), exercise("unknown"), exercise("c")))
        val reordered = WorkoutRules.reorder(w, listOf("c", "a", "b", "ghost", "a"))
        assertEquals(listOf("c", "a", "b", "unknown"), reordered.exercises.map { it.exerciseId })
    }
}
