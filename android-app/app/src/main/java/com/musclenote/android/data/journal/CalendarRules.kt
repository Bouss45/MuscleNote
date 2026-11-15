package com.musclenote.android.data.journal

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.data.util.isDayKey
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Un mois du calendrier (semaine du lundi) : [leadingBlanks] cases vides avant le 1er, puis ses
 * [days]. [cells] = les cases rangée par rangée, `null` pour une case vide.
 */
data class MonthGrid(val month: YearMonth, val leadingBlanks: Int, val days: List<LocalDate>) {
    /** Rangées de 7 cases : 4 à 6. */
    val rows: Int get() = (leadingBlanks + days.size + 6) / 7

    val cells: List<LocalDate?>
        get() = List(leadingBlanks) { null } + days + List(rows * 7 - leadingBlanks - days.size) { null }
}

/** Poids d'une journée : la pesée ([fromWorkout] `null`), sinon le poids saisi au début de [fromWorkout]. */
data class DayWeight(val kg: Double, val fromWorkout: Workout?)

/** Complément pris un jour, avec son nombre de doses. */
data class SupplementIntake(val supplement: Supplement, val doses: Int)

/**
 * Ce qui a été noté le jour [day] (sheet « Journée », § 15.15.2) ; chaque partie vide est masquée.
 * [calories] / [waterMl] : 0 = rien noté ; objectifs en vigueur ce jour-là.
 */
data class DayJournal(
    val day: LocalDate,
    val sessions: List<SessionSummary>,
    val weight: DayWeight?,
    val supplements: List<SupplementIntake>,
    val calories: Int,
    val calorieGoal: Int,
    val waterMl: Int,
    val waterGoal: Int,
) {
    val isEmpty: Boolean
        get() = sessions.isEmpty() && weight == null && supplements.isEmpty() && calories <= 0 && waterMl <= 0
}

/** Compteur d'une semaine du Journal : séances sur la règle de la série de l'Accueil (§ 3.6). */
data class WeekProgress(val sessions: Int, val target: Int) {
    val validated: Boolean get() = sessions >= target
}

/**
 * Calendrier du Journal (SPEC § 15.15, 🔄 D41), en fonctions pures testables sur la JVM : grille
 * du mois, jours à séance, mois accessibles, journée d'un jour, semaines validées. Jour d'une séance
 * = jour local de son début ; seules les séances terminées comptent.
 */
object CalendarRules {
    /** Rangées au plus : la hauteur réservée par le calendrier. */
    const val MAX_ROWS = 6

    /** Semaine du lundi au dimanche (ISO, comme la série du § 3.6). */
    val WEEK: List<DayOfWeek> = DayOfWeek.entries.toList()

    fun monthGrid(month: YearMonth): MonthGrid {
        val first = month.atDay(1)
        return MonthGrid(
            month = month,
            leadingBlanks = first.dayOfWeek.value - DayOfWeek.MONDAY.value,
            days = (1..month.lengthOfMonth()).map { month.atDay(it) },
        )
    }

    /** Jour local du début d'une séance ; `null` si sa date est illisible (§ 10.3). */
    fun startDay(workout: Workout, zone: ZoneId): LocalDate? = parseInstantOrNull(workout.date, zone)?.atZone(zone)?.toLocalDate()

    /** Nombre de séances terminées par jour local de début. */
    fun sessionCounts(workouts: List<Workout>, zone: ZoneId): Map<LocalDate, Int> =
        workouts.mapNotNull { startDay(it, zone) }.groupingBy { it }.eachCount()

    /** Séances terminées du mois [month] (résumé sous la grille). */
    fun monthSessions(counts: Map<LocalDate, Int>, month: YearMonth): Int =
        counts.entries.sumOf { (day, count) -> if (YearMonth.from(day) == month) count else 0 }

