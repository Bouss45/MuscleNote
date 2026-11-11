package com.musclenote.android.data.progress

import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Calculs de l'onglet Progrès (§ 3.10). Fuseau de Paris : UTC+1 en hiver. */
class ProgressStatsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private var nextId = 0

    private fun set(reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet("s${++nextId}", reps, weight, completed)

    private fun cardio(duration: Double, distance: Double, completed: Boolean = true) =
        WorkoutSet("c${++nextId}", 0, 0.0, completed, duration = duration, distance = distance, speed = 0.0, incline = 0.0)

    private fun workout(date: String, bodyWeight: Double = 80.0, vararg exercises: WorkoutExercise, id: String = "w${++nextId}") =
        Workout(id, "", null, date, bodyWeight, exercises.toList(), 60)

    private fun ex(id: String, vararg sets: WorkoutSet) = WorkoutExercise(id, sets = sets.toList())

    private fun day(s: String) = LocalDate.parse(s)

    // ─── Poids de corps ─────────────────────────────────────────────────────────────

    @Test
    fun `un point par jour local, la séance la plus récente du jour l'emporte`() {
        val workouts = listOf(
            workout("2026-03-10T19:00:00.000Z", bodyWeight = 79.4), // 20 h à Paris
            workout("2026-03-10T08:00:00.000Z", bodyWeight = 80.0), // même jour, plus tôt
            workout("2026-03-09T23:30:00.000Z", bodyWeight = 80.6), // 10 mars 0 h 30 à Paris !
            workout("2026-03-08T17:00:00.000Z", bodyWeight = 0.0), // poids inconnu : ignoré
            workout("2026-03-07T17:00:00.000Z", bodyWeight = 81.0),
        )
        assertEquals(
            listOf(DayValue(day("2026-03-07"), 81.0), DayValue(day("2026-03-10"), 79.4)),
            ProgressStats.bodyWeightSeries(workouts, emptyList(), zone),
        )
    }

    @Test
    fun `une pesée remplace la séance du même jour et ajoute les jours sans séance`() {
        val workouts = listOf(
            workout("2026-03-10T17:00:00.000Z", bodyWeight = 79.4),
            workout("2026-03-07T17:00:00.000Z", bodyWeight = 81.0),
        )
        val weighIns = listOf(
            BodyWeightEntry("2026-03-10T11:00:00.000Z", 78.9), // midi local, même jour qu'une séance
            BodyWeightEntry("2026-03-08T11:00:00.000Z", 80.2), // jour de repos
        )
        assertEquals(
            listOf(
                DayValue(day("2026-03-07"), 81.0),
                DayValue(day("2026-03-08"), 80.2),
                DayValue(day("2026-03-10"), 78.9),
            ),
            ProgressStats.bodyWeightSeries(workouts, weighIns, zone),
        )
    }

    @Test
    fun `deux pesées le même jour local, la plus récente l'emporte`() {
        val weighIns = listOf(
            BodyWeightEntry("2026-03-10T06:00:00.000Z", 79.0),
            BodyWeightEntry("2026-03-10T18:00:00.000Z", 78.5),
        )
        assertEquals(listOf(DayValue(day("2026-03-10"), 78.5)), ProgressStats.bodyWeightSeries(emptyList(), weighIns, zone))
    }

    @Test
    fun `stats du poids de corps, évolution au dixième`() {
        val series = listOf(
            DayValue(day("2026-03-01"), 80.1),
            DayValue(day("2026-03-05"), 81.25),
            DayValue(day("2026-03-09"), 78.3),
        )
        assertEquals(BodyWeightStats(current = 78.3, change = -1.8, min = 78.3, max = 81.25), ProgressStats.bodyWeightStats(series))
        assertNull(ProgressStats.bodyWeightStats(emptyList()))
        assertEquals(0.5, ProgressStats.roundTenth(0.45), 0.0)
        assertEquals(1.3, ProgressStats.roundTenth(1.25), 0.0)
    }

    // ─── Calories et eau ──────────────────────────────────────────────────────────────

    private val today = day("2026-03-14")

    @Test
    fun `seuls les jours saisis à plus de 0 sont des points, triés`() {
        val log = mapOf("2026-03-12" to 1800, "2026-03-10" to 2200, "2026-03-11" to 0, "pas-une-date" to 900)
        assertEquals(
            listOf(DayValue(day("2026-03-10"), 2200.0), DayValue(day("2026-03-12"), 1800.0)),
            ProgressStats.dailySeries(log),
        )
    }

    @Test
    fun `la moyenne 7 jours porte sur la fenêtre calendaire et ignore les jours non saisis`() {
        val log = mapOf(
            "2026-03-14" to 2000, // aujourd'hui
            "2026-03-12" to 1800,
            "2026-03-11" to 0, // remis à zéro : ignoré, pas compté à 0
            "2026-03-08" to 2600, // J−6 : dans la fenêtre
            "2026-03-07" to 3000, // J−7 : hors fenêtre
        )
        val stats = ProgressStats.dailyStats(log, mapOf("0000-01-01" to 2000), 2000, today)!!
        assertEquals((2000 + 1800 + 2600) / 3.0, stats.average7!!, 1e-9)
        assertEquals(2000, stats.today)
        assertEquals(3000, stats.best)
        assertEquals(4, stats.daysTracked)
    }

    @Test
    fun `sans jour saisi dans la fenêtre la moyenne est absente, sans jour du tout pas de stats`() {
        val stats = ProgressStats.dailyStats(mapOf("2026-02-01" to 1500), mapOf("0000-01-01" to 2000), 2000, today)!!
        assertNull(stats.average7)
        assertEquals(0, stats.today)
        assertNull(ProgressStats.dailyStats(mapOf("2026-03-14" to 0), mapOf("0000-01-01" to 2000), 2000, today))
    }

    @Test
    fun `un jour atteint l'objectif en vigueur ce jour-là, pas l'objectif actuel`() {
        // 2000 kcal jusqu'au 9 mars, 2500 à partir du 10.
        val history = mapOf("0000-01-01" to 2000, "2026-03-10" to 2500)
        val log = mapOf(
            "2026-03-08" to 2100, // ≥ 2000 : atteint
            "2026-03-09" to 1900, // < 2000
            "2026-03-10" to 2400, // < 2500
            "2026-03-11" to 2500, // = 2500 : atteint
        )
        assertEquals(2, ProgressStats.dailyStats(log, history, 2500, today)!!.goalDaysHit)
        // Historique vide (ne devrait pas arriver) : l'objectif actuel sert de repli.
        assertEquals(1, ProgressStats.dailyStats(log, emptyMap(), 2500, today)!!.goalDaysHit)
    }

    // ─── Groupes musculaires ──────────────────────────────────────────────────────────

    /** Catalogue + deux exercices perso de pecs, dont un archivé. */
    private val definitions: List<ExerciseDefinition> = exerciseDefinitions(
        listOf(
            Exercise("press-perso", "Presse pecs", MuscleGroup.Pecs),
            Exercise("ancien", "Ancien", MuscleGroup.Pecs, archived = true),
            Exercise("rameur", "Rameur", MuscleGroup.Cardio),
        ),
    )

    @Test
    fun `une séance compte pour un groupe s'il a au moins une série validée`() {
        val workouts = listOf(
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("bench", set(8, 80.0)), ex("incline", set(8, 60.0)), ex("curl", set(10, 15.0, completed = false))),
            workout("2026-03-10T17:00:00.000Z", 80.0, ex("curl", set(10, 15.0)), ex("inconnu", set(5, 5.0))),
            workout("2026-03-08T17:00:00.000Z", 80.0, ex("bench")),
        )
        val counts = ProgressStats.muscleSessionCounts(workouts, definitions)
        assertEquals(1, counts[MuscleGroup.Pecs]) // deux exercices de pecs dans la même séance : 1
        assertEquals(1, counts[MuscleGroup.Biceps]) // la série non validée du 12 ne compte pas
        assertEquals(0, counts[MuscleGroup.Dos])
        assertEquals(MuscleGroup.entries.toSet(), counts.keys)
    }

    @Test
    fun `top 3 par nombre de séances, égalités dans l'ordre du catalogue puis des perso`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("press-perso", set(10, 50.0)), ex("dips", set(10, 0.0))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("press-perso", set(10, 45.0)), ex("fly", set(12, 14.0))),
            workout("2026-03-10T17:00:00.000Z", 80.0, ex("fly", set(12, 12.0)), ex("dips", set(8, 0.0)), ex("ancien", set(5, 30.0))),
            workout("2026-03-08T17:00:00.000Z", 80.0, ex("bench", set(5, 90.0))),
        )
        val progress = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone)
        // fly, dips et press-perso : 2 séances chacun ; fly avant dips (catalogue), puis le perso.
        assertEquals(listOf("fly", "dips", "press-perso"), progress.top3.map { it.definition.id })
        // Tous les exercices utilisés, archivés compris, dans l'ordre d'affichage.
        assertEquals(listOf("bench", "fly", "dips", "press-perso", "ancien"), progress.exercises.map { it.definition.id })
        assertEquals(4, progress.sessions)
    }

    @Test
    fun `points chronologiques, record, tendance et charge max du top 3`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("bench", set(5, 92.5), set(3, 100.0, completed = false))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("bench", set(5, 95.0))),
            workout("2026-03-10T17:00:00.000Z", 80.0, ex("bench", set(8, 80.0), set(6, 85.0))),
        )
        val bench = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).top3.single()
        assertEquals(listOf(85.0, 95.0, 92.5), bench.points.map { it.value })
        assertEquals(95.0, bench.record, 0.0)
        assertEquals(7.5, bench.trend!!, 0.0)
        assertEquals(95.0, ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).maxMetric, 0.0)
    }

    @Test
    fun `pas de tendance avec un seul point, groupe vide sans exercice utilisé`() {
        val workouts = listOf(workout("2026-03-14T17:00:00.000Z", 80.0, ex("bench", set(5, 90.0))))
        assertNull(ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).top3.single().trend)
        val dos = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Dos, zone)
        assertTrue(dos.isEmpty)
        assertEquals(0.0, dos.maxMetric, 0.0)
        assertEquals(0.0, dos.maxVolume, 0.0)
    }

    @Test
    fun `le volume max est le plus gros volume d'une séance, séries validées du groupe seulement`() {
        val workouts = listOf(
            // 8 × 80 + 6 × 85 = 1150 ; la série non validée et le curl ne comptent pas.
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("bench", set(8, 80.0), set(6, 85.0), set(10, 100.0, completed = false)), ex("curl", set(10, 300.0))),
            // 10 × 60 + 10 × 50 = 1100.
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("incline", set(10, 60.0)), ex("press-perso", set(10, 50.0))),
        )
        assertEquals(1150.0, ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).maxVolume, 1e-9)
    }

    @Test
    fun `en cardio la métrique est la durée et le volume la distance`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("treadmill", cardio(20.0, 3.2), cardio(10.0, 1.5)), ex("rameur", cardio(15.0, 3.0))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("treadmill", cardio(40.0, 6.0, completed = false), cardio(25.0, 4.0))),
        )
        val progress = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Cardio, zone)
        val treadmill = progress.exercises.first { it.definition.id == "treadmill" }
        assertEquals(listOf(25.0, 30.0), treadmill.points.map { it.value })
        assertEquals(30.0, progress.maxMetric, 0.0)
        // 3,2 + 1,5 + 3,0 le 14 ; 4,0 le 12 (la série non validée ne compte pas).
        assertEquals(7.7, progress.maxVolume, 1e-9)
    }
}
