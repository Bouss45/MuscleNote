package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suivi du jour, objectifs datés, « pris aujourd'hui » et compléments (§ 2.11, § 3.12). */
class NutritionRulesTest {

    private val today = "2026-03-14"
    private val yesterday = "2026-03-13"

    // ─── Calories et eau ─────────────────────────────────────────────────────────────

    @Test
    fun `ajouter des calories cumule le jour, sans descendre sous 0`() {
        var tracker = DailyTracker.DEFAULT.copy(caloriesLog = mapOf(yesterday to 2100))
        tracker = NutritionRules.addCalories(tracker, today, 500)
        tracker = NutritionRules.addCalories(tracker, today, 300)
        assertEquals(800, tracker.caloriesLog[today])
        tracker = NutritionRules.addCalories(tracker, today, -1000)
        assertEquals(0, tracker.caloriesLog[today])
        // Les autres jours ne bougent pas.
        assertEquals(2100, tracker.caloriesLog[yesterday])
    }

    @Test
    fun `définir les calories remplace la valeur du jour, bornée à 0`() {
        var tracker = NutritionRules.addCalories(DailyTracker.DEFAULT, today, 900)
        tracker = NutritionRules.setCalories(tracker, today, 1800)
        assertEquals(1800, tracker.caloriesLog[today])
        assertEquals(0, NutritionRules.setCalories(tracker, today, -5).caloriesLog[today])
    }

    @Test
    fun `l'eau se cumule en ml et la remise à zéro garde un jour à 0`() {
        var tracker = NutritionRules.addWater(DailyTracker.DEFAULT, today, 250)
        tracker = NutritionRules.addWater(tracker, today, 500)
        assertEquals(750, tracker.waterLog[today])
        tracker = NutritionRules.addWater(tracker, today, -750)
        assertEquals(mapOf(today to 0), tracker.waterLog)
    }

    // ─── Objectifs datés ──────────────────────────────────────────────────────────────

    @Test
    fun `un nouvel objectif s'applique à partir d'aujourd'hui seulement`() {
        val tracker = NutritionRules.setCalorieGoal(DailyTracker.DEFAULT, today, 2500)
        assertEquals(2500, tracker.calorieGoal)
        assertEquals(mapOf("0000-01-01" to 2000, today to 2500), tracker.calorieGoalHistory)
        assertEquals(2000, DailyTracker.goalFor(tracker.calorieGoalHistory, "2026-01-01"))
        assertEquals(2000, DailyTracker.goalFor(tracker.calorieGoalHistory, yesterday))
        assertEquals(2500, DailyTracker.goalFor(tracker.calorieGoalHistory, today))
        assertEquals(2500, DailyTracker.goalFor(tracker.calorieGoalHistory, "2026-06-01"))
    }

    @Test
    fun `changer l'objectif deux fois le même jour remplace l'entrée du jour`() {
        var tracker = NutritionRules.setWaterGoal(DailyTracker.DEFAULT, yesterday, 2500)
        tracker = NutritionRules.setWaterGoal(tracker, today, 3000)
        tracker = NutritionRules.setWaterGoal(tracker, today, 2800)
        assertEquals(2800, tracker.waterGoal)
        assertEquals(mapOf("0000-01-01" to 2000, yesterday to 2500, today to 2800), tracker.waterGoalHistory)
        assertEquals(2500, DailyTracker.goalFor(tracker.waterGoalHistory, yesterday))
    }

    @Test
    fun `un objectif nul ou négatif est ignoré`() {
        val tracker = DailyTracker.DEFAULT
        assertSame(tracker, NutritionRules.setCalorieGoal(tracker, today, 0))
        assertSame(tracker, NutritionRules.setWaterGoal(tracker, today, -500))
    }

    @Test
    fun `la couleur des calories suit le ratio non plafonné`() {
        assertEquals(CalorieTone.Below, NutritionRules.calorieTone(1799, 2000))
        assertEquals(CalorieTone.OnTarget, NutritionRules.calorieTone(1800, 2000)) // 0,9
        assertEquals(CalorieTone.OnTarget, NutritionRules.calorieTone(2100, 2000)) // 1,05 : pas encore au-delà
        assertEquals(CalorieTone.Over, NutritionRules.calorieTone(2101, 2000))
        // Le rouge est atteignable (🔄 D15), même très au-delà de 100 %.
        assertEquals(CalorieTone.Over, NutritionRules.calorieTone(4000, 2000))
        assertEquals(CalorieTone.Below, NutritionRules.calorieTone(500, 0))
    }

    // ─── « Pris aujourd'hui » ───────────────────────────────────────────────────────────

    @Test
    fun `basculer « pris » coche pour aujourd'hui puis décoche`() {
        var taken = mapOf("whey" to yesterday)
        assertFalse(NutritionRules.isTaken(taken, "whey", today))
        taken = NutritionRules.toggleTaken(taken, "whey", today)
        assertEquals(mapOf("whey" to today), taken)
        assertTrue(NutritionRules.isTaken(taken, "whey", today))
        taken = NutritionRules.toggleTaken(taken, "whey", today)
        assertEquals(emptyMap<String, String>(), taken)
        assertEquals(mapOf("crea" to today), NutritionRules.toggleTaken(emptyMap(), "crea", today))
    }

    @Test
    fun `le nettoyage ne garde que les prises d'aujourd'hui de compléments existants`() {
        val taken = mapOf("whey" to today, "crea" to yesterday, "supprime" to today)
        assertEquals(mapOf("whey" to today), NutritionRules.cleanTaken(taken, setOf("whey", "crea"), today))
    }

    // ─── Compléments ──────────────────────────────────────────────────────────────────

    private fun ids(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    @Test
    fun `un complément garde son dosage tel que saisi et ses rappels s'ils sont activés`() {
        val supplement = NutritionRules.newSupplement("  Créatine ", " 1,5 ", "g", true, listOf(8 to 0, 20 to 30), ids())
        assertEquals(
            Supplement("id-1", "Créatine", "1,5", "g", true, listOf(Reminder("id-2", 8, 0), Reminder("id-3", 20, 30))),
            supplement,
        )
        val silent = NutritionRules.newSupplement("Whey", "", "dose", false, listOf(8 to 0), ids())
        assertEquals(emptyList<Reminder>(), silent.reminders)
        assertFalse(silent.reminderEnabled)
    }

    @Test
    fun `un rappel ajouté suit le dernier de 2 h, même minute, en revenant après minuit`() {
        assertEquals(8 to 0, NutritionRules.nextReminderTime(null))
        assertEquals(10 to 15, NutritionRules.nextReminderTime(8 to 15))
        assertEquals(1 to 30, NutritionRules.nextReminderTime(23 to 30))
        val supplement = Supplement("s", "Oméga-3", "2", "caps", true, listOf(Reminder("r1", 22, 45)))
        assertEquals(Reminder("id-1", 0, 45), NutritionRules.addReminder(supplement, ids()).reminders.last())
        assertEquals(Reminder("id-1", 8, 0), NutritionRules.addReminder(supplement.copy(reminders = emptyList()), ids()).reminders.single())
    }
}
