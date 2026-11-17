package com.musclenote.android.data.progress

import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.util.isDayKey
import com.musclenote.android.data.workout.ExerciseSession
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

/**
 * Métrique d'une séance d'un exercice (SPEC § 15.9.3, 🔄 D34) : charge max (kg), reps max au poids
 * du corps, durée totale en cardio (min), ou durée max d'une série au temps (s, § 15.18.6, 🔄 D44).
 */
enum class ExerciseMetric { Load, Reps, Duration, Hold }

/**
 * Un point d'un exercice : une séance où il a au moins une série validée — son jour local, sa
 * position sur une courbe ([x] : jour local depuis l'époque, heure en fraction) et sa métrique.
 */
data class SessionPoint(val workoutId: String, val date: Instant, val day: LocalDate, val x: Double, val value: Double)

/**
 * Progression d'un exercice (§ 3.10, § 15.9.3) : ses séances dans l'ordre chronologique, avec la
 * métrique [metric] par séance.
 */
data class ExerciseProgress(val definition: ExerciseDefinition, val metric: ExerciseMetric, val points: List<SessionPoint>) {
    val sessionCount: Int get() = points.size

    /** Meilleure métrique (0 sans point). */
    val record: Double get() = points.maxOfOrNull { it.value } ?: 0.0

    /** Dernier point − premier, au dixième ; `null` avec moins de 2 points. */
    val trend: Double? get() = if (points.size < 2) null else ProgressStats.roundTenth(points.last().value - points.first().value)
}

/**
 * Section « Exercices » d'un groupe musculaire (§ 3.10, § 15.9.2). [exercises] : exercices du groupe
 * utilisés au moins une fois, la dernière séance la plus récente d'abord.
 */
