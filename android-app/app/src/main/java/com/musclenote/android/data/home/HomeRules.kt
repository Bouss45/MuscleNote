package com.musclenote.android.data.home

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WeekCell
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.data.workout.WorkoutStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** La surface mise en avant de l'Accueil (SPEC § 15.8.2). */
sealed interface HomeHighlight {
    /** Une séance est en cours : « En cours », « Reprendre ». */
    data class Live(val workout: Workout) : HomeHighlight

    /** « Prochaine séance » : les types proposés, le suggéré en premier ; vide = pas de suggestion. */
    data class Next(val types: List<SessionType>) : HomeHighlight
}

/**
 * Un type de séance régulier (§ 15.8.3) : sa clé (§ 3.1), sa séance la plus récente (nom affiché,
 * préréglage ou nom pour démarrer, exercices repris), les jours écoulés depuis, et son nombre de
 * séances dans la fenêtre.
 */
data class SessionType(
    val key: String,
    val lastWorkout: Workout,
    val daysSince: Int,
    val count: Int,
) {
    val preset: PresetId? get() = lastWorkout.preset

    /** Les exercices que le pré-remplissage reprendra (§ 3.1). */
    val exerciseCount: Int get() = lastWorkout.exercises.size
}

/** « Cette semaine » : séances de la semaine en cours, série en cours (0 si cassée), record, barres. */
data class HomeWeek(val sessions: Int, val streakWeeks: Int, val bestWeeks: Int, val bars: List<WeekCell>)

/** Règles pures de l'Accueil (SPEC § 15.8.3), testées sur la JVM. */
object HomeRules {
    /** Fenêtre des types réguliers : les 42 derniers jours, aujourd'hui compris. */
    const val WINDOW_DAYS = 42

    /** Puces de type de la surface « Prochaine séance ». */
    const val MAX_TYPES = 3

    /** Séance en cours → « En cours » ; sinon « Prochaine séance » avec les types suggérés. */
    fun highlight(current: Workout?, history: List<Workout>, today: LocalDate, zone: ZoneId): HomeHighlight =
        if (current != null) HomeHighlight.Live(current) else HomeHighlight.Next(suggestedTypes(history, today, zone))

    /** Les trois premiers types réguliers : le premier est le type suggéré. */
    fun suggestedTypes(history: List<Workout>, today: LocalDate, zone: ZoneId): List<SessionType> =
        regularTypes(history, today, zone).take(MAX_TYPES)

    /**
     * Types réguliers des [WINDOW_DAYS] derniers jours : au moins 2 séances et au moins la moitié
     * de la clé la plus fréquente ; de la dernière séance la plus ancienne à la plus récente (à
     * égalité : la plus fréquente, puis l'ordre de la clé).
     */
    fun regularTypes(history: List<Workout>, today: LocalDate, zone: ZoneId): List<SessionType> {
        val firstDay = today.minusDays(WINDOW_DAYS - 1L)
        val dated = history.mapNotNull { workout ->
            val start = parseInstantOrNull(workout.date, zone) ?: return@mapNotNull null
            val day = start.atZone(zone).toLocalDate()
            if (day < firstDay || day > today) null else Triple(workout, start, day)
        }
        val byKey = dated.groupBy { WorkoutRules.sessionKey(it.first) }
        val maxCount = byKey.values.maxOfOrNull { it.size } ?: return emptyList()

        class Candidate(val type: SessionType, val lastStart: Instant)

        return byKey
            .filter { (_, sessions) -> sessions.size >= 2 && sessions.size * 2 >= maxCount }
            .map { (key, sessions) ->
                val (last, start, day) = sessions.maxBy { it.second }
                Candidate(SessionType(key, last, ChronoUnit.DAYS.between(day, today).toInt(), sessions.size), start)
            }
            .sortedWith(compareBy<Candidate> { it.lastStart }.thenByDescending { it.type.count }.thenBy { it.type.key })
            .map { it.type }
    }

    /** « Cette semaine » (§ 3.6) : la série affichée vaut 0 quand elle est cassée. */
    fun thisWeek(history: List<Workout>, today: LocalDate, zone: ZoneId): HomeWeek {
        val streak = WorkoutStats.streak(history, today, zone)
        val bars = streak.lastWeeks
        return HomeWeek(
            sessions = bars.lastOrNull()?.count ?: 0,
            streakWeeks = if (streak.isAlive) streak.current else 0,
            bestWeeks = streak.best,
            bars = bars,
        )
    }
}
