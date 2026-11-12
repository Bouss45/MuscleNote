package com.musclenote.android.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import kotlin.reflect.KClass

/**
 * Passage des enregistrements de MuscleNote aux classes de Health Connect (SPEC § 15.3) :
 * permissions, type de séance, métadonnées de saisie manuelle avec `clientRecordId` stable.
 */
object HealthConnectRecords {
    val SESSION_PERMISSION: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val WEIGHT_PERMISSION: String = HealthPermission.getWritePermission(WeightRecord::class)

    /** Les deux permissions demandées ensemble par l'interrupteur et [Autoriser]. */
    val PERMISSIONS: Set<String> = setOf(SESSION_PERMISSION, WEIGHT_PERMISSION)

    fun typesOf(grantedPermissions: Set<String>): Set<HealthRecordType> = buildSet {
        if (SESSION_PERMISSION in grantedPermissions) add(HealthRecordType.Session)
        if (WEIGHT_PERMISSION in grantedPermissions) add(HealthRecordType.Weight)
    }

    fun recordClass(type: HealthRecordType): KClass<out Record> = when (type) {
        HealthRecordType.Session -> ExerciseSessionRecord::class
        HealthRecordType.Weight -> WeightRecord::class
    }

    /** Enregistrement Health Connect de [spec] ; [title] = nom affiché d'une séance. */
    fun toRecord(spec: HealthRecordSpec, version: Long, title: (HealthRecordSpec.Session) -> String): Record {
        val metadata = Metadata.manualEntry(clientRecordId = spec.clientRecordId, clientRecordVersion = version)
        return when (spec) {
            is HealthRecordSpec.Session -> ExerciseSessionRecord(
                startTime = spec.start,
                startZoneOffset = spec.startOffset,
                endTime = spec.end,
                endZoneOffset = spec.endOffset,
                metadata = metadata,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = title(spec),
                exerciseRoute = null,
            )
            is HealthRecordSpec.Weight -> WeightRecord(
                time = spec.time,
                zoneOffset = spec.offset,
                weight = Mass.kilograms(spec.kilograms),
                metadata = metadata,
            )
        }
    }
}
