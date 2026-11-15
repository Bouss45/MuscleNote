package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Rappel de pesée (§ 15.13.3 – 15.13.5, 🔄 D38) : réglage, décision à la sonnerie, résumé des jours. */
class WeighInReminderRulesTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    /** Jeudi 12 novembre 2026. */
    private val thursday = LocalDate.parse("2026-11-12")

    private val enabled = WeighInReminder(enabled = true)

    private fun workout(id: String, date: String, bodyWeight: Double) =
        Workout(id = id, name = "Push", date = date, bodyWeight = bodyWeight, exercises = emptyList())

    private fun weighIn(day: String, weight: Double) =
        BodyWeightEntry(formatInstant(BodyWeightRules.noonOf(LocalDate.parse(day), paris)), weight)

    private fun decide(
        reminder: WeighInReminder = enabled,
        workouts: List<Workout> = emptyList(),
        weighIns: List<BodyWeightEntry> = emptyList(),
    ) = WeighInReminderRules.decide(reminder, thursday, workouts, weighIns, paris)

    @Test
    fun `par défaut - désactivé, 7 h 30, tous les jours`() {
        assertEquals(WeighInReminder(enabled = false, hour = 7, minute = 30, days = listOf(1, 2, 3, 4, 5, 6, 7)), WeighInReminder.DEFAULT)
    }

    @Test
    fun `désactivé - rien, même un jour coché sans pesée`() {
        assertEquals(WeighInReminderDecision.Disabled, decide(reminder = WeighInReminder.DEFAULT))
    }

    @Test
    fun `jour non coché - pas de notification`() {
        // Lundi, mercredi, vendredi cochés ; la sonnerie tombe un jeudi (réglage changé entre-temps).
        assertEquals(WeighInReminderDecision.DayNotSelected, decide(reminder = enabled.copy(days = listOf(1, 3, 5))))
        assertEquals(WeighInReminderDecision.DayNotSelected, decide(reminder = enabled.copy(days = emptyList())))
    }

    @Test
    fun `déjà pesé ce jour-là - pesée, séance terminée ou séance en cours avec un poids`() {
        assertEquals(WeighInReminderDecision.AlreadyWeighed, decide(weighIns = listOf(weighIn("2026-11-12", 78.4))))
        assertEquals(WeighInReminderDecision.AlreadyWeighed, decide(workouts = listOf(workout("terminée", "2026-11-12T06:00:00.000Z", 79.0))))
        // La séance en cours est passée avec les séances terminées.
        assertEquals(WeighInReminderDecision.AlreadyWeighed, decide(workouts = listOf(workout("en-cours", "2026-11-12T06:15:00.000Z", 78.0))))
    }

    @Test
    fun `pas pesé ce jour-là - notification`() {
        // Pesée d'hier, séance du jour passée sans pesée.
        assertEquals(
            WeighInReminderDecision.Notify,
            decide(workouts = listOf(workout("passée", "2026-11-12T06:00:00.000Z", 0.0)), weighIns = listOf(weighIn("2026-11-11", 78.4))),
        )
    }

    @Test
    fun `Health Connect n'est jamais lu - seules les données de l'app comptent`() {
        // Une pesée faite dans une autre app (balance connectée) n'existe pas pour MuscleNote (D29, aucune
        // permission de lecture) : sans pesée ni séance avec un poids dans l'app, le rappel sonne.
        assertEquals(WeighInReminderDecision.Notify, decide())
    }

    @Test
    fun `l'ordre des raisons - désactivé, puis jour non coché, puis déjà pesé`() {
        val weighed = listOf(weighIn("2026-11-12", 78.4))
        assertEquals(WeighInReminderDecision.Disabled, decide(reminder = WeighInReminder(enabled = false, days = listOf(1)), weighIns = weighed))
        assertEquals(WeighInReminderDecision.DayNotSelected, decide(reminder = enabled.copy(days = listOf(1)), weighIns = weighed))
    }

    @Test
    fun `prochaine alarme - aucune si désactivé ou sans jour, sinon la prochaine occurrence d'un jour coché`() {
        val now = Instant.parse("2026-11-12T07:00:00Z") // jeudi 8 h : 7 h 30 est passé
        assertNull(WeighInReminderRules.nextAlarm(WeighInReminder.DEFAULT, now, paris))
        assertNull(WeighInReminderRules.nextAlarm(enabled.copy(days = emptyList()), now, paris))
        assertEquals(Instant.parse("2026-11-13T06:30:00Z"), WeighInReminderRules.nextAlarm(enabled, now, paris)!!.toInstant())
        assertEquals(Instant.parse("2026-11-16T05:45:00Z"), WeighInReminderRules.nextAlarm(enabled.copy(hour = 6, minute = 45, days = listOf(1)), now, paris)!!.toInstant())
    }

    @Test
    fun `réglage lu - heure ou minute invalide donne 7 h 30, jours de 1 à 7 sans doublon et triés`() {
        assertEquals(WeighInReminder(true, 7, 30, listOf(1, 3)), WeighInReminderRules.normalized(WeighInReminder(true, 25, 10, listOf(3, 1, 1, 9, 0))))
        assertEquals(WeighInReminder(true, 7, 30, emptyList()), WeighInReminderRules.normalized(WeighInReminder(true, 6, 60, emptyList())))
        assertEquals(WeighInReminder(true, 0, 0, listOf(7)), WeighInReminderRules.normalized(WeighInReminder(true, 0, 0, listOf(7))))
    }

    @Test
    fun `cocher un jour l'ajoute, le décocher le retire, la liste reste triée`() {
        val monWed = enabled.copy(days = listOf(1, 3))
        assertEquals(listOf(1, 3, 5), WeighInReminderRules.toggleDay(monWed, FRIDAY).days)
        assertEquals(listOf(3), WeighInReminderRules.toggleDay(monWed, MONDAY).days)
        assertEquals(listOf(1, 3, 4), WeighInReminderRules.toggleDay(monWed, THURSDAY).days)
    }

    @Test
    fun `résumé des jours - tous les jours, aucun jour, ou la liste`() {
        assertEquals(WeighInDays.EveryDay, WeighInReminderRules.daysSummary(enabled))
        assertEquals(WeighInDays.NoDay, WeighInReminderRules.daysSummary(enabled.copy(days = emptyList())))
        assertEquals(WeighInDays.Some(listOf(MONDAY, WEDNESDAY, FRIDAY)), WeighInReminderRules.daysSummary(enabled.copy(days = listOf(1, 3, 5))))
    }
}
