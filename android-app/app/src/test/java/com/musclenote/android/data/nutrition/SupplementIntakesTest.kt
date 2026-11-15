package com.musclenote.android.data.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Historique des prises de compléments (SPEC § 15.15.4, 🔄 D41) : un enregistrement par jour. */
class SupplementIntakesTest {

    private val today = "2026-11-15"
    private val yesterday = "2026-11-14"

    @Test
    fun `cocher enregistre une dose aujourd'hui, en gardant les jours précédents`() {
        val history = NutritionRules.recordIntake(mapOf(yesterday to 1), today, taken = true)
        assertEquals(mapOf(yesterday to 1, today to 1), history)
    }

    @Test
    fun `recocher ne crée pas de second enregistrement`() {
        var history = NutritionRules.recordIntake(emptyMap(), today, taken = true)
        history = NutritionRules.recordIntake(history, today, taken = true)
        assertEquals(mapOf(today to 1), history)
        // Un nombre de doses déjà noté n'est pas écrasé.
        assertEquals(mapOf(today to 2), NutritionRules.recordIntake(mapOf(today to 2), today, taken = true))
    }

    @Test
    fun `décocher retire la prise du jour seulement`() {
        val history = NutritionRules.recordIntake(mapOf(yesterday to 1, today to 1), today, taken = false)
        assertEquals(mapOf(yesterday to 1), history)
        assertTrue(NutritionRules.recordIntake(mapOf(today to 1), today, taken = false).isEmpty())
    }

    @Test
    fun `décocher un jour sans prise ne change rien`() {
        val history = mapOf(yesterday to 1)
        assertEquals(history, NutritionRules.recordIntake(history, today, taken = false))
    }
}
