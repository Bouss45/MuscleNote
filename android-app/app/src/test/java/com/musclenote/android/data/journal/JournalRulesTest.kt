package com.musclenote.android.data.journal

import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Journal (SPEC § 15.8.4) : résumé d'une séance, regroupement par semaine, libellés, totaux. */
class JournalRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private var nextId = 0

    private fun set(reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet("s${++nextId}", reps, weight, completed)

    private fun cardio(duration: Double, completed: Boolean = true) =
        WorkoutSet("c${++nextId}", 0, 0.0, completed, duration = duration, distance = 5.0, speed = 0.0, incline = 0.0)

    private fun workout(
        date: String,
        vararg sets: WorkoutSet,
        durationMinutes: Int? = 60,
        name: String = "Séance",
        id: String = "w${++nextId}",
    ) = Workout(id, name, null, date, 80.0, listOf(WorkoutExercise("bench", sets = sets.toList())), durationMinutes)

    /** Séance d'une série validée de 8 × 80, le [day] à 18 h heure de Paris (17 h UTC en hiver). */
    private fun sessionOn(day: String, id: String = "w${++nextId}") = workout("${day}T17:00:00.000Z", set(8, 80.0), id = id)

    // ─── Résumé d'une séance ──────────────────────────────────────────────────────

    @Test
    fun `la durée n'est affichée que si elle est connue et non nulle`() {
        assertEquals(72, JournalRules.summary(workout("2026-11-11T17:00:00.000Z", set(8, 80.0), durationMinutes = 72), zone).durationMinutes)
        // Moins de 10 min : c'est la vraie durée, elle s'affiche (l'estimation du § 3.7 ne sert qu'aux totaux).
        assertEquals(5, JournalRules.summary(workout("2026-11-11T17:00:00.000Z", set(8, 80.0), durationMinutes = 5), zone).durationMinutes)
        // Séance finalisée après 24 h (§ 3.4) : pas de durée.
        assertNull(JournalRules.summary(workout("2026-11-11T17:00:00.000Z", set(8, 80.0), durationMinutes = null), zone).durationMinutes)
        assertNull(JournalRules.summary(workout("2026-11-11T17:00:00.000Z", set(8, 80.0), durationMinutes = 0), zone).durationMinutes)
    }

    @Test
    fun `durée affichée en heures et minutes`() {
        assertEquals(DisplayDuration(0, 45), JournalRules.duration(45))
        assertEquals(DisplayDuration(1, 0), JournalRules.duration(60))
        assertEquals(DisplayDuration(1, 12), JournalRules.duration(72))
        assertEquals(DisplayDuration(2, 5), JournalRules.duration(125))
    }

    @Test
    fun `le volume ne compte que les séries validées, arrondi au kg, demis vers le haut`() {
        // 8 × 80 + 7 × 82,5 = 1217,5 → 1218 ; la série non validée ne compte pas.
        val summary = JournalRules.summary(
            workout("2026-11-11T17:00:00.000Z", set(8, 80.0), set(7, 82.5), set(10, 100.0, completed = false)),
            zone,
        )
        assertEquals(1218L, summary.volumeKg)
        assertEquals(2, summary.completedSets)
        assertTrue(summary.showsVolume)
    }

    @Test
    fun `sans volume (cardio, poids du corps), la ligne montre le nombre de séries validées`() {
        val cardioSession = JournalRules.summary(workout("2026-11-11T17:00:00.000Z", cardio(20.0), cardio(10.0), cardio(5.0, completed = false)), zone)
        assertEquals(0L, cardioSession.volumeKg)
        assertFalse(cardioSession.showsVolume)
        assertEquals(2, cardioSession.completedSets)

        val pullUps = JournalRules.summary(workout("2026-11-11T17:00:00.000Z", set(10, 0.0), set(8, 0.0)), zone)
        assertFalse(pullUps.showsVolume)
        assertEquals(2, pullUps.completedSets)

        // 🔄 D44 : des séries au temps, même lestées, n'ont pas de volume.
        val planks = JournalRules.summary(
            workout("2026-11-11T17:00:00.000Z", WorkoutSet("h1", 0, 10.0, true, durationSeconds = 60), WorkoutSet("h2", 0, 0.0, true, durationSeconds = 45)),
            zone,
        )
        assertEquals(0L, planks.volumeKg)
        assertFalse(planks.showsVolume)
        assertEquals(2, planks.completedSets)
    }

    @Test
    fun `le résumé porte la date locale de la séance`() {
        // 23 h 30 UTC le 15 novembre = 0 h 30 le 16 à Paris.
        val summary = JournalRules.summary(workout("2026-11-15T23:30:00.000Z", set(8, 80.0)), zone)
        assertEquals(LocalDate.of(2026, 11, 16), summary.start?.toLocalDate())
        assertNull(JournalRules.summary(workout("pas une date", set(8, 80.0)), zone).start)
    }

    // ─── Regroupement par semaine ─────────────────────────────────────────────────

    @Test
    fun `une semaine va du lundi au dimanche, au jour local`() {
        val weeks = JournalRules.weeks(
            listOf(
                sessionOn("2026-11-16", "lundi-16"),
                // Dimanche 15 à 23 h 30 à Paris : semaine du 9.
                workout("2026-11-15T22:30:00.000Z", set(8, 80.0), id = "dimanche-15"),
                // Dimanche 15 à 23 h 30 UTC = lundi 16 à 0 h 30 à Paris : semaine du 16.
                workout("2026-11-15T23:30:00.000Z", set(8, 80.0), id = "lundi-16-nuit"),
                sessionOn("2026-11-09", "lundi-9"),
                sessionOn("2026-11-08", "dimanche-8"),
            ),
            zone,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2)), weeks.map { it.monday })
        assertEquals(listOf("lundi-16", "lundi-16-nuit"), weeks[0].sessions.map { it.workout.id })
        assertEquals(listOf("dimanche-15", "lundi-9"), weeks[1].sessions.map { it.workout.id })
        assertEquals(listOf("dimanche-8"), weeks[2].sessions.map { it.workout.id })
    }

    @Test
    fun `une semaine à cheval sur deux années reste une seule section`() {
        val weeks = JournalRules.weeks(
            listOf(
                sessionOn("2026-01-05", "lundi-5-janv"),
                sessionOn("2026-01-02", "vendredi-2-janv"),
                sessionOn("2025-12-30", "mardi-30-dec"),
                sessionOn("2025-12-28", "dimanche-28-dec"),
            ),
            zone,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2025, 12, 29), LocalDate.of(2025, 12, 22)),
            weeks.map { it.monday },
        )
        assertEquals(listOf("vendredi-2-janv", "mardi-30-dec"), weeks[1].sessions.map { it.workout.id })
    }

    @Test
    fun `semaines et séances de la plus récente à la plus ancienne, quel que soit l'ordre reçu`() {
        val weeks = JournalRules.weeks(
            listOf(sessionOn("2026-10-27", "a"), sessionOn("2026-11-12", "c"), sessionOn("2026-11-10", "b")),
            zone,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 26)), weeks.map { it.monday })
        assertEquals(listOf("c", "b"), weeks[0].sessions.map { it.workout.id })
    }

    @Test
    fun `une séance à la date illisible va dans une dernière section sans semaine`() {
        val weeks = JournalRules.weeks(listOf(workout("31/02/2026", set(8, 80.0), id = "illisible"), sessionOn("2026-11-10", "ok")), zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 9), null), weeks.map { it.monday })
        assertEquals(listOf("illisible"), weeks.last().sessions.map { it.workout.id })
    }

    @Test
    fun `aucune séance, aucune section`() {
        assertTrue(JournalRules.weeks(emptyList(), zone).isEmpty())
    }

    // ─── Libellé d'une semaine ────────────────────────────────────────────────────

    @Test
    fun `cette semaine, la semaine dernière, puis le lundi`() {
        val today = LocalDate.of(2026, 11, 12) // jeudi
        assertEquals(WeekLabel.Current, JournalRules.weekLabel(LocalDate.of(2026, 11, 9), today))
        assertEquals(WeekLabel.Previous, JournalRules.weekLabel(LocalDate.of(2026, 11, 2), today))
        assertEquals(WeekLabel.Of(LocalDate.of(2026, 10, 26), withYear = false), JournalRules.weekLabel(LocalDate.of(2026, 10, 26), today))
    }

    @Test
    fun `l'année s'affiche quand le lundi n'est pas dans l'année en cours`() {
        // Vendredi 2 janvier 2026 : sa semaine commence le lundi 29 décembre 2025.
        val today = LocalDate.of(2026, 1, 2)
        assertEquals(WeekLabel.Current, JournalRules.weekLabel(LocalDate.of(2025, 12, 29), today))
        assertEquals(WeekLabel.Previous, JournalRules.weekLabel(LocalDate.of(2025, 12, 22), today))
        assertEquals(WeekLabel.Of(LocalDate.of(2025, 12, 15), withYear = true), JournalRules.weekLabel(LocalDate.of(2025, 12, 15), today))
        // En novembre 2026, la semaine du 29 décembre 2025 garde son année.
        assertEquals(
            WeekLabel.Of(LocalDate.of(2025, 12, 29), withYear = true),
            JournalRules.weekLabel(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 11, 12)),
        )
    }

    // ─── Totaux ───────────────────────────────────────────────────────────────────

    @Test
    fun `totaux - séances, tonnes arrondies, temps en salle et moyenne du § 3_7`() {
        val workouts = listOf(
            // 10 × 100 × 3 = 3000 kg chacune.
            workout("2026-11-12T17:00:00.000Z", set(10, 100.0), set(10, 100.0), set(10, 100.0), durationMinutes = 70, name = "Push"),
            workout("2026-11-10T17:00:00.000Z", set(10, 100.0), set(10, 100.0), set(10, 100.0), durationMinutes = 50, name = "Push"),
            // Séance de 4 min : comptée à la moyenne de sa clé (60).
            workout("2026-11-09T17:00:00.000Z", set(5, 100.0), durationMinutes = 4, name = "Push"),
        )
        val totals = JournalRules.totals(workouts)
        assertEquals(3, totals.sessions)
        assertEquals(7L, totals.volumeTonnes) // 6500 kg → 6,5 t → 7
        assertEquals(180, totals.gymMinutes)
        assertEquals(60, totals.averageMinutes)
    }

    @Test
    fun `sans séance retenue par le temps en salle, ni total ni moyenne`() {
        val totals = JournalRules.totals(listOf(workout("2026-11-12T17:00:00.000Z", set(10, 40.0), durationMinutes = null)))
        assertEquals(1, totals.sessions)
        assertEquals(0L, totals.volumeTonnes)
        assertNull(totals.gymMinutes)
        assertNull(totals.averageMinutes)
    }
}
