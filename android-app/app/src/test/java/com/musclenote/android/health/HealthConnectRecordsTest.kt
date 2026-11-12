package com.musclenote.android.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Séance et pesée → enregistrements du SDK Health Connect (§ 15.3). */
class HealthConnectRecordsTest {

    private val paris = ZoneId.of("Europe/Paris")

    private val workout = Workout(
        id = "w1",
        name = "Haut du corps",
        date = "2026-11-10T17:05:12.345Z",
        bodyWeight = 78.5,
        exercises = emptyList(),
        durationMinutes = 72,
    )

    @Test
    fun `une séance devient une séance de musculation en saisie manuelle`() {
        val spec = HealthRecords.forWorkout(workout, paris).first { it is HealthRecordSpec.Session }
        val record = HealthConnectRecords.toRecord(spec, version = 1_762_800_000_000) { "Titre ${it.name}" } as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals(Instant.parse("2026-11-10T17:05:12.345Z"), record.startTime)
        assertEquals(Instant.parse("2026-11-10T18:17:12.345Z"), record.endTime)
        assertEquals(ZoneOffset.ofHours(1), record.startZoneOffset)
        assertEquals(ZoneOffset.ofHours(1), record.endZoneOffset)
        assertEquals("Titre Haut du corps", record.title)
        assertNull(record.notes)
        assertEquals(emptyList<Any>(), record.segments)
        assertEquals("musclenote-session-w1", record.metadata.clientRecordId)
        assertEquals(1_762_800_000_000, record.metadata.clientRecordVersion)
        assertEquals(Metadata.RECORDING_METHOD_MANUAL_ENTRY, record.metadata.recordingMethod)
    }

    @Test
    fun `le poids d'une séance et une pesée deviennent des mesures de poids`() {
        val fromWorkout = HealthRecords.forWorkout(workout, paris).first { it is HealthRecordSpec.Weight }
        val record = HealthConnectRecords.toRecord(fromWorkout, version = 7) { error("pas de titre pour un poids") } as WeightRecord
        assertEquals(Instant.parse("2026-11-10T17:05:12.345Z"), record.time)
        assertEquals(ZoneOffset.ofHours(1), record.zoneOffset)
        assertEquals(78.5, record.weight.inKilograms, 1e-9)
        assertEquals("musclenote-weight-workout-w1", record.metadata.clientRecordId)
        assertEquals(7L, record.metadata.clientRecordVersion)
        assertEquals(Metadata.RECORDING_METHOD_MANUAL_ENTRY, record.metadata.recordingMethod)

        val weighIn = HealthRecords.forWeighIn(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2), paris)!!
        val weighInRecord = HealthConnectRecords.toRecord(weighIn, version = 8) { "" } as WeightRecord
        assertEquals(Instant.parse("2026-11-09T11:00:00Z"), weighInRecord.time)
        assertEquals(78.2, weighInRecord.weight.inKilograms, 1e-9)
        assertEquals("musclenote-weight-weighin-2026-11-09T11:00:00.000Z", weighInRecord.metadata.clientRecordId)
    }

    @Test
    fun `les permissions d'écriture correspondent aux types`() {
        assertEquals("android.permission.health.WRITE_EXERCISE", HealthConnectRecords.SESSION_PERMISSION)
        assertEquals("android.permission.health.WRITE_WEIGHT", HealthConnectRecords.WEIGHT_PERMISSION)
        assertEquals(setOf(HealthRecordType.Session, HealthRecordType.Weight), HealthConnectRecords.typesOf(HealthConnectRecords.PERMISSIONS))
        assertEquals(setOf(HealthRecordType.Weight), HealthConnectRecords.typesOf(setOf("android.permission.health.WRITE_WEIGHT", "autre")))
        assertEquals(emptySet<HealthRecordType>(), HealthConnectRecords.typesOf(emptySet()))
        assertEquals(ExerciseSessionRecord::class, HealthConnectRecords.recordClass(HealthRecordType.Session))
        assertEquals(WeightRecord::class, HealthConnectRecords.recordClass(HealthRecordType.Weight))
    }
}
