package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Ce que fait le rappel de pesée à sa sonnerie (§ 15.13.4). */
enum class WeighInReminderDecision {
    /** Rappel désactivé : rien, et pas de reprogrammation. */
    Disabled,

    /** Jour de la sonnerie non coché (réglage changé entre-temps) : pas de notification. */
    DayNotSelected,

    /** Déjà pesé ce jour-là (pesée, séance terminée ou en cours avec un poids) : pas de notification. */
    AlreadyWeighed,

    /** La notification « Pesée du jour ». */
    Notify,
}

/** Jours d'un rappel de pesée, pour son résumé (§ 15.13.3). */
sealed interface WeighInDays {
    data object EveryDay : WeighInDays
    data object NoDay : WeighInDays

    /** Quelques jours, du lundi au dimanche. */
    data class Some(val days: List<DayOfWeek>) : WeighInDays
}

/** Rappel de pesée (SPEC § 15.13, 🔄 D38), en fonctions pures testables sur la JVM. */
object WeighInReminderRules {
    /** Jours cochés (jours ISO 1 – 7 ; une valeur hors limites est ignorée). */
    fun daysOfWeek(reminder: WeighInReminder): Set<DayOfWeek> =
        reminder.days.filter { it in 1..7 }.mapTo(LinkedHashSet()) { DayOfWeek.of(it) }

    /**
     * Décision à la sonnerie, le jour local [day] : désactivé, puis jour non coché, puis déjà pesé
     * (§ 15.13.1 ; [workouts] = séances terminées et la séance en cours). Health Connect n'est pas lu
     * (🔄 D29) : une pesée faite dans une autre app ne compte pas.
     */
    fun decide(
        reminder: WeighInReminder,
        day: LocalDate,
        workouts: List<Workout>,
        weighIns: List<BodyWeightEntry>,
        zone: ZoneId,
    ): WeighInReminderDecision = when {
        !reminder.enabled -> WeighInReminderDecision.Disabled
        day.dayOfWeek !in daysOfWeek(reminder) -> WeighInReminderDecision.DayNotSelected
        BodyWeightRules.weightOn(day, workouts, weighIns, zone) != null -> WeighInReminderDecision.AlreadyWeighed
        else -> WeighInReminderDecision.Notify
    }

    /** Prochaine sonnerie strictement après [now] ; `null` si le rappel est désactivé ou sans jour. */
    fun nextAlarm(reminder: WeighInReminder, now: Instant, zone: ZoneId): ZonedDateTime? {
        if (!reminder.enabled) return null
        val valid = normalized(reminder)
        return ReminderSchedule.nextOccurrence(valid.hour, valid.minute, daysOfWeek(valid), now, zone)
    }

    /**
     * Réglage relu (fichier ou import, § 15.13.5) : heure hors 0 – 23 ou minute hors 0 – 59 → 07:30 ;
     * jours de 1 à 7 seulement, sans doublon, triés.
     */
    fun normalized(reminder: WeighInReminder): WeighInReminder {
        val timeValid = reminder.hour in 0..23 && reminder.minute in 0..59
        return reminder.copy(
            hour = if (timeValid) reminder.hour else WeighInReminder.DEFAULT_HOUR,
            minute = if (timeValid) reminder.minute else WeighInReminder.DEFAULT_MINUTE,
            days = reminder.days.filter { it in 1..7 }.distinct().sorted(),
        )
    }

    /** Coche ou décoche [day] ; les jours restent triés. */
    fun toggleDay(reminder: WeighInReminder, day: DayOfWeek): WeighInReminder {
        val value = day.value
        val days = if (value in reminder.days) reminder.days - value else reminder.days + value
        return reminder.copy(days = days.distinct().sorted())
    }

    /** « Tous les jours », « Aucun jour » ou la liste des jours cochés (§ 15.13.3). */
    fun daysSummary(reminder: WeighInReminder): WeighInDays {
        val days = daysOfWeek(reminder).sorted()
        return when (days.size) {
            0 -> WeighInDays.NoDay
            7 -> WeighInDays.EveryDay
            else -> WeighInDays.Some(days)
        }
    }
}
