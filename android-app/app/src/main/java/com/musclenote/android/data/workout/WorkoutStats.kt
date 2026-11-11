package com.musclenote.android.data.workout

import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Case du mini-calendrier de la série hebdomadaire (§ 3.6). */
enum class WeekStatus { Validated, Partial, None }

/** Une semaine du mini-calendrier : son lundi, son nombre de séances et son statut. */
data class WeekCell(val monday: LocalDate, val count: Int, val status: WeekStatus, val isCurrent: Boolean)

/**
 * Série hebdomadaire (§ 3.6) : semaines validées consécutives ([current]), meilleure série
 * ([best]), série encore en vie ([isAlive]) et les 8 dernières semaines, de la plus ancienne à la
 * semaine courante.
 */
data class Streak(val current: Int, val best: Int, val isAlive: Boolean, val lastWeeks: List<WeekCell>) {
    companion object {
        /** Aucune séance : carte masquée. */
        val NONE = Streak(current = 0, best = 0, isAlive = false, lastWeeks = emptyList())
    }
}

/** Temps passé à la salle (§ 3.7) : total et moyenne en minutes, sur [sessionCount] séances retenues. */
data class GymTime(val totalMinutes: Int, val averageMinutes: Int, val sessionCount: Int)

/** Une séance de l'historique d'un exercice (§ 2.8), avec **seulement** ses séries validées. */
data class ExerciseSession(val workoutId: String, val date: Instant, val bodyWeight: Double, val sets: List<WorkoutSet>) {
    /** Métrique de la séance (§ 2.8, § 3.10) : poids max, ou somme des durées (min) en cardio. */
    fun metric(isCardio: Boolean): Double = if (isCardio) totalDuration else maxWeight

    val maxWeight: Double get() = sets.maxOfOrNull { it.weight } ?: 0.0
    val totalDuration: Double get() = sets.sumOf { it.duration ?: 0.0 }
    val volume: Double get() = WorkoutStats.volume(sets)
    val bestOneRepMax: Double get() = WorkoutStats.bestOneRepMax(sets)
}

/**
 * Statistiques des séances terminées (SPEC § 2.2, § 2.8, § 3.5 – 3.8), en fonctions pures
 * testables sur la JVM. Seules les **séries validées** comptent (§ 3.16). Les historiques passés
 * en paramètre sont triés de la séance la plus récente à la plus ancienne (invariant du dépôt).
 */
object WorkoutStats {
    /** Une semaine est validée à partir de 3 séances (§ 3.6). */
    const val VALIDATED_WEEK_SESSIONS = 3

    /** Semaines du mini-calendrier de l'accueil. */
    const val CALENDAR_WEEKS = 8

    /** En dessous, la durée d'une séance est remplacée par la moyenne de sa clé (§ 3.7). */
    const val MIN_GYM_MINUTES = 10

    // ─── § 3.5 : 1RM estimé (Epley) ──────────────────────────────────────────────

    /**
     * 1RM estimé (kg) : 0 si poids ou reps ≤ 0, le poids lui-même pour 1 rep, sinon
     * `round(poids × (1 + reps / 30))`. `roundToLong` arrondit les demis vers +∞, comme
     * `Math.round` en JS (`kotlin.math.round` les arrondirait au pair).
     */
    fun estimateOneRepMax(weight: Double, reps: Int): Double = when {
        weight <= 0 || reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> (weight * (1 + reps / 30.0)).roundToLong().toDouble()
    }

    /** Meilleur 1RM estimé des séries **validées** (0 s'il n'y en a pas). */
    fun bestOneRepMax(sets: List<WorkoutSet>): Double =
        sets.filter { it.completed }.maxOfOrNull { estimateOneRepMax(it.weight, it.reps) }?.coerceAtLeast(0.0) ?: 0.0

    // ─── § 3.8 : volume et compteurs ──────────────────────────────────────────────

    /** Σ reps × poids des séries **validées**, en kg (le cardio, à 0 × 0, vaut 0). */
    fun volume(sets: List<WorkoutSet>): Double = sets.filter { it.completed }.sumOf { it.reps * it.weight }

    fun volume(workout: Workout): Double = workout.exercises.sumOf { volume(it.sets) }

    /** Nombre de séries validées de la séance. */
    fun completedSetCount(workout: Workout): Int = workout.exercises.sumOf { e -> e.sets.count { it.completed } }

    /** Nombre d'exercices ayant au moins une série validée. */
    fun exercisesWithCompletedSets(workout: Workout): Int = workout.exercises.count { e -> e.sets.any { it.completed } }

    // ─── § 3.6 : série hebdomadaire ───────────────────────────────────────────────

