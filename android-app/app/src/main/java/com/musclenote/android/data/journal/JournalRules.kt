package com.musclenote.android.data.journal

import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * Résumé d'une séance terminée, pour sa ligne du Journal et de l'Accueil (SPEC § 15.8.4).
 * [start] : début de la séance au fuseau local (`null` si la date est illisible) ;
 * [durationMinutes] : affichée seulement si elle est connue et non nulle ; [volumeKg] : volume des
 * séries validées arrondi au kg (§ 3.8) ; [completedSets] : séries validées.
 */
data class SessionSummary(
    val workout: Workout,
    val start: ZonedDateTime?,
    val durationMinutes: Int?,
    val volumeKg: Long,
    val completedSets: Int,
) {
    /** La ligne montre le volume ; sans volume (cardio, poids du corps), le nombre de séries. */
    val showsVolume: Boolean get() = volumeKg > 0
}

/** Durée affichée : heures et minutes (« 45 min », « 1 h », « 1 h 12 »). */
data class DisplayDuration(val hours: Int, val minutes: Int)

/** Une section du Journal : le lundi de sa semaine (`null` : dates illisibles) et ses séances. */
data class JournalWeek(val monday: LocalDate?, val sessions: List<SessionSummary>)

/** Titre d'une section de semaine. */
sealed interface WeekLabel {
    data object Current : WeekLabel

    data object Previous : WeekLabel

    /** « Semaine du 26 oct. », avec l'année si [withYear]. */
    data class Of(val monday: LocalDate, val withYear: Boolean) : WeekLabel
}

/**
 * Totaux du Journal (« Depuis le début ») : séances, volume en tonnes arrondies (§ 3.8), temps en
 * salle total et moyen (§ 3.7, `null` si aucune séance n'est retenue).
 */
data class JournalTotals(val sessions: Int, val volumeTonnes: Long, val gymMinutes: Int?, val averageMinutes: Int?)

/** Règles pures du Journal (SPEC § 15.8.4), testées sur la JVM. */
object JournalRules {

    fun summary(workout: Workout, zone: ZoneId): SessionSummary = SessionSummary(
        workout = workout,
        start = parseInstantOrNull(workout.date, zone)?.atZone(zone),
        durationMinutes = workout.durationMinutes?.takeIf { it > 0 },
        volumeKg = WorkoutStats.volume(workout).roundToLong(),
        completedSets = WorkoutStats.completedSetCount(workout),
    )

    fun duration(minutes: Int): DisplayDuration = DisplayDuration(minutes / 60, minutes % 60)

    /**
     * Séances groupées par semaine ISO (du lundi au dimanche, au jour local), semaines et séances de
     * la plus récente à la plus ancienne ; les dates illisibles dans une dernière section.
     */
    fun weeks(workouts: List<Workout>, zone: ZoneId): List<JournalWeek> {
        val summaries = workouts.map { summary(it, zone) }
        val dated = summaries.filter { it.start != null }
            .sortedByDescending { it.start!!.toInstant() }
            .groupBy { WorkoutStats.weekStart(it.start!!.toLocalDate()) }
            .map { (monday, sessions) -> JournalWeek(monday, sessions) }
            .sortedByDescending { it.monday }
        val undated = summaries.filter { it.start == null }
        return if (undated.isEmpty()) dated else dated + JournalWeek(null, undated)
    }

    /** Titre de la semaine du lundi [monday], vu le [today]. */
    fun weekLabel(monday: LocalDate, today: LocalDate): WeekLabel {
        val current = WorkoutStats.weekStart(today)
        return when (monday) {
            current -> WeekLabel.Current
            current.minusWeeks(1) -> WeekLabel.Previous
            else -> WeekLabel.Of(monday, withYear = monday.year != today.year)
        }
    }

    fun totals(workouts: List<Workout>): JournalTotals {
        val volume = workouts.sumOf { WorkoutStats.volume(it) }
        val gym = WorkoutStats.gymTime(workouts)
        val retained = gym.sessionCount > 0
        return JournalTotals(
            sessions = workouts.size,
            volumeTonnes = (volume / 1000).roundToLong(),
            gymMinutes = gym.totalMinutes.takeIf { retained },
            averageMinutes = gym.averageMinutes.takeIf { retained },
        )
    }
}
