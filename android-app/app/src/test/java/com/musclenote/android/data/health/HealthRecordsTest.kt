package com.musclenote.android.data.health

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Conversion des séances et des pesées en enregistrements Health Connect (§ 15.3). */
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

    @Test
    fun `une séance terminée donne une séance et son poids`() {
        val records = HealthRecords.forWorkout(workout(), paris)
        assertEquals(2, records.size)
        val session = records[0] as HealthRecordSpec.Session
        assertEquals("musclenote-session-w1", session.clientRecordId)
        assertEquals(HealthRecordType.Session, session.type)
        assertEquals(Instant.parse("2026-11-10T17:05:12.345Z"), session.start)
        assertEquals(Instant.parse("2026-11-10T18:17:12.345Z"), session.end)
        assertEquals(ZoneOffset.ofHours(1), session.startOffset)
        assertEquals(ZoneOffset.ofHours(1), session.endOffset)
        assertEquals(PresetId.Push, session.preset)
        assertEquals("Push", session.name)

        val weight = records[1] as HealthRecordSpec.Weight
        assertEquals("musclenote-weight-workout-w1", weight.clientRecordId)
        assertEquals(HealthRecordType.Weight, weight.type)
        assertEquals(session.start, weight.time)
        assertEquals(ZoneOffset.ofHours(1), weight.offset)
        assertEquals(78.5, weight.kilograms, 0.0)
    }

    @Test
    fun `le décalage horaire suit le fuseau à chaque instant, passage à l'heure d'hiver compris`() {
        // 25 octobre 2026 : 3 h (heure d'été) → 2 h (heure d'hiver) à Paris.
        val session = HealthRecords.forWorkout(workout(date = "2026-10-25T00:30:00.000Z", durationMinutes = 60), paris)[0] as HealthRecordSpec.Session
        assertEquals(ZoneOffset.ofHours(2), session.startOffset)
        assertEquals(ZoneOffset.ofHours(1), session.endOffset)
    }

    @Test
    fun `une séance de 0 minute dure 1 minute, sans durée elle n'est pas envoyée`() {
        val zero = HealthRecords.forWorkout(workout(durationMinutes = 0), paris)[0] as HealthRecordSpec.Session
        assertEquals(60, zero.end.epochSecond - zero.start.epochSecond)

        val stale = HealthRecords.forWorkout(workout(durationMinutes = null), paris)
        assertEquals(listOf("musclenote-weight-workout-w1"), stale.map { it.clientRecordId })
    }

    @Test
    fun `un poids inconnu ou hors limites n'est pas envoyé, une date illisible rien du tout`() {
        assertEquals(listOf("musclenote-session-w1"), HealthRecords.forWorkout(workout(bodyWeight = 0.0), paris).map { it.clientRecordId })
        assertEquals(listOf("musclenote-session-w1"), HealthRecords.forWorkout(workout(bodyWeight = 1000.5), paris).map { it.clientRecordId })
        assertEquals(2, HealthRecords.forWorkout(workout(bodyWeight = 1000.0), paris).size)
        assertEquals(emptyList<HealthRecordSpec>(), HealthRecords.forWorkout(workout(date = "pas une date"), paris))
    }

    @Test
    fun `une pesée donne un poids à midi local, identifié par sa date`() {
        val weight = HealthRecords.forWeighIn(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2), paris)!!
        assertEquals("musclenote-weight-weighin-2026-11-09T11:00:00.000Z", weight.clientRecordId)
        assertEquals(Instant.parse("2026-11-09T11:00:00Z"), weight.time)
        assertEquals(ZoneOffset.ofHours(1), weight.offset)
        assertEquals(78.2, weight.kilograms, 0.0)
        assertNull(HealthRecords.forWeighIn(BodyWeightEntry("2026-11-09T11:00:00.000Z", 0.0), paris))
        assertNull(HealthRecords.forWeighIn(BodyWeightEntry("??", 78.0), paris))
    }

    @Test
    fun `le type se lit dans l'identifiant`() {
        assertEquals(HealthRecordType.Session, HealthRecords.typeOf(HealthRecords.sessionId("weight-x")))
        assertEquals(HealthRecordType.Weight, HealthRecords.typeOf(HealthRecords.workoutWeightId("w1")))
        assertEquals(HealthRecordType.Weight, HealthRecords.typeOf(HealthRecords.weighInId("2026-11-09T11:00:00.000Z")))
        assertNull(HealthRecords.typeOf("autre-app-1"))
    }

    @Test
    fun `l'empreinte change avec les champs envoyés, pas avec les exercices`() {
        fun fingerprints(w: Workout) = HealthRecords.forWorkout(w, paris).associate { it.clientRecordId to it.fingerprint }
        val base = fingerprints(workout())
        val sets = workout().copy(exercises = listOf(com.musclenote.android.data.model.WorkoutExercise(exerciseId = "bench", sets = emptyList())))
        assertEquals(base, fingerprints(sets))
        val renamed = fingerprints(workout(preset = null, name = "Haut du corps"))
        assertNotEquals(base["musclenote-session-w1"], renamed["musclenote-session-w1"])
        assertEquals(base["musclenote-weight-workout-w1"], renamed["musclenote-weight-workout-w1"])
        val heavier = fingerprints(workout(bodyWeight = 79.0))
        assertEquals(base["musclenote-session-w1"], heavier["musclenote-session-w1"])
        assertNotEquals(base["musclenote-weight-workout-w1"], heavier["musclenote-weight-workout-w1"])
    }

    @Test
    fun `les changements sont les ajouts, les retraits et les empreintes modifiées`() {
        val before = HealthRecords.fingerprints(
            listOf(workout("a"), workout("b")),
            listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2)),
            paris,
        )
        val after = HealthRecords.fingerprints(
            listOf(workout("a", durationMinutes = 80), workout("c")),
            listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2)),
            paris,
        )
        val changed = HealthRecords.changedIds(before, after)
        assertEquals(
            setOf(
                "musclenote-session-a",
                "musclenote-session-c", "musclenote-weight-workout-c",
                "musclenote-session-b", "musclenote-weight-workout-b",
            ),
            changed,
        )
        assertTrue(HealthRecords.changedIds(after, after).isEmpty())
    }
}
