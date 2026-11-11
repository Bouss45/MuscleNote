package com.musclenote.android.data.progress

import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.isDayKey
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Valeur d'un jour local (point des graphiques Poids corps, Calories, Eau). */
data class DayValue(val day: LocalDate, val value: Double)

/** Stats du poids de corps (§ 2.10) : dernier point, évolution au dixième, min et max. */
data class BodyWeightStats(val current: Double, val change: Double, val min: Double, val max: Double)

/**
 * Stats d'un journal quotidien (calories en kcal, eau en ml, § 3.10). [average7] est `null` si
 * aucun jour de la fenêtre des 7 derniers jours n'a été saisi (affiché « — »).
 */
data class DailyStats(
    val today: Int,
    val average7: Double?,
    val goalDaysHit: Int,
    val best: Int,
    val daysTracked: Int,
)

/** Un point d'un exercice : une séance où il a au moins une série validée. */
data class SessionPoint(val date: Instant, val value: Double)

/**
 * Progression d'un exercice (§ 3.10) : ses séances dans l'ordre chronologique, avec la métrique
 * par séance (poids max, ou durée totale en cardio).
 */
data class ExerciseProgress(val definition: ExerciseDefinition, val points: List<SessionPoint>) {
    val sessionCount: Int get() = points.size

    /** Meilleure métrique (0 sans point). */
    val record: Double get() = points.maxOfOrNull { it.value } ?: 0.0

    /** Dernier point − premier, au dixième ; `null` avec moins de 2 points. */
    val trend: Double? get() = if (points.size < 2) null else ProgressStats.roundTenth(points.last().value - points.first().value)
}

/**
 * Onglet d'un groupe musculaire (§ 3.10). [exercises] : exercices du groupe utilisés au moins une
 * fois, dans l'ordre du catalogue puis des perso ; [top3] : les plus utilisés.
 */
data class MuscleGroupProgress(
    val sessions: Int,
    val exercises: List<ExerciseProgress>,
    val top3: List<ExerciseProgress>,
    /** « Charge max » (kg) ou « Durée max » (min) : meilleur record du top 3. */
    val maxMetric: Double,
    /** « Vol. max » : max par séance de Σ reps × poids (kg) ; en cardio, de Σ distance (km). */
    val maxVolume: Double,
) {
    val isEmpty: Boolean get() = exercises.isEmpty()
}

/**
 * Calculs de l'onglet Progrès (SPEC § 3.10), en fonctions pures testables sur la JVM. Les séances
 * passées en paramètre sont triées de la plus récente à la plus ancienne (invariant du dépôt) ;
 * seules les **séries validées** comptent (🔄 D15). Dates interprétées dans le fuseau [ZoneId]
 * donné (§ 10.2).
 */
object ProgressStats {
    /** Taille de la fenêtre calendaire de la moyenne (aujourd'hui compris). */
    const val AVERAGE_DAYS = 7L

    /** Nombre d'exercices du classement « Exercices les plus utilisés ». */
    const val TOP_COUNT = 3

    /** Arrondi au dixième, demis vers le haut (comme `Math.round`, § 3.5). */
    fun roundTenth(value: Double): Double = (value * 10).roundToLong() / 10.0

    // ─── Poids de corps ─────────────────────────────────────────────────────────────

    /**
     * Un point par **jour local** : pour chaque jour, la séance la plus récente avec un poids non
     * nul ; puis chaque pesée manuelle écrase la valeur de son jour (la plus récente si deux
     * pesées tombent le même jour). Tri par jour croissant.
     */
    fun bodyWeightSeries(workouts: List<Workout>, bodyWeights: List<BodyWeightEntry>, zone: ZoneId): List<DayValue> {
        val byDay = HashMap<LocalDate, Double>()
        for (workout in workouts) {
            if (workout.bodyWeight == 0.0) continue
            val day = localDay(workout.date, zone) ?: continue
            // Séances de la plus récente à la plus ancienne : la première du jour gagne.
            byDay.putIfAbsent(day, workout.bodyWeight)
        }
        bodyWeights
            .mapNotNull { entry -> parseInstantOrNull(entry.date, zone)?.let { it to entry } }
            .sortedBy { it.first }
            .forEach { (instant, entry) -> byDay[instant.atZone(zone).toLocalDate()] = entry.weight }
        return byDay.entries.sortedBy { it.key }.map { DayValue(it.key, it.value) }
    }

    /** Actuel (dernier point), évolution (dernier − premier, au dixième), min et max ; `null` sans point. */
    fun bodyWeightStats(series: List<DayValue>): BodyWeightStats? {
        if (series.isEmpty()) return null
        val values = series.map { it.value }
        return BodyWeightStats(
            current = values.last(),
            change = roundTenth(values.last() - values.first()),
            min = values.min(),
            max = values.max(),
        )
    }

