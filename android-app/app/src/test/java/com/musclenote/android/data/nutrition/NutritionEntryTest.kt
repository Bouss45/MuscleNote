package com.musclenote.android.data.nutrition

import com.musclenote.android.data.workout.SetEntryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Saisie au pavé des calories, de l'eau et des objectifs (SPEC § 15.10.2, § 15.10.5, 🔄 D35). */
class NutritionEntryTest {

    private val fr: Locale = Locale.FRENCH
    private val en: Locale = Locale.ENGLISH

    private fun type(text: String, key: Char, entry: NutritionEntry, fresh: Boolean = false, locale: Locale = fr) =
        SetEntryRules.type(text, fresh, key, entry, locale)

    // ─── Saisie ──────────────────────────────────────────────────────────────────────

    @Test
    fun `kcal et ml sont des entiers, le séparateur est sans effet`() {
        assertNull(type("350", ',', NutritionEntry.Calories))
        assertNull(type("", ',', NutritionEntry.Calories))
        assertNull(type("250", '.', NutritionEntry.Water, locale = en))
        assertEquals("3505", type("350", '5', NutritionEntry.Calories))
    }

    @Test
    fun `kcal et ml s'arrêtent à 5 chiffres, sans zéro en tête`() {
        assertEquals("12345", type("1234", '5', NutritionEntry.Calories))
        assertNull(type("12345", '6', NutritionEntry.Calories))
        assertNull(type("99999", '0', NutritionEntry.Water))
        assertEquals("7", type("0", '7', NutritionEntry.Water))
        assertEquals("100", type("10", '0', NutritionEntry.CalorieGoal))
    }

    @Test
    fun `un champ neuf est remplacé par la première touche`() {
        assertEquals("2", type("1850", '2', NutritionEntry.Calories, fresh = true))
        assertEquals("", SetEntryRules.backspace("1850", fresh = true))
    }

    @Test
    fun `l'objectif d'eau en litres prend le séparateur de la langue et 2 décimales`() {
        assertEquals("2,", type("2", ',', NutritionEntry.WaterGoal))
        assertEquals("2,25", type("2,2", '5', NutritionEntry.WaterGoal))
        assertNull(type("2,25", '0', NutritionEntry.WaterGoal))
        assertEquals("0.", type("", '.', NutritionEntry.WaterGoal, locale = en))
        assertNull(type("2,5", ',', NutritionEntry.WaterGoal))
        assertEquals("12,25", type("12,2", '5', NutritionEntry.WaterGoal))
        assertNull(type("12,25", '5', NutritionEntry.WaterGoal))
    }

    @Test
    fun `les pas ajoutent 50 kcal, 250 ml, 100 kcal ou 0,25 L, jamais sous 0`() {
        assertEquals(50.0, SetEntryRules.step(0.0, +1, NutritionEntry.Calories, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(30.0, -1, NutritionEntry.Calories, fr)!!, 0.0)
        assertEquals(500.0, SetEntryRules.step(250.0, +1, NutritionEntry.Water, fr)!!, 0.0)
        assertEquals(2700.0, SetEntryRules.step(2800.0, -1, NutritionEntry.CalorieGoal, fr)!!, 0.0)
        assertEquals(2.25, SetEntryRules.step(2.0, +1, NutritionEntry.WaterGoal, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(0.1, -1, NutritionEntry.WaterGoal, fr)!!, 0.0)
    }

    @Test
    fun `un pas sans effet s'il dépasserait 5 chiffres`() {
        assertNull(SetEntryRules.step(99990.0, +1, NutritionEntry.Calories, fr))
        assertEquals(99740.0, SetEntryRules.step(99990.0, -1, NutritionEntry.Water, fr)!!, 0.0)
        assertNull(SetEntryRules.step(99.9, +1, NutritionEntry.WaterGoal, fr))
    }

    @Test
    fun `les valeurs s'affichent sans décimales inutiles`() {
        assertEquals("1850", SetEntryRules.format(1850.0, NutritionEntry.Calories, fr))
        assertEquals("2", NutritionRules.waterGoalText(2000, fr))
        assertEquals("2,5", NutritionRules.waterGoalText(2500, fr))
        assertEquals("2.25", NutritionRules.waterGoalText(2250, en))
    }

    // ─── Validation ──────────────────────────────────────────────────────────────────

    @Test
    fun `« Ajouter » demande une quantité supérieure à 0`() {
        assertEquals(350, NutritionRules.entryValue(EntryMode.Add, "350"))
        assertNull(NutritionRules.entryValue(EntryMode.Add, "0"))
        assertNull(NutritionRules.entryValue(EntryMode.Add, ""))
    }

    @Test
    fun `« Total du jour » accepte 0 mais pas un champ vide`() {
        assertEquals(1800, NutritionRules.entryValue(EntryMode.Total, "1800"))
        assertEquals(0, NutritionRules.entryValue(EntryMode.Total, "0"))
        assertNull(NutritionRules.entryValue(EntryMode.Total, ""))
    }

    @Test
    fun `le nouveau total cumule en « Ajouter » et remplace en « Total du jour »`() {
        assertEquals(2200, NutritionRules.newTotal(1850, EntryMode.Add, 350))
        assertEquals(1850, NutritionRules.newTotal(1850, EntryMode.Add, 0))
        assertEquals(500, NutritionRules.newTotal(1850, EntryMode.Total, 500))
        assertEquals(0, NutritionRules.newTotal(1850, EntryMode.Total, 0))
    }

    @Test
    fun `un objectif doit être supérieur à 0, l'eau saisie en litres est stockée en ml`() {
        assertEquals(2800, NutritionRules.calorieGoalValue("2800"))
        assertNull(NutritionRules.calorieGoalValue("0"))
        assertNull(NutritionRules.calorieGoalValue(""))
        assertEquals(2500, NutritionRules.waterGoalValue("2,5"))
        assertEquals(2250, NutritionRules.waterGoalValue("2.25"))
        assertEquals(500, NutritionRules.waterGoalValue("0,5"))
        assertNull(NutritionRules.waterGoalValue("0"))
        assertNull(NutritionRules.waterGoalValue("0,"))
        assertNull(NutritionRules.waterGoalValue(""))
    }
}
