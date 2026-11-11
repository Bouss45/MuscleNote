package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Statistiques des séances (§ 2.2, § 2.8, § 3.5 – 3.8). */
class WorkoutStatsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private var nextId = 0

    private fun set(reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet("s${++nextId}", reps, weight, completed)

    private fun cardio(duration: Double, distance: Double = 0.0, completed: Boolean = true) =
        WorkoutSet("c${++nextId}", 0, 0.0, completed, duration = duration, distance = distance, speed = 0.0, incline = 0.0)

    private fun workout(
        date: String,
        vararg exercises: WorkoutExercise,
        name: String = "",
        preset: PresetId? = null,
        durationMinutes: Int? = 60,
        id: String = "w${++nextId}",
    ) = Workout(id, name, preset, date, 80.0, exercises.toList(), durationMinutes)

    /** Séance d'une série validée, à midi heure de Paris le jour [day]. */
    private fun sessionOn(day: String) = workout("${day}T11:00:00.000Z", WorkoutExercise("bench", sets = listOf(set(8, 80.0))))

    /** [count] séances dans la semaine du lundi [monday] (lundi, mardi, mercredi…). */
    private fun week(monday: String, count: Int): List<Workout> =
        (0 until count).map { sessionOn(LocalDate.parse(monday).plusDays(it.toLong()).toString()) }

    // ─── § 3.5 : 1RM ──────────────────────────────────────────────────────────────

    @Test
    fun `1RM d'Epley arrondi à l'entier, demis vers le haut`() {
        assertEquals(0.0, WorkoutStats.estimateOneRepMax(0.0, 5), 0.0)
        assertEquals(0.0, WorkoutStats.estimateOneRepMax(100.0, 0), 0.0)
        assertEquals(0.0, WorkoutStats.estimateOneRepMax(-10.0, 5), 0.0)
        // Une seule rep : le poids lui-même, décimales comprises.
        assertEquals(62.5, WorkoutStats.estimateOneRepMax(62.5, 1), 0.0)
        assertEquals(117.0, WorkoutStats.estimateOneRepMax(100.0, 5), 0.0) // 116,67
        assertEquals(101.0, WorkoutStats.estimateOneRepMax(80.0, 8), 0.0) // 101,33
        // 3 × 1,5 = 4,5 → 5 (Math.round) et non 4 (arrondi au pair).
        assertEquals(5.0, WorkoutStats.estimateOneRepMax(3.0, 15), 0.0)
    }

    @Test
    fun `le meilleur 1RM ne regarde que les séries validées`() {
        val sets = listOf(set(5, 100.0), set(1, 130.0, completed = false), set(10, 70.0))
        assertEquals(117.0, WorkoutStats.bestOneRepMax(sets), 0.0) // 100 × 5 → 117 ; 70 × 10 → 93
        assertEquals(0.0, WorkoutStats.bestOneRepMax(emptyList()), 0.0)
        assertEquals(0.0, WorkoutStats.bestOneRepMax(listOf(set(5, 100.0, completed = false))), 0.0)
    }

    // ─── § 3.8 : volume ───────────────────────────────────────────────────────────

    @Test
    fun `le volume ne compte que les séries validées, et le cardio vaut 0`() {
        val w = workout(
            "2026-03-10T17:00:00.000Z",
            WorkoutExercise("bench", sets = listOf(set(8, 80.0), set(6, 82.5), set(10, 100.0, completed = false))),
            WorkoutExercise("treadmill", sets = listOf(cardio(20.0, 3.2))),
            WorkoutExercise("curl", sets = listOf(set(12, 15.0, completed = false))),
            WorkoutExercise("row", sets = emptyList()),
        )
        assertEquals(8 * 80.0 + 6 * 82.5, WorkoutStats.volume(w), 1e-9)
        assertEquals(3, WorkoutStats.completedSetCount(w))
        // bench et treadmill ont une série validée ; curl n'en a pas, row n'a aucune série.
        assertEquals(2, WorkoutStats.exercisesWithCompletedSets(w))
    }

    // ─── § 3.6 : série hebdomadaire ────────────────────────────────────────────────

    /** Aujourd'hui : jeudi 12 mars 2026 ; semaine courante = lundi 9 mars. */
    private val today = LocalDate.parse("2026-03-12")

    @Test
    fun `sans séance, pas de série et carte masquée`() {
        assertEquals(Streak.NONE, WorkoutStats.streak(emptyList(), today, zone))
    }

    @Test
    fun `les semaines validées consécutives comptent, la semaine en cours n'est jamais pénalisée`() {
        val workouts = week("2026-02-16", 3) + week("2026-02-23", 4) + week("2026-03-02", 3) + week("2026-03-09", 1)
        val streak = WorkoutStats.streak(workouts, today, zone)
        assertEquals(3, streak.current)
        assertTrue(streak.isAlive)
        assertEquals(3, streak.best)
    }

    @Test
    fun `la semaine courante validée compte dans la série`() {
        val streak = WorkoutStats.streak(week("2026-03-02", 3) + week("2026-03-09", 3), today, zone)
        assertEquals(2, streak.current)
        assertEquals(2, streak.best)
    }

    @Test
    fun `une semaine non validée isolée est tolérée sans compter`() {
        // Validée, partielle, validée, puis la semaine courante vide.
        val workouts = week("2026-02-16", 3) + week("2026-02-23", 2) + week("2026-03-02", 3)
        val streak = WorkoutStats.streak(workouts, today, zone)
        assertEquals(2, streak.current)
        assertTrue(streak.isAlive)
        assertEquals(2, streak.best)
    }

    @Test
    fun `deux semaines non validées consécutives cassent la série`() {
        val workouts = week("2026-01-26", 3) + week("2026-02-02", 3) + week("2026-02-09", 3) +
            week("2026-02-16", 1) + /* 23 février : rien */ week("2026-03-02", 3)
        val streak = WorkoutStats.streak(workouts, today, zone)
        assertEquals(1, streak.current)
        assertFalse(streak.isAlive)
        // La meilleure série est celle de fin janvier – début février.
        assertEquals(3, streak.best)
    }

    @Test
    fun `une série terminée depuis longtemps vaut 0 mais garde sa meilleure valeur`() {
        val streak = WorkoutStats.streak(week("2026-01-05", 3) + week("2026-01-12", 5), today, zone)
        assertEquals(0, streak.current)
        assertFalse(streak.isAlive)
        assertEquals(2, streak.best)
    }

    @Test
    fun `une série en vie qui remonte jusqu'à la première séance n'est pas cassée`() {
        val streak = WorkoutStats.streak(week("2026-03-02", 3), today, zone)
        assertEquals(1, streak.current)
        assertTrue(streak.isAlive)
    }

    @Test
    fun `la semaine ISO enjambe le changement d'année`() {
        // Jeudi 31 décembre 2026, vendredi 1er et dimanche 3 janvier 2027 : semaine 2026-53.
        val workouts = listOf(sessionOn("2026-12-31"), sessionOn("2027-01-01"), sessionOn("2027-01-03"))
        val streak = WorkoutStats.streak(workouts, LocalDate.parse("2027-01-06"), zone)
        assertEquals(1, streak.current)
        assertEquals(LocalDate.parse("2026-12-28"), streak.lastWeeks[6].monday)
        assertEquals(3, streak.lastWeeks[6].count)
    }

    @Test
    fun `une séance compte dans la semaine de sa date locale, pas UTC`() {
        // Dimanche 8 mars 23 h 30 UTC = lundi 9 mars 0 h 30 à Paris : semaine courante.
        val late = workout("2026-03-08T23:30:00.000Z", WorkoutExercise("bench", sets = listOf(set(8, 80.0))))
        val streak = WorkoutStats.streak(week("2026-03-09", 2) + late, today, zone)
        assertEquals(3, streak.lastWeeks.last().count)
        assertEquals(1, streak.current)
    }

    @Test
    fun `le mini-calendrier couvre 8 semaines, de la plus ancienne à la semaine courante`() {
        val workouts = week("2026-01-19", 3) + // 7 semaines avant : validée
            week("2026-02-02", 1) + // partielle
            week("2026-03-02", 4) + // validée
            week("2026-03-09", 2) // semaine courante non validée : « aucune », nombre affiché
        val cells = WorkoutStats.streak(workouts, today, zone).lastWeeks
        assertEquals(8, cells.size)
        assertEquals(LocalDate.parse("2026-01-19"), cells.first().monday)
        assertEquals(LocalDate.parse("2026-03-09"), cells.last().monday)
        assertEquals(listOf(3, 0, 1, 0, 0, 0, 4, 2), cells.map { it.count })
        assertEquals(
            listOf(
                WeekStatus.Validated, WeekStatus.None, WeekStatus.Partial, WeekStatus.None,
                WeekStatus.None, WeekStatus.None, WeekStatus.Validated, WeekStatus.None,
            ),
            cells.map { it.status },
        )
        assertEquals(listOf(false, false, false, false, false, false, false, true), cells.map { it.isCurrent })
    }

    // ─── § 3.7 : temps en salle ─────────────────────────────────────────────────────

    @Test
    fun `une séance courte ou sans durée prend la moyenne de sa clé, sinon elle est ignorée`() {
        val workouts = listOf(
            workout("2026-03-10T17:00:00.000Z", name = "Push", preset = PresetId.Push, durationMinutes = 60),
            workout("2026-03-08T17:00:00.000Z", name = "Push", preset = PresetId.Push, durationMinutes = 50),
            // Courte et auto-finalisée : moyenne des Push (55).
            workout("2026-03-06T17:00:00.000Z", name = "Push", preset = PresetId.Push, durationMinutes = 5),
            workout("2026-03-05T17:00:00.000Z", name = "Push", preset = PresetId.Push, durationMinutes = null),
            // Même clé malgré la casse, les accents et les espaces : moyenne 40.
            workout("2026-03-04T17:00:00.000Z", name = "Épaules", durationMinutes = 40),
            workout("2026-03-03T17:00:00.000Z", name = " epaules ", durationMinutes = 3),
            // Aucune séance de référence : ignorée.
            workout("2026-03-02T17:00:00.000Z", name = "Solo", durationMinutes = 4),
        )
        val gymTime = WorkoutStats.gymTime(workouts)
        assertEquals(GymTime(totalMinutes = 60 + 50 + 55 + 55 + 40 + 40, averageMinutes = 50, sessionCount = 6), gymTime)
    }

    @Test
    fun `les moyennes du temps en salle sont arrondies demis vers le haut`() {
        val workouts = listOf(
            workout("2026-03-10T17:00:00.000Z", name = "A", durationMinutes = 60),
            workout("2026-03-09T17:00:00.000Z", name = "A", durationMinutes = 51),
            workout("2026-03-08T17:00:00.000Z", name = "A", durationMinutes = 0),
        )
        // Moyenne de A = 55,5 → 56 ; total 167 sur 3 séances → 55,67 → 56.
        assertEquals(GymTime(totalMinutes = 167, averageMinutes = 56, sessionCount = 3), WorkoutStats.gymTime(workouts))
        assertEquals(GymTime(0, 0, 0), WorkoutStats.gymTime(listOf(workout("2026-03-08T17:00:00.000Z", name = "B", durationMinutes = 2))))
    }

    @Test
    fun `format des durées`() {
        assertEquals("0m", WorkoutStats.formatDuration(0))
        assertEquals("45m", WorkoutStats.formatDuration(45))
        assertEquals("1h", WorkoutStats.formatDuration(60))
        assertEquals("1h 5m", WorkoutStats.formatDuration(65))
        assertEquals("26h 40m", WorkoutStats.formatDuration(1600))
    }

    // ─── § 2.8 : historique d'un exercice ───────────────────────────────────────────

    @Test
    fun `l'historique d'un exercice ne garde que les séances et séries validées, du plus récent au plus ancien`() {
        val workouts = listOf(
            workout("2026-03-10T17:00:00.000Z", WorkoutExercise("bench", sets = listOf(set(8, 85.0), set(5, 90.0, completed = false))), id = "recent"),
            workout("2026-03-08T17:00:00.000Z", WorkoutExercise("bench", sets = listOf(set(8, 80.0, completed = false))), id = "unchecked"),
            workout("2026-03-06T17:00:00.000Z", WorkoutExercise("row", sets = listOf(set(10, 60.0))), id = "other"),
            workout("2026-03-04T17:00:00.000Z", WorkoutExercise("bench", sets = listOf(set(10, 70.0), set(8, 75.0))), id = "old"),
        )
        val history = WorkoutStats.exerciseHistory(workouts, "bench", zone)
        assertEquals(listOf("recent", "old"), history.map { it.workoutId })
        assertEquals(listOf(85.0), history[0].sets.map { it.weight })
        assertEquals(75.0, history[1].maxWeight, 0.0)
        assertEquals(10 * 70.0 + 8 * 75.0, history[1].volume, 1e-9)
        assertEquals(95.0, history[1].bestOneRepMax, 0.0) // 75 × (1 + 8/30) = 95
        assertEquals(75.0, history[1].metric(isCardio = false), 0.0)
    }

    @Test
    fun `la métrique d'une séance de cardio est la somme des durées`() {
        val workouts = listOf(
            workout("2026-03-10T17:00:00.000Z", WorkoutExercise("treadmill", sets = listOf(cardio(20.0), cardio(12.5), cardio(30.0, completed = false)))),
        )
        val session = WorkoutStats.exerciseHistory(workouts, "treadmill", zone).single()
        assertEquals(32.5, session.metric(isCardio = true), 0.0)
    }
}
