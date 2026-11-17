package com.musclenote.android.data.workout

import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.ExerciseType
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Résumé de fin de séance (SPEC § 15.12.3) : séance précédente du même programme, variation du
 * volume, records battus. Les historiques sont triés de la plus récente à la plus ancienne, comme
 * celui du dépôt.
 */
class WorkoutSummaryRulesTest {

    private val definitions = exerciseDefinitions(
        listOf(Exercise("perso", "Gainage lesté", MuscleGroup.Abdos), Exercise("tenue", "Tenue perso", MuscleGroup.Epaules, type = ExerciseType.Timed)),
    )
    private var nextId = 0

    private fun set(reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet("s${++nextId}", reps, weight, completed)

    private fun cardio(duration: Double, completed: Boolean = true) =
        WorkoutSet("c${++nextId}", 0, 0.0, completed, duration = duration, distance = 5.0, speed = 0.0, incline = 0.0)

    private fun ex(id: String, vararg sets: WorkoutSet) = WorkoutExercise(id, sets = sets.toList())

    private fun workout(
        day: String,
        vararg exercises: WorkoutExercise,
        preset: PresetId? = PresetId.Push,
        name: String = preset?.name ?: "",
        id: String = "w${++nextId}",
    ) = Workout(id, name, preset, "${day}T17:00:00.000Z", 80.0, exercises.toList(), 60)

    /** Une séance Push dont le volume vaut [volume] kg (une série validée de 1 × [volume]). */
    private fun push(day: String, volume: Double, id: String = "w${++nextId}") = workout(day, ex("bench", set(1, volume)), id = id)

    private fun records(workouts: List<Workout>, workoutId: String, exerciseId: String): Set<SummaryRecord> =
        WorkoutSummaryRules.summary(workouts, workoutId, definitions)!!.exercises.first { it.definition.id == exerciseId }.records

    // ─── Séance précédente du même programme et variation ─────────────────────────

    @Test
    fun `première séance du programme, pas de variation`() {
        val workouts = listOf(
            push("2026-11-12", 8240.0, id = "today"),
            workout("2026-11-10", ex("row", set(10, 60.0)), preset = PresetId.Pull),
        )
        val summary = WorkoutSummaryRules.summary(workouts, "today", definitions)!!
        assertNull(summary.comparison)
        assertEquals(8240L, summary.volumeKg)
    }

    @Test
    fun `une séance d'un autre programme, même plus récente, est ignorée`() {
        val previousPush = push("2026-11-05", 7960.0, id = "previous")
        val workouts = listOf(
            push("2026-11-12", 8240.0, id = "today"),
            workout("2026-11-10", ex("row", set(10, 90.0)), preset = PresetId.Pull),
            workout("2026-11-08", ex("squat", set(10, 100.0)), preset = PresetId.Legs),
            previousPush,
            push("2026-11-01", 7000.0),
        )
        val comparison = WorkoutSummaryRules.summary(workouts, "today", definitions)!!.comparison!!
        assertSame(previousPush, comparison.previous)
        assertEquals(280L, comparison.variationKg)
    }

    @Test
    fun `même volume au kg près, variation nulle (identique)`() {
        val workouts = listOf(push("2026-11-12", 8240.4, id = "today"), push("2026-11-05", 8239.6))
        assertEquals(0L, WorkoutSummaryRules.summary(workouts, "today", definitions)!!.comparison!!.variationKg)
    }

    @Test
    fun `baisse de volume, variation négative`() {
        val workouts = listOf(push("2026-11-12", 7800.0, id = "today"), push("2026-11-05", 7950.0))
        assertEquals(-150L, WorkoutSummaryRules.summary(workouts, "today", definitions)!!.comparison!!.variationKg)
    }

    @Test
    fun `le programme d'une séance perso est son nom normalisé`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(10, 50.0)), preset = null, name = "Haut du corps", id = "today"),
            workout("2026-11-10", ex("bench", set(10, 80.0)), preset = null, name = "Bras"),
            workout("2026-11-05", ex("bench", set(10, 40.0)), preset = null, name = " haut du CORPS ", id = "previous"),
        )
        val comparison = WorkoutSummaryRules.summary(workouts, "today", definitions)!!.comparison!!
        assertEquals("previous", comparison.previous.id)
        assertEquals(100L, comparison.variationKg)
    }

    @Test
    fun `le résumé d'une ancienne séance ignore les séances plus récentes`() {
        val workouts = listOf(push("2026-11-12", 9000.0), push("2026-11-05", 8000.0, id = "old"), push("2026-11-01", 7500.0))
        assertEquals(500L, WorkoutSummaryRules.summary(workouts, "old", definitions)!!.comparison!!.variationKg)
    }

    @Test
    fun `seules les séries validées comptent dans le volume`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(10, 80.0), set(10, 80.0, completed = false)), id = "today"),
            workout("2026-11-05", ex("bench", set(10, 80.0), set(10, 100.0, completed = false))),
        )
        val summary = WorkoutSummaryRules.summary(workouts, "today", definitions)!!
        assertEquals(800L, summary.volumeKg)
        assertEquals(1, summary.completedSets)
        assertEquals(0L, summary.comparison!!.variationKg)
    }

    @Test
    fun `séance introuvable, pas de résumé`() {
        assertNull(WorkoutSummaryRules.summary(listOf(push("2026-11-12", 100.0)), "absente", definitions))
    }

    // ─── Records battus ──────────────────────────────────────────────────────────

    @Test
    fun `première fois qu'on fait l'exercice, pas de record`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(8, 100.0)), id = "today"),
            workout("2026-11-05", ex("row", set(8, 80.0))),
            // L'exercice était dans cette séance, mais sans série validée : ce n'est pas une fois.
            workout("2026-11-01", ex("bench", set(8, 60.0, completed = false))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(workouts, "today", "bench"))
    }

    @Test
    fun `égalité de charge et de 1RM, pas de record`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(8, 80.0)), id = "today"),
            workout("2026-11-05", ex("bench", set(8, 80.0), set(6, 75.0))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(workouts, "today", "bench"))
    }

    @Test
    fun `charge max dépassée, record personnel et 1RM`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(8, 82.5)), id = "today"),
            workout("2026-11-05", ex("bench", set(8, 80.0))),
            workout("2026-10-29", ex("bench", set(5, 80.0))),
        )
        assertEquals(setOf(SummaryRecord.Personal, SummaryRecord.OneRepMax), records(workouts, "today", "bench"))
    }

    @Test
    fun `plus de reps à la même charge, 1RM seulement`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(10, 80.0)), id = "today"), // 1RM 107
            workout("2026-11-05", ex("bench", set(8, 80.0))), // 1RM 101
        )
        assertEquals(setOf(SummaryRecord.OneRepMax), records(workouts, "today", "bench"))
    }

    @Test
    fun `charge record mais 1RM égal à un ancien, record personnel seulement`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(1, 101.0)), id = "today"), // 1RM 101
            workout("2026-11-05", ex("bench", set(8, 80.0))), // 1RM round(101,33) = 101
        )
        assertEquals(setOf(SummaryRecord.Personal), records(workouts, "today", "bench"))
    }

    @Test
    fun `séances plus récentes ignorées, le record reste celui du jour`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(8, 90.0))),
            workout("2026-11-05", ex("bench", set(8, 85.0)), id = "old"),
            workout("2026-10-29", ex("bench", set(8, 80.0))),
        )
        assertEquals(setOf(SummaryRecord.Personal, SummaryRecord.OneRepMax), records(workouts, "old", "bench"))
    }

    @Test
    fun `seules les séries validées comptent pour les records`() {
        val workouts = listOf(
            // La série à 100 kg n'est pas validée : la meilleure du jour est à 80 kg.
            workout("2026-11-12", ex("bench", set(8, 80.0), set(8, 100.0, completed = false)), id = "today"),
            // Une ancienne série non validée plus lourde n'est pas un record à battre.
            workout("2026-11-05", ex("bench", set(8, 75.0), set(8, 120.0, completed = false))),
        )
        assertEquals(setOf(SummaryRecord.Personal, SummaryRecord.OneRepMax), records(workouts, "today", "bench"))
    }

    @Test
    fun `au poids du corps, le record personnel se compte en reps`() {
        val workouts = listOf(
            workout("2026-11-12", ex("pullup", set(12, 0.0)), id = "today"),
            workout("2026-11-05", ex("pullup", set(10, 0.0))),
        )
        // 1RM nul à 0 kg : jamais un record.
        assertEquals(setOf(SummaryRecord.Personal), records(workouts, "today", "pullup"))
    }

    @Test
    fun `au poids du corps, mêmes reps, pas de record`() {
        val workouts = listOf(
            workout("2026-11-12", ex("pullup", set(10, 0.0)), id = "today"),
            workout("2026-11-05", ex("pullup", set(10, 0.0))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(workouts, "today", "pullup"))
    }

    @Test
    fun `un premier lest est un record de charge et de 1RM`() {
        val workouts = listOf(
            workout("2026-11-12", ex("pullup", set(6, 5.0)), id = "today"),
            workout("2026-11-05", ex("pullup", set(12, 0.0))),
        )
        assertEquals(setOf(SummaryRecord.Personal, SummaryRecord.OneRepMax), records(workouts, "today", "pullup"))
    }

    @Test
    fun `en cardio, la séance la plus longue est un record, sans 1RM`() {
        val workouts = listOf(
            workout("2026-11-12", ex("treadmill", cardio(20.0), cardio(15.0)), id = "today"),
            workout("2026-11-05", ex("treadmill", cardio(30.0))),
            workout("2026-10-29", ex("treadmill", cardio(35.0, completed = false))),
        )
        assertEquals(setOf(SummaryRecord.Personal), records(workouts, "today", "treadmill"))
    }

    @Test
    fun `en cardio, même durée totale, pas de record (sans erreur d'arrondi)`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bike", cardio(10.1), cardio(20.2)), id = "today"), // 30,299999…
            workout("2026-11-05", ex("bike", cardio(30.3))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(workouts, "today", "bike"))
    }

    // ─── Exercices du résumé ─────────────────────────────────────────────────────

    @Test
    fun `exercices validés seulement, dans l'ordre de la séance, inconnus masqués`() {
        val workouts = listOf(
            workout(
                "2026-11-12",
                ex("squat", set(5, 120.0)),
                ex("inconnu", set(10, 50.0)),
                ex("bench", set(8, 80.0, completed = false)),
                ex("perso", set(10, 10.0)),
                ex("curl", set(10, 15.0)),
                id = "today",
            ),
            workout("2026-11-05", ex("squat", set(5, 110.0)), ex("curl", set(10, 15.0))),
        )
        val summary = WorkoutSummaryRules.summary(workouts, "today", definitions)!!
        assertEquals(listOf("squat", "perso", "curl"), summary.exercises.map { it.definition.id })
        // Squat battu ; exercice perso fait pour la première fois ; curl égal.
        assertEquals(1, summary.recordCount)
        assertTrue(summary.exercises.first().brokeRecord)
    }

    @Test
    fun `meilleure série et 1RM de l'exercice, sur les séries validées`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(10, 80.0), set(8, 82.5), set(6, 82.5), set(3, 90.0, completed = false)), id = "today"),
        )
        val bench = WorkoutSummaryRules.summary(workouts, "today", definitions)!!.exercises.single()
        assertEquals(8, bench.bestSet!!.reps)
        assertEquals(82.5, bench.bestSet!!.weight, 0.0)
        assertEquals(107.0, bench.bestOneRepMax, 0.0) // 80 × (1 + 10/30) = 106,67 → 107
        assertEquals(3, bench.sets.size)
    }

    // ─── Exercices au temps (§ 15.18.6 – 15.18.7, 🔄 D44) ─────────────────────────

    private fun hold(seconds: Int?, weight: Double = 0.0, completed: Boolean = true) =
        WorkoutSet("h${++nextId}", 0, weight, completed, durationSeconds = seconds)

    @Test
    fun `au temps, une durée max plus longue est un record, sans 1RM`() {
        val workouts = listOf(
            workout("2026-11-12", ex("plank", hold(60, weight = 20.0), hold(95)), id = "today"),
            workout("2026-11-05", ex("plank", hold(90), hold(90))),
            workout("2026-10-29", ex("plank", hold(120, completed = false))),
        )
        assertEquals(setOf(SummaryRecord.Personal), records(workouts, "today", "plank"))
        val plank = WorkoutSummaryRules.summary(workouts, "today", definitions)!!.exercises.single()
        assertEquals(0.0, plank.bestOneRepMax, 0.0)
        assertEquals(95, plank.bestSet!!.durationSeconds)
    }

    @Test
    fun `au temps, égalité ou première fois, pas de record, une vieille séance en reps ne compte pas`() {
        val tie = listOf(
            workout("2026-11-12", ex("plank", hold(90)), id = "today"),
            workout("2026-11-05", ex("plank", hold(90))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(tie, "today", "plank"))
        val first = listOf(
            workout("2026-11-12", ex("plank", hold(90)), id = "today"),
            // Ancienne séance de « Gainage » en reps : rien à battre.
            workout("2026-11-05", ex("plank", set(60, 0.0))),
        )
        assertEquals(emptySet<SummaryRecord>(), records(first, "today", "plank"))
        // Un exercice perso au temps suit la même règle.
        val custom = listOf(
            workout("2026-11-12", ex("tenue", hold(40, weight = 5.0)), id = "today"),
            workout("2026-11-05", ex("tenue", hold(30, weight = 10.0))),
        )
        assertEquals(setOf(SummaryRecord.Personal), records(custom, "today", "tenue"))
    }

    @Test
    fun `temps sous tension et volume sans les séries au temps`() {
        val workouts = listOf(
            workout("2026-11-12", ex("bench", set(10, 80.0)), ex("plank", hold(60, weight = 10.0), hold(45), hold(30, completed = false)), ex("wallsit", hold(120)), id = "today"),
        )
        val summary = WorkoutSummaryRules.summary(workouts, "today", definitions)!!
        assertEquals(800L, summary.volumeKg)
        assertEquals(225, summary.tensionSeconds)
        assertEquals(0, WorkoutSummaryRules.summary(listOf(push("2026-11-12", 100.0, id = "p")), "p", definitions)!!.tensionSeconds)
    }
}
