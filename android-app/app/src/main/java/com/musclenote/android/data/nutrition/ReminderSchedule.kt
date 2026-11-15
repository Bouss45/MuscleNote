package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.Supplement
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Identité d'un rappel, donc de son alarme (§ 7.3) : complément + rappel. */
data class ReminderRef(val supplementId: String, val reminderId: String)

/** Alarme attendue : un rappel d'un complément dont les rappels sont activés. */
data class ReminderAlarm(val ref: ReminderRef, val hour: Int, val minute: Int)

/**
 * Programmation des rappels de compléments (SPEC § 7.3, § 10.10), en fonctions pures testables
 * sur la JVM : heure **locale murale**, une alarme par rappel, reprogrammée chaque jour.
 */
object ReminderSchedule {
    /**
     * Prochaine occurrence de `hour:minute`, heure locale du fuseau [zone], strictement après
     * [now] : aujourd'hui, ou demain si elle est déjà passée (ou exactement maintenant).
     * `ZonedDateTime.of` résout les cas du changement d'heure : une heure inexistante (passage à
     * l'heure d'été) est décalée après le saut ; une heure ambiguë (passage à l'heure d'hiver)
     * prend sa première occurrence.
     */
    fun nextOccurrence(hour: Int, minute: Int, now: Instant, zone: ZoneId): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        val today = now.atZone(zone).toLocalDate()
        val candidate = ZonedDateTime.of(today, time, zone)
        return if (candidate.toInstant().isAfter(now)) candidate else ZonedDateTime.of(today.plusDays(1), time, zone)
    }

    /**
     * Prochaine occurrence de `hour:minute`, heure locale de [zone], strictement après [now], un jour
     * de [days] (rappel de pesée, § 15.13.4) : le premier jour coché, aujourd'hui compris, où cette
     * heure est encore à venir ; `null` sans jour coché. Changements d'heure résolus comme dans
     * [nextOccurrence].
     */
    fun nextOccurrence(hour: Int, minute: Int, days: Set<DayOfWeek>, now: Instant, zone: ZoneId): ZonedDateTime? {
        if (days.isEmpty()) return null
        val time = LocalTime.of(hour, minute)
        val today = now.atZone(zone).toLocalDate()
        // Huit jours : le seul jour coché, déjà passé aujourd'hui, revient dans 7 jours.
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val candidate = ZonedDateTime.of(date, time, zone)
            if (candidate.toInstant().isAfter(now)) return candidate
        }
        return null
    }

    /** Alarmes qui doivent exister : chaque rappel d'un complément dont `reminderEnabled` est vrai. */
    fun expectedAlarms(supplements: List<Supplement>): List<ReminderAlarm> =
        supplements.filter { it.reminderEnabled }.flatMap { supplement ->
            supplement.reminders.map { ReminderAlarm(ReminderRef(supplement.id, it.id), it.hour, it.minute) }
        }

    /** Tous les rappels d'une liste, activés ou non : ce qui a pu avoir une alarme. */
    fun refs(supplements: List<Supplement>): Set<ReminderRef> =
        supplements.flatMapTo(HashSet()) { supplement -> supplement.reminders.map { ReminderRef(supplement.id, it.id) } }

    /**
     * Alarmes à annuler lors d'une synchronisation : rappels de [previous] qui n'ont plus d'alarme
     * attendue dans [current] (supprimés, ou rappels désactivés).
     */
    fun toCancel(previous: List<Supplement>, current: List<Supplement>): Set<ReminderRef> =
        refs(previous) - expectedAlarms(current).mapTo(HashSet()) { it.ref }

    /**
     * À la sonnerie, le récepteur relit les données : l'alarme du rappel [ref] n'a lieu d'être
     * que si le complément et le rappel existent encore et que ses rappels sont activés.
     */
    fun alarmFor(ref: ReminderRef, supplements: List<Supplement>): Pair<Supplement, ReminderAlarm>? {
        val supplement = supplements.firstOrNull { it.id == ref.supplementId } ?: return null
        if (!supplement.reminderEnabled) return null
        val reminder = supplement.reminders.firstOrNull { it.id == ref.reminderId } ?: return null
        return supplement to ReminderAlarm(ref, reminder.hour, reminder.minute)
    }
}
