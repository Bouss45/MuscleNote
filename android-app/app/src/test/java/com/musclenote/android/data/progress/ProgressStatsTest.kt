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
    fun `exercices triés par date de dernière séance, égalités au nombre de séances puis au catalogue`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("press-perso", set(10, 50.0)), ex("dips", set(10, 0.0))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("press-perso", set(10, 45.0)), ex("fly", set(12, 14.0))),
            workout("2026-03-10T17:00:00.000Z", 80.0, ex("fly", set(12, 12.0)), ex("dips", set(8, 0.0)), ex("ancien", set(5, 30.0))),
            workout("2026-03-08T17:00:00.000Z", 80.0, ex("bench", set(5, 90.0)), ex("incline", set(5, 60.0))),
            workout("2026-03-06T17:00:00.000Z", 80.0, ex("incline", set(5, 55.0))),
        )
        val progress = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone)
        // dips et press-perso le 14 (2 séances chacun : le catalogue d'abord), fly le 12, l'archivé
        // le 10, puis incline (2 séances) avant bench (1) le 8.
        assertEquals(listOf("dips", "press-perso", "fly", "ancien", "incline", "bench"), progress.exercises.map { it.definition.id })
        assertEquals(5, progress.sessions)
    }

    @Test
    fun `points chronologiques, dernière valeur, record, tendance et charge max`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("bench", set(5, 92.5), set(3, 100.0, completed = false))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("bench", set(5, 95.0))),
            workout("2026-03-10T17:00:00.000Z", 80.0, ex("bench", set(8, 80.0), set(6, 85.0)), ex("fly", set(12, 20.0))),
        )
        val progress = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone)
        val bench = progress.exercises.first { it.definition.id == "bench" }
        assertEquals(ExerciseMetric.Load, bench.metric)
        assertEquals(listOf(85.0, 95.0, 92.5), bench.points.map { it.value })
        assertEquals(92.5, bench.points.last().value, 0.0)
        assertEquals(95.0, bench.record, 0.0)
        assertEquals(7.5, bench.trend!!, 0.0)
        // Charge max : le meilleur record de tous les exercices du groupe.
        assertEquals(95.0, progress.maxMetric!!, 0.0)
        assertEquals(listOf(day("2026-03-10"), day("2026-03-12"), day("2026-03-14")), bench.points.map { it.day })
    }

    @Test
    fun `pas de tendance avec un seul point, groupe vide sans exercice utilisé`() {
        val workouts = listOf(workout("2026-03-14T17:00:00.000Z", 80.0, ex("bench", set(5, 90.0))))
        assertNull(ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).exercises.single().trend)
        val dos = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Dos, zone)
        assertTrue(dos.isEmpty)
        assertNull(dos.maxMetric)
        assertEquals(0.0, dos.maxVolume, 0.0)
    }

    @Test
    fun `au poids du corps, la métrique est le nombre de reps max d'une série`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("dips", set(12, 0.0), set(15, 0.0), set(20, 0.0, completed = false))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("dips", set(10, 0.0), set(8, 0.0)), ex("fly", set(12, 14.0))),
        )
        val progress = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone)
        val dips = progress.exercises.first { it.definition.id == "dips" }
        assertEquals(ExerciseMetric.Reps, dips.metric)
        assertEquals(listOf(10.0, 15.0), dips.points.map { it.value })
        assertEquals(15.0, dips.record, 0.0)
        // Les reps n'entrent pas dans la charge max du groupe.
        assertEquals(14.0, progress.maxMetric!!, 0.0)
    }

    @Test
    fun `avec du lest, la métrique devient la charge ajoutée, 0 kg pour une séance sans lest`() {
        val workouts = listOf(
            workout("2026-03-14T17:00:00.000Z", 80.0, ex("dips", set(8, 10.0), set(10, 0.0))),
            workout("2026-03-12T17:00:00.000Z", 80.0, ex("dips", set(12, 0.0))),
        )
        val dips = ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).exercises.single()
        assertEquals(ExerciseMetric.Load, dips.metric)
        assertEquals(listOf(0.0, 10.0), dips.points.map { it.value })
    }

    @Test
    fun `groupe dont tous les exercices sont au poids du corps, pas de charge max`() {
        val workouts = listOf(workout("2026-03-14T17:00:00.000Z", 80.0, ex("dips", set(12, 0.0))))
        assertNull(ProgressStats.muscleGroupProgress(workouts, definitions, MuscleGroup.Pecs, zone).maxMetric)
    }

    @Test
    fun `métrique d'un exercice selon son historique`() {
        fun session(vararg sets: WorkoutSet) =
            com.musclenote.android.data.workout.ExerciseSession("w", java.time.Instant.EPOCH, 80.0, sets.toList())
        assertEquals(ExerciseMetric.Duration, ProgressStats.exerciseMetric(isCardio = true, history = listOf(session(cardio(20.0, 3.0)))))
        assertEquals(ExerciseMetric.Reps, ProgressStats.exerciseMetric(isCardio = false, history = listOf(session(set(10, 0.0)), session(set(12, 0.0)))))
        assertEquals(ExerciseMetric.Load, ProgressStats.exerciseMetric(isCardio = false, history = listOf(session(set(10, 0.0)), session(set(5, 2.5)))))
        assertEquals(ExerciseMetric.Load, ProgressStats.exerciseMetric(isCardio = false, history = emptyList()))
    }

    @Test
    fun `meilleure série, la plus lourde, puis le plus de reps, puis la première`() {
        val heavy = set(5, 100.0)
        assertEquals(heavy, ProgressStats.bestSet(listOf(set(8, 90.0), heavy, set(3, 95.0)), isCardio = false))
        val moreReps = set(8, 80.0)
        assertEquals(moreReps, ProgressStats.bestSet(listOf(set(6, 80.0), moreReps, set(8, 80.0)), isCardio = false))
        val bodyweight = set(15, 0.0)
        assertEquals(bodyweight, ProgressStats.bestSet(listOf(set(12, 0.0), bodyweight), isCardio = false))
        assertNull(ProgressStats.bestSet(emptyList(), isCardio = false))
    }

    @Test
    fun `meilleure série en cardio, la plus longue, puis la plus longue distance`() {
        val longest = cardio(30.0, 5.0)
        assertEquals(longest, ProgressStats.bestSet(listOf(cardio(20.0, 6.0), longest, cardio(30.0, 4.0)), isCardio = true))
        val farther = cardio(30.0, 5.2)
        assertEquals(farther, ProgressStats.bestSet(listOf(cardio(30.0, 5.0), farther), isCardio = true))
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
        assertEquals(ExerciseMetric.Duration, treadmill.metric)
        assertEquals(30.0, progress.maxMetric!!, 0.0)
        // 3,2 + 1,5 + 3,0 le 14 ; 4,0 le 12 (la série non validée ne compte pas).
        assertEquals(7.7, progress.maxVolume, 1e-9)
    }
}
