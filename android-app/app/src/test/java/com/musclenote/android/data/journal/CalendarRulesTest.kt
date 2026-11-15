package com.musclenote.android.data.journal

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.WorkoutStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Calendrier du Journal (SPEC § 15.15, 🔄 D41) : grille du mois, jours à séance, mois accessibles,
 * journée d'un jour (séances, poids, compléments pris, calories, eau et protéines — 🔄 D42), semaines
 * validées.
 */
class CalendarRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today: LocalDate = LocalDate.parse("2026-11-15")

    private var nextId = 0

    private fun workout(date: String, name: String = "Push", bodyWeight: Double = 0.0, durationMinutes: Int? = 60) = Workout(
        id = "w${++nextId}",
        name = name,
        date = date,
        bodyWeight = bodyWeight,
        exercises = listOf(WorkoutExercise("bench", sets = listOf(WorkoutSet("s${++nextId}", 8, 80.0, completed = true)))),
        durationMinutes = durationMinutes,
    )

    private fun supplement(id: String, intakes: Map<String, Int> = emptyMap(), dosage: String = "5") =
        Supplement(id, name = id.replaceFirstChar { it.uppercase() }, dosage = dosage, unit = "g", reminderEnabled = false, reminders = emptyList<Reminder>(), intakes = intakes)

    private val emptyTracker = DailyTracker(
        calorieGoal = 2600,
        waterGoal = 2500,
        calorieGoalHistory = mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to 2600),
        waterGoalHistory = mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to 2500),
        caloriesLog = emptyMap(),
        waterLog = emptyMap(),
    )

    private fun day(
        date: String,
        workouts: List<Workout> = emptyList(),
        weighIns: List<BodyWeightEntry> = emptyList(),
        tracker: DailyTracker = emptyTracker,
        supplements: List<Supplement> = emptyList(),
        taken: Map<String, String> = emptyMap(),
    ) = CalendarRules.dayJournal(LocalDate.parse(date), today, workouts, weighIns, tracker, supplements, taken, zone)

    // ─── Grille du mois ───────────────────────────────────────────────────────────

    @Test
    fun `la semaine va du lundi au dimanche`() {
        assertEquals(DayOfWeek.entries.toList(), CalendarRules.WEEK)
        assertEquals(DayOfWeek.MONDAY, CalendarRules.WEEK.first())
    }

    @Test
    fun `un mois qui commence un lundi n'a aucune case vide`() {
        val june = CalendarRules.monthGrid(YearMonth.of(2026, 6))
        assertEquals(0, june.leadingBlanks)
        assertEquals(30, june.days.size)
        assertEquals(5, june.rows)
        assertEquals(LocalDate.parse("2026-06-01"), june.cells.first())
    }

    @Test
    fun `un mois qui commence un dimanche a six cases vides et six rangées`() {
        val march = CalendarRules.monthGrid(YearMonth.of(2026, 3))
        assertEquals(6, march.leadingBlanks)
        assertEquals(6, march.rows)
        assertEquals(List(6) { null }, march.cells.take(6))
        assertEquals(LocalDate.parse("2026-03-01"), march.cells[6])
        assertEquals(LocalDate.parse("2026-03-31"), march.cells.last { it != null })
        assertEquals(42, march.cells.size)
        // Novembre 2026, le mois de la maquette : 30 jours, aussi sur six rangées.
        assertEquals(6, CalendarRules.monthGrid(YearMonth.of(2026, 11)).rows)
    }

    @Test
    fun `février de 28 jours commençant un lundi tient sur quatre rangées`() {
        val february = CalendarRules.monthGrid(YearMonth.of(2027, 2))
        assertEquals(0, february.leadingBlanks)
        assertEquals(28, february.days.size)
        assertEquals(4, february.rows)
        assertEquals(28, february.cells.size)
    }

    @Test
    fun `février bissextile a son 29`() {
        val february = CalendarRules.monthGrid(YearMonth.of(2028, 2))
        assertEquals(29, february.days.size)
        assertEquals(LocalDate.parse("2028-02-29"), february.days.last())
        assertEquals(1, february.leadingBlanks) // le 1er février 2028 est un mardi
        assertEquals(5, february.rows)
        // Les cases après le dernier jour complètent la rangée.
        assertEquals(35, february.cells.size)
        assertNull(february.cells.last())
    }

    @Test
    fun `aucun mois ne dépasse six rangées`() {
        val months = (0L until 120L).map { YearMonth.of(2020, 1).plusMonths(it) }
        assertTrue(months.all { CalendarRules.monthGrid(it).rows in 4..CalendarRules.MAX_ROWS })
        assertTrue(months.any { CalendarRules.monthGrid(it).rows == CalendarRules.MAX_ROWS })
    }

    // ─── Jours à séance et résumé du mois ─────────────────────────────────────────

    @Test
    fun `une séance compte au jour local de son début`() {
        val workouts = listOf(
            // Commencée à 23:30 à Paris le 11, finie après minuit : le 11.
            workout("2026-11-11T22:30:00.000Z", durationMinutes = 70),
            // Commencée à 00:15 à Paris le 12, enregistrée le 11 en UTC : le 12.
            workout("2026-11-11T23:15:00.000Z"),
            workout("2026-11-12T17:00:00.000Z"),
            workout("pas une date"),
        )
        val counts = CalendarRules.sessionCounts(workouts, zone)
        assertEquals(mapOf(LocalDate.parse("2026-11-11") to 1, LocalDate.parse("2026-11-12") to 2), counts)
    }

    @Test
    fun `le résumé compte les séances terminées du mois`() {
        val counts = CalendarRules.sessionCounts(
            listOf(workout("2026-10-31T17:00:00.000Z"), workout("2026-11-02T17:00:00.000Z"), workout("2026-11-04T17:00:00.000Z")),
            zone,
        )
        assertEquals(2, CalendarRules.monthSessions(counts, YearMonth.of(2026, 11)))
        assertEquals(1, CalendarRules.monthSessions(counts, YearMonth.of(2026, 10)))
        assertEquals(0, CalendarRules.monthSessions(counts, YearMonth.of(2026, 9)))
    }

    // ─── Mois accessibles ─────────────────────────────────────────────────────────

    @Test
    fun `les mois vont de la plus ancienne donnée au mois courant`() {
        val first = CalendarRules.firstMonth(
            workouts = listOf(workout("2026-09-12T17:00:00.000Z")),
            weighIns = listOf(BodyWeightEntry("2026-08-20T10:00:00.000Z", 80.0)),
            tracker = emptyTracker.copy(waterLog = mapOf("2026-07-03" to 500, "2026-06-01" to 0)),
            supplements = listOf(supplement("creatine", intakes = mapOf("2026-05-30" to 1))),
            zone = zone,
            today = today,
        )
        // Une eau à 0 ne compte pas ; la prise du 30 mai est la plus ancienne donnée.
        assertEquals(YearMonth.of(2026, 5), first)
        assertEquals((5..11).map { YearMonth.of(2026, it) }, CalendarRules.months(first, YearMonth.from(today)))
    }

    @Test
    fun `sans donnée seul le mois courant est accessible, jamais un mois à venir`() {
        assertEquals(YearMonth.of(2026, 11), CalendarRules.firstMonth(emptyList(), emptyList(), emptyTracker, emptyList(), zone, today))
        val future = CalendarRules.firstMonth(listOf(workout("2027-01-05T17:00:00.000Z")), emptyList(), emptyTracker, emptyList(), zone, today)
        assertEquals(YearMonth.of(2026, 11), future)
        assertEquals(listOf(YearMonth.of(2026, 11)), CalendarRules.months(future, YearMonth.of(2026, 11)))
    }

    // ─── Journée ──────────────────────────────────────────────────────────────────

    @Test
    fun `les séances d'une journée suivent le jour local de leur début, de la plus ancienne à la plus récente`() {
        val lateEvening = workout("2026-11-11T22:30:00.000Z", name = "Legs", durationMinutes = 70)
        val afterMidnight = workout("2026-11-11T23:15:00.000Z", name = "Cardio")
        val evening = workout("2026-11-12T17:30:00.000Z", name = "Push")
        val workouts = listOf(evening, afterMidnight, lateEvening)
        assertEquals(listOf("Legs"), day("2026-11-11", workouts).sessions.map { it.workout.name })
        assertEquals(listOf("Cardio", "Push"), day("2026-11-12", workouts).sessions.map { it.workout.name })
    }

    @Test
    fun `le poids du jour est la pesée, sinon le poids saisi au début d'une séance`() {
        val morning = workout("2026-11-10T07:00:00.000Z", name = "Pull", bodyWeight = 78.0)
        val evening = workout("2026-11-10T17:00:00.000Z", name = "Push", bodyWeight = 78.6)
        val withoutWeight = workout("2026-11-09T17:00:00.000Z", name = "Legs")
        val weighIn = BodyWeightEntry("2026-11-11T11:00:00.000Z", 78.4)
        val workouts = listOf(evening, morning, withoutWeight)

        val weighed = day("2026-11-11", workouts, listOf(weighIn)).weight!!
        assertEquals(78.4, weighed.kg, 0.0)
        assertNull(weighed.fromWorkout)

        val fromSession = day("2026-11-10", workouts, listOf(weighIn)).weight!!
        assertEquals(78.6, fromSession.kg, 0.0)
        assertEquals("Push", fromSession.fromWorkout?.name)

        assertNull(day("2026-11-09", workouts, listOf(weighIn)).weight)
    }

    @Test
    fun `les compléments pris viennent de l'historique des prises, avec leurs doses`() {
        val supplements = listOf(
            supplement("whey", intakes = mapOf("2026-11-10" to 2, "2026-11-11" to 1)),
            supplement("creatine", intakes = mapOf("2026-11-10" to 1)),
            supplement("omega", intakes = mapOf("2026-11-12" to 1)),
        )
        val intakes = day("2026-11-10", supplements = supplements).supplements
        assertEquals(listOf("whey" to 2, "creatine" to 1), intakes.map { it.supplement.id to it.doses })
    }

    @Test
    fun `un jour sans prise n'a pas de compléments`() {
        val supplements = listOf(supplement("whey", intakes = mapOf("2026-11-10" to 1)))
        assertTrue(day("2026-11-09", supplements = supplements).supplements.isEmpty())
        // L'entrée « pris » ne vaut que pour aujourd'hui : elle ne remplit pas un jour passé.
        assertTrue(day("2026-11-14", supplements = listOf(supplement("whey")), taken = mapOf("whey" to "2026-11-14")).supplements.isEmpty())
    }

    @Test
    fun `aujourd'hui une coche sans historique compte pour une dose`() {
        // Coché avant la mise à jour : `taken` le dit, l'historique ne le connaît pas encore.
        val intakes = day("2026-11-15", supplements = listOf(supplement("whey")), taken = mapOf("whey" to "2026-11-15")).supplements
        assertEquals(listOf("whey" to 1), intakes.map { it.supplement.id to it.doses })
        // Une coche d'un autre jour ne compte pas.
        assertTrue(day("2026-11-15", supplements = listOf(supplement("whey")), taken = mapOf("whey" to "2026-11-14")).supplements.isEmpty())
    }

    @Test
    fun `calories et eau gardent l'objectif en vigueur ce jour-là`() {
        val tracker = emptyTracker.copy(
            calorieGoal = 2800,
            calorieGoalHistory = mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to 2600, "2026-11-13" to 2800),
            caloriesLog = mapOf("2026-11-12" to 1850),
            waterLog = mapOf("2026-11-12" to 1500),
        )
        val journal = day("2026-11-12", tracker = tracker)
        assertEquals(1850, journal.calories)
        assertEquals(2600, journal.calorieGoal)
        assertEquals(1500, journal.waterMl)
        assertEquals(2500, journal.waterGoal)
        assertFalse(journal.isEmpty)
    }

    @Test
    fun `un jour sans rien est vide`() {
        val tracker = emptyTracker.copy(caloriesLog = mapOf("2026-11-08" to 0))
        val journal = day("2026-11-08", tracker = tracker)
        assertTrue(journal.sessions.isEmpty())
        assertNull(journal.weight)
        assertTrue(journal.supplements.isEmpty())
        assertEquals(0, journal.calories)
        assertEquals(0, journal.waterMl)
        assertTrue(journal.isEmpty)
    }

    @Test
    fun `les protéines du jour sont dans la journée`() {
        val tracker = emptyTracker.copy(caloriesLog = mapOf("2026-11-12" to 1850), proteinLog = mapOf("2026-11-12" to 112, "2026-11-13" to 0))
        assertEquals(112, day("2026-11-12", tracker = tracker).proteinGrams)
        assertEquals(0, day("2026-11-13", tracker = tracker).proteinGrams)
        assertTrue("des protéines à 0 ne font pas une journée", day("2026-11-13", tracker = tracker).isEmpty)
    }

    @Test
    fun `un jour avec des protéines seulement n'est pas vide, et compte pour les mois accessibles`() {
        val tracker = emptyTracker.copy(proteinLog = mapOf("2026-10-02" to 140))
        val journal = day("2026-10-02", tracker = tracker)
        assertEquals(0, journal.calories)
        assertEquals(0, journal.waterMl)
        assertFalse(journal.isEmpty)
        assertEquals(YearMonth.of(2026, 10), CalendarRules.firstMonth(emptyList(), emptyList(), tracker, emptyList(), zone, today))
    }

    // ─── Semaines validées ────────────────────────────────────────────────────────

    @Test
    fun `une semaine est validée à 3 séances, la règle de la série de l'Accueil`() {
        assertEquals(WorkoutStats.VALIDATED_WEEK_SESSIONS, CalendarRules.weekProgress(0).target)
        assertFalse(CalendarRules.weekProgress(0).validated)
        assertFalse(CalendarRules.weekProgress(2).validated)
        assertTrue(CalendarRules.weekProgress(3).validated)
        assertTrue(CalendarRules.weekProgress(4).validated)
        assertEquals(4, CalendarRules.weekProgress(4).sessions)
    }
}
