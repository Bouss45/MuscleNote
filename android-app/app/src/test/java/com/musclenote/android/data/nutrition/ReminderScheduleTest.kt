package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Prochaine occurrence d'un rappel en heure locale murale (§ 7.3, § 10.10). Paris : passage à
 * l'heure d'été le 29 mars 2026 (2 h → 3 h), à l'heure d'hiver le 25 octobre 2026 (3 h → 2 h).
 */
class ReminderScheduleTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun next(hour: Int, minute: Int, now: String): Instant =
        ReminderSchedule.nextOccurrence(hour, minute, Instant.parse(now), paris).toInstant()

    @Test
    fun `aujourd'hui si l'heure n'est pas passée, sinon demain`() {
        // 7 h à Paris (UTC+1).
        assertEquals(Instant.parse("2026-03-14T07:00:00Z"), next(8, 0, "2026-03-14T06:00:00Z"))
        assertEquals(Instant.parse("2026-03-15T05:59:00Z"), next(6, 59, "2026-03-14T06:00:00Z"))
    }

    @Test
    fun `exactement maintenant donne demain, l'alarme qui sonne ne se reprogramme pas sur elle-même`() {
        assertEquals(Instant.parse("2026-03-15T07:00:00Z"), next(8, 0, "2026-03-14T07:00:00Z"))
        assertEquals(Instant.parse("2026-03-15T07:00:00Z"), next(8, 0, "2026-03-14T07:00:00.250Z"))
    }

    @Test
    fun `le lendemain se calcule en jours locaux, à travers la fin d'année`() {
        // 23 h 30 le 31 décembre à Paris : 23 h 00 est passé → 1er janvier 23 h.
        assertEquals(Instant.parse("2027-01-01T22:00:00Z"), next(23, 0, "2026-12-31T22:30:00Z"))
    }

    @Test
    fun `passage à l'heure d'été, une heure inexistante est décalée après le saut`() {
        // 29 mars, 1 h du matin (UTC+1) : 2 h 30 n'existe pas → 3 h 30 heure d'été (UTC+2).
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next(2, 30, "2026-03-29T00:00:00Z"))
    }

    @Test
    fun `passage à l'heure d'été, 8 h reste 8 h à la montre`() {
        // Samedi 28, 9 h 30 (UTC+1) : prochain 8 h = dimanche 29, 8 h heure d'été = 6 h UTC (22 h 30 plus tard).
        assertEquals(Instant.parse("2026-03-29T06:00:00Z"), next(8, 0, "2026-03-28T08:30:00Z"))
    }

    @Test
    fun `passage à l'heure d'hiver, une heure ambiguë prend sa première occurrence`() {
        // 25 octobre, 1 h du matin (UTC+2) : 2 h 30 arrive deux fois, la première est à UTC+2.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), next(2, 30, "2026-10-24T23:00:00Z"))
    }

    @Test
    fun `passage à l'heure d'hiver, la seconde occurrence ne sonne pas une deuxième fois`() {
        // Juste après la première 2 h 30 (2 h 45 UTC+2) : prochaine = 26 octobre 2 h 30 (UTC+1).
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), next(2, 30, "2026-10-25T00:45:00Z"))
    }

    @Test
    fun `passage à l'heure d'hiver, 8 h reste 8 h à la montre`() {
        // Samedi 24, 9 h (UTC+2) : prochain 8 h = dimanche 25, 8 h heure d'hiver = 7 h UTC (24 h + 1 h plus tard).
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), next(8, 0, "2026-10-24T07:00:00Z"))
    }

    // ─── Alarmes attendues ────────────────────────────────────────────────────────────

    private val crea = Supplement("crea", "Créatine", "5", "g", true, listOf(Reminder("r1", 8, 0), Reminder("r2", 20, 0)))
    private val whey = Supplement("whey", "Whey", "", "dose", false, listOf(Reminder("r3", 18, 0)))

    @Test
    fun `une alarme par rappel des compléments aux rappels activés`() {
        assertEquals(
            listOf(
                ReminderAlarm(ReminderRef("crea", "r1"), 8, 0),
                ReminderAlarm(ReminderRef("crea", "r2"), 20, 0),
            ),
            ReminderSchedule.expectedAlarms(listOf(crea, whey)),
        )
    }

    @Test
    fun `une synchronisation annule les rappels supprimés ou désactivés`() {
        val now = listOf(crea.copy(reminders = crea.reminders.take(1)), whey)
        assertEquals(setOf(ReminderRef("crea", "r2"), ReminderRef("whey", "r3")), ReminderSchedule.toCancel(listOf(crea, whey), now))
        // Complément supprimé : toutes ses alarmes.
        assertEquals(setOf(ReminderRef("crea", "r1"), ReminderRef("crea", "r2")), ReminderSchedule.toCancel(listOf(crea), emptyList()))
    }

    @Test
    fun `à la sonnerie le rappel est relu, rien s'il a disparu ou est désactivé`() {
        assertEquals(crea to ReminderAlarm(ReminderRef("crea", "r2"), 20, 0), ReminderSchedule.alarmFor(ReminderRef("crea", "r2"), listOf(crea)))
        assertNull(ReminderSchedule.alarmFor(ReminderRef("crea", "r9"), listOf(crea)))
        assertNull(ReminderSchedule.alarmFor(ReminderRef("absent", "r1"), listOf(crea)))
        assertNull(ReminderSchedule.alarmFor(ReminderRef("whey", "r3"), listOf(whey)))
    }
}
