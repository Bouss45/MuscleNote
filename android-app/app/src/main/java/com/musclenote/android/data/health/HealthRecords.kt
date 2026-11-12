package com.musclenote.android.data.health

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Type d'enregistrement Health Connect, chacun avec sa permission d'écriture (§ 15.3). */
enum class HealthRecordType { Session, Weight }

/**
 * Enregistrement à écrire dans Health Connect (SPEC § 15.3, 🔄 D29), sans dépendre du SDK : la
 * conversion vers les classes de Health Connect se fait au moment de l'écriture.
 * [clientRecordId] est stable : le réécrire remplace l'enregistrement, le supprimer le retire.
 * [fingerprint] résume les champs source : il change quand l'enregistrement doit être renvoyé.
 */
sealed interface HealthRecordSpec {
    val clientRecordId: String
    val type: HealthRecordType
    val fingerprint: String

    /**
     * Séance de musculation. Le titre (nom affiché, § 3.1) est calculé à l'écriture dans la langue
     * de l'app, d'après [preset], [name] et [start] : il ne fait pas partie de l'empreinte.
     */
    data class Session(
        override val clientRecordId: String,
        val start: Instant,
        val startOffset: ZoneOffset,
        val end: Instant,
        val endOffset: ZoneOffset,
        val preset: PresetId?,
        val name: String,
        override val fingerprint: String,
    ) : HealthRecordSpec {
        override val type get() = HealthRecordType.Session
    }

    /** Poids de corps à un instant. */
    data class Weight(
        override val clientRecordId: String,
        val time: Instant,
        val offset: ZoneOffset,
        val kilograms: Double,
        override val fingerprint: String,
    ) : HealthRecordSpec {
        override val type get() = HealthRecordType.Weight
    }
}

/**
 * Conversion des séances et des pesées en enregistrements Health Connect (SPEC § 15.3), en
 * fonctions pures testables sur la JVM.
 */
object HealthRecords {
    const val SESSION_PREFIX = "musclenote-session-"
    const val WEIGHT_PREFIX = "musclenote-weight-"
    private const val WORKOUT_WEIGHT_PREFIX = "${WEIGHT_PREFIX}workout-"
    private const val WEIGH_IN_PREFIX = "${WEIGHT_PREFIX}weighin-"

    /** Limite haute de Health Connect pour un poids. */
    const val MAX_WEIGHT_KG = 1000.0

    fun sessionId(workoutId: String) = SESSION_PREFIX + workoutId

    fun workoutWeightId(workoutId: String) = WORKOUT_WEIGHT_PREFIX + workoutId

    fun weighInId(date: String) = WEIGH_IN_PREFIX + date

    /** Type d'après l'identifiant ; `null` pour un identifiant qui n'est pas de MuscleNote. */
    fun typeOf(clientRecordId: String): HealthRecordType? = when {
        clientRecordId.startsWith(SESSION_PREFIX) -> HealthRecordType.Session
        clientRecordId.startsWith(WEIGHT_PREFIX) -> HealthRecordType.Weight
        else -> null
    }

    private fun isValidWeight(kg: Double) = kg > 0 && kg <= MAX_WEIGHT_KG

    /**
     * Enregistrements d'une séance terminée : la séance elle-même si sa durée est connue (fin =
     * début + durée, au moins 1 min), et le poids saisi au début (> 0). Date illisible → rien.
     */
    fun forWorkout(workout: Workout, zone: ZoneId): List<HealthRecordSpec> {
        val start = parseInstantOrNull(workout.date, zone) ?: return emptyList()
        val records = ArrayList<HealthRecordSpec>(2)
        val minutes = workout.durationMinutes
        if (minutes != null) {
            val end = start.plusSeconds(60L * minutes.coerceAtLeast(1))
            records += HealthRecordSpec.Session(
                clientRecordId = sessionId(workout.id),
                start = start,
                startOffset = zone.rules.getOffset(start),
                end = end,
                endOffset = zone.rules.getOffset(end),
                preset = workout.preset,
                name = workout.name,
                fingerprint = "$start|$minutes|${workout.preset?.id.orEmpty()}|${workout.name}",
            )
        }
        if (isValidWeight(workout.bodyWeight)) {
            records += HealthRecordSpec.Weight(
                clientRecordId = workoutWeightId(workout.id),
                time = start,
                offset = zone.rules.getOffset(start),
                kilograms = workout.bodyWeight,
                fingerprint = "$start|${workout.bodyWeight}",
            )
        }
        return records
    }

    /** Pesée manuelle (§ 3.11), à son instant (midi local du jour). Date illisible → `null`. */
    fun forWeighIn(entry: BodyWeightEntry, zone: ZoneId): HealthRecordSpec.Weight? {
        val time = parseInstantOrNull(entry.date, zone) ?: return null
        if (!isValidWeight(entry.weight)) return null
        return HealthRecordSpec.Weight(
            clientRecordId = weighInId(entry.date),
            time = time,
            offset = zone.rules.getOffset(time),
            kilograms = entry.weight,
            fingerprint = "$time|${entry.weight}",
        )
    }

    /** Tous les enregistrements attendus dans Health Connect, par identifiant. */
    fun all(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): Map<String, HealthRecordSpec> {
        val records = LinkedHashMap<String, HealthRecordSpec>()
        for (workout in workouts) for (record in forWorkout(workout, zone)) records[record.clientRecordId] = record
        for (entry in weighIns) forWeighIn(entry, zone)?.let { records[it.clientRecordId] = it }
        return records
    }

    /** Empreintes par identifiant : ce que compare la détection des changements. */
    fun fingerprints(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): Map<String, String> =
        all(workouts, weighIns, zone).mapValues { it.value.fingerprint }

    /**
     * Identifiants à renvoyer ou à supprimer entre deux états : ajoutés, retirés, ou dont
     * l'empreinte a changé.
     */
    fun changedIds(before: Map<String, String>, after: Map<String, String>): Set<String> {
        val changed = LinkedHashSet<String>()
        for ((id, fingerprint) in after) if (before[id] != fingerprint) changed += id
        for (id in before.keys) if (id !in after) changed += id
        return changed
    }
}