data class MuscleGroupProgress(
    val sessions: Int,
    val exercises: List<ExerciseProgress>,
    /**
     * « Charge max » (kg, records des exercices comptés en charge : ni reps ni durée d'une série au
     * temps) ou « Durée max » (min) ; `null` si aucun exercice du groupe n'a de charge.
     */
    val maxMetric: Double?,
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

    /** Arrondi au dixième, demis vers le haut (comme `Math.round`, § 3.5). */
    fun roundTenth(value: Double): Double = (value * 10).roundToLong() / 10.0

    // ─── Poids de corps ─────────────────────────────────────────────────────────────

    /**
     * Un point par **jour local** : pour chaque jour, la séance la plus récente avec un poids non
     * nul ; puis chaque pesée manuelle écrase la valeur de son jour (la plus récente si deux
     * pesées tombent le même jour). Tri par jour croissant.
     */
    fun bodyWeightSeries(workouts: List<Workout>, bodyWeights: List<BodyWeightEntry>, zone: ZoneId): List<DayValue> =
        BodyWeightRules.dailyWeights(workouts, bodyWeights, zone).map { DayValue(it.day, it.weight) }

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

    // ─── Exercices ───────────────────────────────────────────────────────────────────

    /**
     * Métrique d'un exercice d'après son [history] (§ 15.9.3) : durée en cardio ; durée max au temps
     * (§ 15.18.6) ; sinon charge max dès qu'une série validée a une charge > 0 (le lest d'un exercice
     * au poids du corps), reps max si toutes sont à 0 kg. Sans historique : charge.
     */
    fun exerciseMetric(kind: SetKind, history: List<ExerciseSession>): ExerciseMetric =
        metricOfSessions(kind, history.map { it.sets })

    /**
     * La règle de [exerciseMetric] sur les séries validées de chaque séance ([sessions]) : aussi pour
     * les records du résumé de séance (§ 15.12.3), qui ne gardent que les séances plus anciennes.
     */
    fun metricOfSessions(kind: SetKind, sessions: List<List<WorkoutSet>>): ExerciseMetric = when {
        kind == SetKind.Cardio -> ExerciseMetric.Duration
        kind == SetKind.Timed -> ExerciseMetric.Hold
        sessions.isNotEmpty() && sessions.all { sets -> sets.all { it.weight <= 0.0 } } -> ExerciseMetric.Reps
        else -> ExerciseMetric.Load
    }

    /** Valeur de [metric] pour une séance (ses séries sont déjà les séries validées). */
    fun metricValue(session: ExerciseSession, metric: ExerciseMetric): Double = metricValue(session.sets, metric)

    /** Valeur de [metric] pour les séries validées [sets] d'une séance (0 sans série). */
    fun metricValue(sets: List<WorkoutSet>, metric: ExerciseMetric): Double = when (metric) {
        ExerciseMetric.Load -> sets.maxOfOrNull { it.weight } ?: 0.0
        ExerciseMetric.Reps -> (sets.maxOfOrNull { it.reps } ?: 0).toDouble()
        ExerciseMetric.Duration -> sets.sumOf { it.duration ?: 0.0 }
        // Une série sans durée (ancienne série en reps) n'entre pas dans la durée max (§ 15.18.6).
        ExerciseMetric.Hold -> (sets.mapNotNull { it.durationSeconds }.maxOrNull() ?: 0).toDouble()
    }

    /**
     * Points d'un exercice, de la séance la plus ancienne à la plus récente ([history] est trié de la
     * plus récente à la plus ancienne, comme [WorkoutStats.exerciseHistory]).
     */
    fun exerciseSeries(history: List<ExerciseSession>, metric: ExerciseMetric, zone: ZoneId): List<SessionPoint> =
        history.asReversed().map { session ->
            val local = session.date.atZone(zone)
            val day = local.toLocalDate()
            SessionPoint(
                workoutId = session.workoutId,
                date = session.date,
                day = day,
                x = day.toEpochDay() + local.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY,
                value = metricValue(session, metric),
            )
        }

    /**
     * Meilleure série d'une séance (§ 15.9.1) parmi [sets] (validées) : la plus lourde, à égalité le
     * plus de reps, puis la première ; en cardio, la plus longue, puis la plus longue distance ; au
     * temps (§ 15.18.6), la plus longue, puis la plus lourde (une série sans durée passe après).
     */
    fun bestSet(sets: List<WorkoutSet>, kind: SetKind): WorkoutSet? {
        val order = when (kind) {
            SetKind.Cardio -> compareBy<WorkoutSet>({ it.duration ?: 0.0 }, { it.distance ?: 0.0 })
            SetKind.Timed -> compareBy<WorkoutSet>({ it.durationSeconds ?: -1 }, { it.weight })
            SetKind.Strength -> compareBy<WorkoutSet>({ it.weight }, { it.reps })
        }
        // maxWithOrNull garde le premier des éléments égaux.
        return sets.maxWithOrNull(order)
    }

    private const val SECONDS_PER_DAY = 86_400.0

    /**
     * Section « Exercices » du groupe [group] (§ 3.10, § 15.9.2). [definitions] dans l'ordre
     * d'affichage (catalogue puis perso, archivés compris) : il départage les exercices à égalité.
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
            val history = WorkoutStats.exerciseHistory(workouts, definition.id, zone, timed = definition.isTimed)
            if (history.isEmpty()) return@mapNotNull null
            val metric = exerciseMetric(definition.kind, history)
            ExerciseProgress(definition, metric, exerciseSeries(history, metric, zone))
        }
            // Tri stable : à égalité de date et de nombre de séances, l'ordre du catalogue puis des perso.
            .sortedWith(compareByDescending<ExerciseProgress> { it.points.last().date }.thenByDescending { it.sessionCount })

        val groupIds = inGroup.mapTo(HashSet()) { it.id }
        val maxVolume = workouts.maxOfOrNull { workout ->
            workout.exercises
                .filter { it.exerciseId in groupIds }
                .sumOf { e ->
                    // Les séries au temps n'ont pas de volume (§ 15.18.7).
                    if (isCardio) e.sets.filter { it.completed }.sumOf { it.distance ?: 0.0 } else WorkoutStats.volume(e.sets)
                }
        } ?: 0.0

        return MuscleGroupProgress(
            sessions = muscleSessionCounts(workouts, definitions).getValue(group),
            exercises = exercises,
            maxMetric = exercises.filter { it.metric == ExerciseMetric.Load || it.metric == ExerciseMetric.Duration }.maxOfOrNull { it.record },
            maxVolume = maxVolume,
        )
    }
}