    /**
     * Premier mois accessible : celui de la plus ancienne donnée (séance terminée, pesée, calories ou
     * eau notées, prise de complément), jamais après le mois de [today] ; sans donnée, ce mois-là.
     */
    fun firstMonth(
        workouts: List<Workout>,
        weighIns: List<BodyWeightEntry>,
        tracker: DailyTracker,
        supplements: List<Supplement>,
        zone: ZoneId,
        today: LocalDate,
    ): YearMonth {
        val days = sequence {
            workouts.forEach { startDay(it, zone)?.let { day -> yield(day) } }
            weighIns.forEach { entry -> parseInstantOrNull(entry.date, zone)?.let { yield(it.atZone(zone).toLocalDate()) } }
            (tracker.caloriesLog.entries + tracker.waterLog.entries).forEach { (key, value) ->
                if (value > 0 && isDayKey(key)) yield(LocalDate.parse(key))
            }
            supplements.forEach { s -> s.intakes.keys.forEach { key -> if (isDayKey(key)) yield(LocalDate.parse(key)) } }
        }
        val current = YearMonth.from(today)
        val earliest = days.minOrNull()?.let(YearMonth::from) ?: return current
        return minOf(earliest, current)
    }

    /** Mois accessibles, du [first] au [current] compris. */
    fun months(first: YearMonth, current: YearMonth): List<YearMonth> =
        generateSequence(minOf(first, current)) { it.plusMonths(1) }.takeWhile { it <= current }.toList()

    /**
     * Journée du [day] : séances terminées commencées ce jour-là (de la plus ancienne à la plus
     * récente), poids du jour (pesée, sinon poids saisi au début d'une séance de ce jour), compléments
     * pris d'après leur historique — aujourd'hui, une coche « pris » sans historique compte pour une
     * dose —, calories et eau avec les objectifs en vigueur ce jour-là.
     */
    fun dayJournal(
        day: LocalDate,
        today: LocalDate,
        workouts: List<Workout>,
        weighIns: List<BodyWeightEntry>,
        tracker: DailyTracker,
        supplements: List<Supplement>,
        taken: TakenRecord,
        zone: ZoneId,
    ): DayJournal {
        val sessions = workouts
            .map { JournalRules.summary(it, zone) }
            .filter { it.start?.toLocalDate() == day }
            .sortedBy { it.start!!.toInstant() }
        val key = dayKey(day)
        val todayKey = dayKey(today)
        val intakes = supplements.mapNotNull { supplement ->
            val doses = supplement.intakes[key]
                ?: if (key == todayKey && taken[supplement.id] == todayKey) 1 else 0
            if (doses >= 1) SupplementIntake(supplement, doses) else null
        }
        return DayJournal(
            day = day,
            sessions = sessions,
            weight = weightOn(day, workouts, weighIns, zone),
            supplements = intakes,
            calories = (tracker.caloriesLog[key] ?: 0).coerceAtLeast(0),
            calorieGoal = DailyTracker.goalFor(tracker.calorieGoalHistory, key) ?: tracker.calorieGoal,
            waterMl = (tracker.waterLog[key] ?: 0).coerceAtLeast(0),
            waterGoal = DailyTracker.goalFor(tracker.waterGoalHistory, key) ?: tracker.waterGoal,
        )
    }

    /** Poids du jour selon la règle de Progrès (§ 3.10), avec la séance d'où il vient s'il n'y a pas de pesée. */
    private fun weightOn(day: LocalDate, workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): DayWeight? {
        val daily = BodyWeightRules.weightOn(day, workouts, weighIns, zone) ?: return null
        if (daily.weighIn) return DayWeight(daily.weight, fromWorkout = null)
        val source = workouts
            .filter { it.bodyWeight > 0 && startDay(it, zone) == day }
            .maxByOrNull { parseInstantOrNull(it.date, zone)!! }
        return DayWeight(daily.weight, source)
    }

    /** Compteur d'une semaine du Journal : validée à 3 séances, comme la série de l'Accueil (§ 3.6). */
    fun weekProgress(sessions: Int): WeekProgress = WeekProgress(sessions, WorkoutStats.VALIDATED_WEEK_SESSIONS)
}
