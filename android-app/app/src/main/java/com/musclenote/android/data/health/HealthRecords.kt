package com.musclenote.android.data.health

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

    /** Poids de corps d'un jour local, relevé à [time]. */
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
 * Conversion des séances et des pesées en enregistrements Health Connect (SPEC § 15.3, 🔄 D30),
 * en fonctions pures testables sur la JVM.
 */
object HealthRecords {
    const val SESSION_PREFIX = "musclenote-session-"

    /**
     * Préfixe de tous les poids : `musclenote-weight-{AAAA-MM-JJ}` aujourd'hui, et les anciens
     * `musclenote-weight-workout-{id}` / `musclenote-weight-weighin-{date}` (v1.0.5), supprimés
     * au premier envoi qui suit.
     */
    const val WEIGHT_PREFIX = "musclenote-weight-"

    private const val LEGACY_WORKOUT_WEIGHT_PREFIX = "${WEIGHT_PREFIX}workout-"
    private const val LEGACY_WEIGH_IN_PREFIX = "${WEIGHT_PREFIX}weighin-"

    /** Limite haute de Health Connect pour un poids. */
    const val MAX_WEIGHT_KG = 1000.0

    fun sessionId(workoutId: String) = SESSION_PREFIX + workoutId

    /** Le poids du jour local [day] : un seul enregistrement par jour. */
    fun weightId(day: LocalDate) = WEIGHT_PREFIX + day

    /** Type d'après l'identifiant ; `null` pour un identifiant qui n'est pas de MuscleNote. */
    fun typeOf(clientRecordId: String): HealthRecordType? = when {
        clientRecordId.startsWith(SESSION_PREFIX) -> HealthRecordType.Session
        clientRecordId.startsWith(WEIGHT_PREFIX) -> HealthRecordType.Weight
        else -> null
    }

    private fun isValidWeight(kg: Double) = kg > 0 && kg <= MAX_WEIGHT_KG

    /**
     * Séance terminée → séance de musculation, ou `null` si sa date est illisible. Durée :
     * - au moins [WorkoutStats.MIN_GYM_MINUTES] min → la sienne, depuis le début de la séance ;
     * - plus courte ou inconnue → l'estimation du temps en salle (§ 3.7 : moyenne des séances
     *   d'au moins 10 min de même clé, dans [averageByKey]), sinon
     *   [WorkoutStats.MIN_GYM_MINUTES] min. Une séance courte a été saisie après coup : elle finit
     *   au moment où elle a été terminée dans l'app et commence l'estimation plus tôt ; une séance
     *   sans durée (finalisée après 24 h, § 3.4) commence à sa date.
     *
     * La fin est donc toujours après le début, et jamais après le moment où la séance a été
     * terminée dans l'app.
     */
    fun sessionFor(workout: Workout, zone: ZoneId, averageByKey: Map<String, Int>): HealthRecordSpec.Session? {
        val date = parseInstantOrNull(workout.date, zone) ?: return null
        val recorded = workout.durationMinutes
        val estimate = Duration.ofMinutes((WorkoutStats.countedMinutes(workout, averageByKey) ?: WorkoutStats.MIN_GYM_MINUTES).toLong())
        val (start, end) = when {
            recorded != null && recorded >= WorkoutStats.MIN_GYM_MINUTES -> date to date.plus(estimate)
            recorded != null -> date.plus(Duration.ofMinutes(recorded.toLong())).let { finished -> finished.minus(estimate) to finished }
            else -> date to date.plus(estimate)
        }
        return HealthRecordSpec.Session(
            clientRecordId = sessionId(workout.id),
            start = start,
            startOffset = zone.rules.getOffset(start),
            end = end,
            endOffset = zone.rules.getOffset(end),
            preset = workout.preset,
            name = workout.name,
            fingerprint = "$start|$end|${workout.preset?.id.orEmpty()}|${workout.name}",
        )
    }

    /**
     * Un poids par jour local, avec la règle de Progrès (§ 3.10) : la pesée du jour, sinon le poids
     * saisi au début de la séance la plus récente du jour. Hors de ]0 ; 1000] kg → rien ce jour-là.
     */
    fun dailyWeights(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): List<HealthRecordSpec.Weight> =
        BodyWeightRules.dailyWeights(workouts, weighIns, zone)
            .filter { isValidWeight(it.weight) }
            .map { daily ->
                HealthRecordSpec.Weight(
                    clientRecordId = weightId(daily.day),
                    time = daily.time,
                    offset = zone.rules.getOffset(daily.time),
                    kilograms = daily.weight,
                    fingerprint = "${daily.time}|${daily.weight}",
                )
            }

    /** Tous les enregistrements attendus dans Health Connect, par identifiant. */
    fun all(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): Map<String, HealthRecordSpec> {
        val averageByKey = WorkoutStats.averageMinutesByKey(workouts)
        val records = LinkedHashMap<String, HealthRecordSpec>()
        for (workout in workouts) sessionFor(workout, zone, averageByKey)?.let { records[it.clientRecordId] = it }
        for (weight in dailyWeights(workouts, weighIns, zone)) records[weight.clientRecordId] = weight
        return records
    }

    /**
     * Identifiants qu'une séance peut produire : sa séance et le poids de son jour (finalisation au
     * chargement, § 3.4, qui précède la détection des changements).
     */
    fun idsFor(workout: Workout, zone: ZoneId): List<String> {
        val day = parseInstantOrNull(workout.date, zone)?.atZone(zone)?.toLocalDate() ?: return emptyList()
        return listOf(sessionId(workout.id), weightId(day))
    }

    /** Empreintes par identifiant : ce que compare la détection des changements. */
    fun fingerprints(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): Map<String, String> =
        all(workouts, weighIns, zone).mapValues { it.value.fingerprint }

    /**
     * Poids envoyés par la v1.0.5 sous un ancien identifiant (un par séance et un par pesée) →
     * identifiants des poids du jour qui les remplacent. Le jour se lit dans l'empreinte de
     * l'époque, `{instant}|{kg}`. L'ancien enregistrement est supprimé parce que sa source a
     * disparu ; celui du jour est écrit dans le même envoi.
     */
    fun legacyWeightReplacements(sent: Map<String, String>, zone: ZoneId): Set<String> =
        sent.filterKeys { it.startsWith(LEGACY_WORKOUT_WEIGHT_PREFIX) || it.startsWith(LEGACY_WEIGH_IN_PREFIX) }
            .values
            .mapNotNull { fingerprint -> parseInstantOrNull(fingerprint.substringBefore('|'), zone)?.atZone(zone)?.toLocalDate() }
            .mapTo(LinkedHashSet(), ::weightId)

    /** Nombre d'identifiants par type (ceux d'autres apps sont ignorés). */
    fun countByType(clientRecordIds: Collection<String>): Map<HealthRecordType, Int> =
        clientRecordIds.mapNotNull { typeOf(it) }.groupingBy { it }.eachCount()

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
