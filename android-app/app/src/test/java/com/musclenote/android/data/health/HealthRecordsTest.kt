package com.musclenote.android.data.health

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.progress.ProgressStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Conversion des séances et des pesées en enregistrements Health Connect (§ 15.3, D30). */
class HealthRecordsTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun workout(
        id: String = "w1",
        date: String = "2026-11-10T17:05:12.345Z",
        durationMinutes: Int? = 72,
        bodyWeight: Double = 78.5,
        preset: PresetId? = PresetId.Push,
        name: String = "Push",
    ) = Workout(id = id, name = name, preset = preset, date = date, bodyWeight = bodyWeight, exercises = emptyList(), durationMinutes = durationMinutes)

    private fun session(workouts: List<Workout>, id: String = "w1") =
        HealthRecords.all(workouts, emptyList(), paris).getValue(HealthRecords.sessionId(id)) as HealthRecordSpec.Session

    private fun weights(workouts: List<Workout>, weighIns: List<BodyWeightEntry> = emptyList()) =
        HealthRecords.all(workouts, weighIns, paris).values.filterIsInstance<HealthRecordSpec.Weight>()

    @Test
    fun `signalement - une séance et une pesée le même jour local ne donnent qu'un poids, celui de la pesée`() {
        // § 3.10 (Progrès) : la pesée manuelle remplace le poids de séance du jour.
        val records = HealthRecords.all(
            listOf(workout(date = "2026-11-10T17:05:12.345Z", bodyWeight = 78.5)),
            listOf(BodyWeightEntry("2026-11-10T11:00:00.000Z", 78.2)),
            paris,
        )
        val weights = records.values.filterIsInstance<HealthRecordSpec.Weight>()
        assertEquals(listOf(78.2), weights.map { it.kilograms })
    }

    @Test
    fun `une séance d'au moins 10 min garde sa durée, depuis son début`() {
        val session = session(listOf(workout()))
        assertEquals("musclenote-session-w1", session.clientRecordId)
        assertEquals(HealthRecordType.Session, session.type)
        assertEquals(Instant.parse("2026-11-10T17:05:12.345Z"), session.start)
        assertEquals(Instant.parse("2026-11-10T18:17:12.345Z"), session.end)
        assertEquals(ZoneOffset.ofHours(1), session.startOffset)
        assertEquals(ZoneOffset.ofHours(1), session.endOffset)
        assertEquals(PresetId.Push, session.preset)
        assertEquals("Push", session.name)
    }

    @Test
    fun `le décalage horaire suit le fuseau à chaque instant, passage à l'heure d'hiver compris`() {
        // 25 octobre 2026 : 3 h (heure d'été) → 2 h (heure d'hiver) à Paris.
        val session = session(listOf(workout(date = "2026-10-25T00:30:00.000Z", durationMinutes = 60)))
        assertEquals(ZoneOffset.ofHours(2), session.startOffset)
        assertEquals(ZoneOffset.ofHours(1), session.endOffset)
    }

    @Test
    fun `une séance courte finit quand elle a été terminée et dure la moyenne de son type (§ 3_7)`() {
        val history = listOf(
            workout("short", date = "2026-11-10T17:00:00.000Z", durationMinutes = 3),
            workout("a", date = "2026-11-07T17:00:00.000Z", durationMinutes = 60),
            workout("b", date = "2026-11-05T17:00:00.000Z", durationMinutes = 70),
            // Autre type : n'entre pas dans la moyenne des Push.
            workout("legs", date = "2026-11-03T17:00:00.000Z", durationMinutes = 120, preset = PresetId.Legs, name = "Legs"),
        )
        val short = session(history, "short")
        assertEquals(Instant.parse("2026-11-10T17:03:00Z"), short.end)
        assertEquals(Duration.ofMinutes(65), Duration.between(short.start, short.end))
    }

    @Test
    fun `sans moyenne de son type, une séance de 0 min dure 10 min et finit à sa date`() {
        val zero = session(listOf(workout(date = "2026-11-10T17:00:00.000Z", durationMinutes = 0)))
        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), zero.end)
        assertEquals(Instant.parse("2026-11-10T16:50:00Z"), zero.start)
    }

    @Test
    fun `une séance sans durée (finalisée après 24 h) est envoyée, depuis sa date, avec l'estimation`() {
        val history = listOf(
            workout("stale", date = "2026-11-10T17:00:00.000Z", durationMinutes = null),
            workout("a", date = "2026-11-07T17:00:00.000Z", durationMinutes = 50),
        )
        val stale = session(history, "stale")
        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), stale.start)
        assertEquals(Instant.parse("2026-11-10T17:50:00Z"), stale.end)

        val alone = session(listOf(workout(durationMinutes = null)))
        assertEquals(Duration.ofMinutes(10), Duration.between(alone.start, alone.end))
    }

    @Test
    fun `la fin est toujours après le début et jamais après la fin de la séance dans l'app`() {
        val date = Instant.parse("2026-11-10T17:00:00Z")
        for (minutes in listOf(0, 1, 9, 10, 95)) {
            val history = listOf(workout("x", date = date.toString(), durationMinutes = minutes), workout("a", date = "2026-11-01T17:00:00Z", durationMinutes = 80))
            val s = session(history, "x")
            assertTrue("$minutes min", s.end.isAfter(s.start))
            // Fin = moment où la séance a été terminée dans l'app (date + durée enregistrée).
            assertEquals("$minutes min", date.plus(Duration.ofMinutes(minutes.toLong())), s.end)
        }
    }

    @Test
    fun `un poids par jour local - la séance la plus récente du jour, puis la pesée qui la remplace`() {
        val morning = workout("m", date = "2026-11-10T06:30:00.000Z", bodyWeight = 78.0)
        val evening = workout("e", date = "2026-11-10T17:00:00.000Z", bodyWeight = 78.6)
        val otherDay = workout("o", date = "2026-11-08T17:00:00.000Z", bodyWeight = 79.0)

        val fromWorkouts = weights(listOf(evening, morning, otherDay))
        assertEquals(listOf("musclenote-weight-2026-11-08", "musclenote-weight-2026-11-10"), fromWorkouts.map { it.clientRecordId })
        val day = fromWorkouts.last()
        assertEquals(78.6, day.kilograms, 0.0)
        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), day.time)
        assertEquals(ZoneOffset.ofHours(1), day.offset)

        val withWeighIn = weights(listOf(evening, morning, otherDay), listOf(BodyWeightEntry("2026-11-10T11:00:00.000Z", 78.2)))
        val replaced = withWeighIn.single { it.clientRecordId == "musclenote-weight-2026-11-10" }
        assertEquals(78.2, replaced.kilograms, 0.0)
        assertEquals(Instant.parse("2026-11-10T11:00:00Z"), replaced.time)
        assertEquals(2, withWeighIn.size)
    }

    @Test
    fun `le jour d'un poids est le jour local du fuseau`() {
        // 23 h 30 UTC = 0 h 30 le lendemain à Paris.
        val late = weights(listOf(workout(date = "2026-11-10T23:30:00.000Z")))
        assertEquals(listOf("musclenote-weight-2026-11-11"), late.map { it.clientRecordId })
    }

    @Test
    fun `les poids envoyés sont ceux du graphique de Progrès, jour pour jour`() {
        val workouts = listOf(
            workout("a", date = "2026-11-10T17:00:00.000Z", bodyWeight = 78.6),
            workout("b", date = "2026-11-10T06:00:00.000Z", bodyWeight = 78.1),
            workout("c", date = "2026-11-08T17:00:00.000Z", bodyWeight = 0.0),
            workout("d", date = "2026-11-06T17:00:00.000Z", bodyWeight = 79.2),
        )
        val weighIns = listOf(BodyWeightEntry("2026-11-08T11:00:00.000Z", 78.9), BodyWeightEntry("2026-11-06T11:00:00.000Z", 79.0))
        val progress = ProgressStats.bodyWeightSeries(workouts, weighIns, paris).map { it.day to it.value }
        val sent = weights(workouts, weighIns).map { LocalDate.parse(it.clientRecordId.removePrefix(HealthRecords.WEIGHT_PREFIX)) to it.kilograms }
        assertEquals(progress, sent)
    }

    @Test
    fun `un poids inconnu ou hors limites n'est pas envoyé, une date illisible rien du tout`() {
        assertTrue(weights(listOf(workout(bodyWeight = 0.0))).isEmpty())
        assertTrue(weights(listOf(workout(bodyWeight = 1000.5))).isEmpty())
        assertEquals(1, weights(listOf(workout(bodyWeight = 1000.0))).size)
        assertTrue(HealthRecords.all(listOf(workout(date = "pas une date")), listOf(BodyWeightEntry("??", 78.0)), paris).isEmpty())
    }

    @Test
    fun `le type se lit dans l'identifiant, anciens identifiants de poids compris`() {
        assertEquals(HealthRecordType.Session, HealthRecords.typeOf(HealthRecords.sessionId("weight-x")))
        assertEquals(HealthRecordType.Weight, HealthRecords.typeOf(HealthRecords.weightId(LocalDate.of(2026, 11, 10))))
        assertEquals(HealthRecordType.Weight, HealthRecords.typeOf("musclenote-weight-workout-w1"))
        assertEquals(HealthRecordType.Weight, HealthRecords.typeOf("musclenote-weight-weighin-2026-11-09T11:00:00.000Z"))
        assertNull(HealthRecords.typeOf("autre-app-1"))
        assertEquals(
            mapOf(HealthRecordType.Session to 1, HealthRecordType.Weight to 2),
            HealthRecords.countByType(listOf("musclenote-session-a", "musclenote-weight-2026-11-10", "musclenote-weight-workout-b", "autre-app-1")),
        )
    }

    @Test
    fun `une séance produit sa séance et le poids de son jour`() {
        assertEquals(listOf("musclenote-session-w1", "musclenote-weight-2026-11-10"), HealthRecords.idsFor(workout(), paris))
        assertTrue(HealthRecords.idsFor(workout(date = "??"), paris).isEmpty())
    }

    @Test
    fun `l'empreinte change avec les champs envoyés, pas avec les exercices`() {
        fun fingerprints(w: Workout) = HealthRecords.fingerprints(listOf(w), emptyList(), paris)
        val base = fingerprints(workout())
        val sets = workout().copy(exercises = listOf(WorkoutExercise(exerciseId = "bench", sets = emptyList())))
        assertEquals(base, fingerprints(sets))
        val renamed = fingerprints(workout(preset = null, name = "Haut du corps"))
        assertNotEquals(base["musclenote-session-w1"], renamed["musclenote-session-w1"])
        assertEquals(base["musclenote-weight-2026-11-10"], renamed["musclenote-weight-2026-11-10"])
        val heavier = fingerprints(workout(bodyWeight = 79.0))
        assertEquals(base["musclenote-session-w1"], heavier["musclenote-session-w1"])
        assertNotEquals(base["musclenote-weight-2026-11-10"], heavier["musclenote-weight-2026-11-10"])
    }

    @Test
    fun `les changements sont les ajouts, les retraits et les empreintes modifiées`() {
        val before = HealthRecords.fingerprints(
            listOf(workout("a"), workout("b", date = "2026-11-08T17:00:00.000Z")),
            listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2)),
            paris,
        )
        val after = HealthRecords.fingerprints(
            listOf(workout("a", durationMinutes = 80), workout("c", date = "2026-11-06T17:00:00.000Z")),
            listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2)),
            paris,
        )
        val changed = HealthRecords.changedIds(before, after)
        assertEquals(
            setOf(
                "musclenote-session-a",
                "musclenote-session-c", "musclenote-weight-2026-11-06",
                "musclenote-session-b", "musclenote-weight-2026-11-08",
            ),
            changed,
        )
        assertTrue(HealthRecords.changedIds(after, after).isEmpty())
    }
}