    // ─── Calories et eau ──────────────────────────────────────────────────────────────

    /** Un point par jour dont la valeur est > 0 (clés invalides ignorées), tri croissant. */
    fun dailySeries(log: Map<String, Int>): List<DayValue> =
        log.entries
            .filter { (day, value) -> value > 0 && isDayKey(day) }
            .map { (day, value) -> DayValue(LocalDate.parse(day), value.toDouble()) }
            .sortedBy { it.day }

    /**
     * Stats d'un journal (§ 3.10, 🔄 D15) ; `null` s'il n'a aucun jour saisi.
     * - moyenne des valeurs > 0 de la fenêtre calendaire [aujourd'hui − 6 j ; aujourd'hui] (les
     *   jours non saisis sont ignorés, pas comptés à 0) ;
     * - jours objectif atteint : valeur ≥ l'objectif **en vigueur ce jour-là** ([DailyTracker.goalFor]),
     *   ou l'objectif actuel [currentGoal] si l'historique n'en donne pas.
     */
    fun dailyStats(
        log: Map<String, Int>,
        goalHistory: Map<String, Int>,
        currentGoal: Int,
        today: LocalDate,
    ): DailyStats? {
        val series = dailySeries(log)
        if (series.isEmpty()) return null
        val windowStart = today.minusDays(AVERAGE_DAYS - 1)
        val window = series.filter { it.day in windowStart..today }
        return DailyStats(
            today = log[today.toString()]?.coerceAtLeast(0) ?: 0,
            average7 = if (window.isEmpty()) null else window.map { it.value }.average(),
            goalDaysHit = series.count { point ->
                val goal = DailyTracker.goalFor(goalHistory, point.day.toString()) ?: currentGoal
                point.value >= goal
            },
            best = series.maxOf { it.value }.toInt(),
            daysTracked = series.size,
        )
    }

    // ─── Groupes musculaires ──────────────────────────────────────────────────────────

    /**
     * Badges des onglets de groupe : nombre de séances contenant au moins un exercice du groupe
     * avec au moins une série validée. Les exercices à la définition introuvable sont ignorés.
     */
    fun muscleSessionCounts(workouts: List<Workout>, definitions: List<ExerciseDefinition>): Map<MuscleGroup, Int> {
        val groupOf = definitions.associate { it.id to it.muscleGroup }
        val counts = MuscleGroup.entries.associateWithTo(LinkedHashMap()) { 0 }
        for (workout in workouts) {
            workout.exercises
                .filter { e -> e.sets.any { it.completed } }
                .mapNotNullTo(HashSet()) { groupOf[it.exerciseId] }
                .forEach { counts[it] = counts.getValue(it) + 1 }
        }
        return counts
    }

    /**
     * Onglet du groupe [group] (§ 3.10). [definitions] dans l'ordre d'affichage (catalogue puis
     * perso, archivés compris) : il départage les exercices à égalité dans le top 3.
     */
    fun muscleGroupProgress(
        workouts: List<Workout>,
        definitions: List<ExerciseDefinition>,
        group: MuscleGroup,
        zone: ZoneId,
    ): MuscleGroupProgress {
        val isCardio = group == MuscleGroup.Cardio
        val inGroup = definitions.filter { it.muscleGroup == group }
        val exercises = inGroup.mapNotNull { definition ->
            val points = WorkoutStats.exerciseHistory(workouts, definition.id, zone)
                .asReversed()
                .map { SessionPoint(it.date, it.metric(isCardio)) }
            if (points.isEmpty()) null else ExerciseProgress(definition, points)
        }
        // Tri stable : à égalité, l'ordre du catalogue puis des perso est conservé.
        val top3 = exercises.sortedByDescending { it.sessionCount }.take(TOP_COUNT)

        val groupIds = inGroup.mapTo(HashSet()) { it.id }
        val maxVolume = workouts.maxOfOrNull { workout ->
            workout.exercises
                .filter { it.exerciseId in groupIds }
                .sumOf { e ->
                    e.sets.filter { it.completed }.sumOf { set -> if (isCardio) set.distance ?: 0.0 else set.reps * set.weight }
                }
        } ?: 0.0

        return MuscleGroupProgress(
            sessions = muscleSessionCounts(workouts, definitions).getValue(group),
            exercises = exercises,
            top3 = top3,
            maxMetric = top3.maxOfOrNull { it.record } ?: 0.0,
            maxVolume = maxVolume,
        )
    }

    private fun localDay(date: String, zone: ZoneId): LocalDate? = parseInstantOrNull(date, zone)?.atZone(zone)?.toLocalDate()
}