    /**
     * Lundi de la semaine ISO de [date] (lundi → dimanche). Équivaut à la clé `"AAAA-SS"`
     * (`IsoFields.WEEK_BASED_YEAR` + `WEEK_OF_WEEK_BASED_YEAR`) : deux dates ont la même clé ssi
     * elles ont le même lundi, et les lundis se comparent et se décalent directement.
     */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Série hebdomadaire au jour [today] (§ 3.6). Chaque séance compte dans la semaine de sa date
     * **locale** ([zone]) ; une semaine est validée avec au moins 3 séances. En remontant depuis la
     * semaine courante (jamais pénalisée), une semaine non validée isolée est tolérée, deux
     * consécutives cassent la série.
     */
    fun streak(workouts: List<Workout>, today: LocalDate, zone: ZoneId): Streak {
        val counts = HashMap<LocalDate, Int>()
        for (workout in workouts) {
            val start = parseInstantOrNull(workout.date, zone) ?: continue
            counts.merge(weekStart(start.atZone(zone).toLocalDate()), 1, Int::plus)
        }
        if (counts.isEmpty()) return Streak.NONE

        fun validated(week: LocalDate) = (counts[week] ?: 0) >= VALIDATED_WEEK_SESSIONS
        val currentWeek = weekStart(today)
        val earliest = counts.keys.min()

        // Série actuelle : de la semaine courante vers la plus ancienne semaine ayant une séance.
        var current = 0
        var misses = 0
        var isAlive = true
        var week = currentWeek
        while (week >= earliest) {
            if (validated(week)) {
                current++
                misses = 0
            } else if (week != currentWeek) {
                misses++
                if (misses > 1) {
                    isAlive = false
                    break
                }
            }
            week = week.minusWeeks(1)
        }

        // Meilleure série : de la plus ancienne semaine à celle qui précède la semaine courante.
        var best = 0
        var run = 0
        misses = 0
        week = earliest
        while (week < currentWeek) {
            if (validated(week)) {
                run++
                misses = 0
                best = max(best, run)
            } else {
                misses++
                if (misses > 1) {
                    run = 0
                    misses = 0
                }
            }
            week = week.plusWeeks(1)
        }
        best = max(best, current)

        val lastWeeks = (CALENDAR_WEEKS - 1 downTo 0).map { weeksAgo ->
            val monday = currentWeek.minusWeeks(weeksAgo.toLong())
            val count = counts[monday] ?: 0
            val isCurrent = weeksAgo == 0
            val status = when {
                count >= VALIDATED_WEEK_SESSIONS -> WeekStatus.Validated
                // Semaine en cours pas encore validée : « aucune » (le nombre reste affiché).
                isCurrent -> WeekStatus.None
                count > 0 -> WeekStatus.Partial
                else -> WeekStatus.None
            }
            WeekCell(monday, count, status, isCurrent)
        }
        return Streak(current, best, isAlive, lastWeeks)
    }

    // ─── § 3.7 : temps passé à la salle ─────────────────────────────────────────────

    /**
     * Temps en salle (§ 3.7, 🔄 D3/D15) : une séance de moins de 10 min (ou sans durée, comme une
     * séance finalisée automatiquement) prend la durée moyenne des séances d'au moins 10 min de
     * même clé (§ 3.1) ; sans moyenne, elle est ignorée.
     */
    fun gymTime(workouts: List<Workout>): GymTime {
        val averageByKey = workouts
            .mapNotNull { w -> w.durationMinutes?.takeIf { it >= MIN_GYM_MINUTES }?.let { WorkoutRules.sessionKey(w) to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, durations) -> durations.average().roundToInt() }

        var total = 0
        var count = 0
        for (workout in workouts) {
            val minutes = workout.durationMinutes ?: 0
            val counted = if (minutes >= MIN_GYM_MINUTES) minutes else averageByKey[WorkoutRules.sessionKey(workout)]
            if (counted != null) {
                total += counted
                count++
            }
        }
        val average = if (count > 0) (total.toDouble() / count).roundToInt() else 0
        return GymTime(total, average, count)
    }

    /** Durée en minutes : `45m`, `1h`, `1h 5m` (§ 3.7). */
    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    // ─── § 2.8 : historique d'un exercice ──────────────────────────────────────────

    /**
     * Séances terminées où l'exercice a au moins une série validée, de la plus récente à la plus
     * ancienne, avec leurs seules séries validées (🔄 D15). Une séance à la date illisible est
     * ignorée (elle ne peut être ni datée ni placée sur le graphique).
     */
    fun exerciseHistory(workouts: List<Workout>, exerciseId: String, zone: ZoneId): List<ExerciseSession> =
        workouts.mapNotNull { workout ->
            val sets = workout.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets.orEmpty().filter { it.completed }
            if (sets.isEmpty()) return@mapNotNull null
            val date = parseInstantOrNull(workout.date, zone) ?: return@mapNotNull null
            ExerciseSession(workout.id, date, workout.bodyWeight, sets)
        }
}
