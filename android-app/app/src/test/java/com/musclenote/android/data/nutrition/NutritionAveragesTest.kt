package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.DailyTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** « Moyenne sur 7 jours » de Nutrition (SPEC § 15.16.6, 🔄 D42). */
class NutritionAveragesTest {

    private val today: LocalDate = LocalDate.parse("2026-11-15")

    private fun daysAgo(n: Long) = today.minusDays(n).toString()

    private fun tracker(
        calories: Map<String, Int> = emptyMap(),
        water: Map<String, Int> = emptyMap(),
        protein: Map<String, Int> = emptyMap(),
    ) = DailyTracker.DEFAULT.copy(caloriesLog = calories, waterLog = water, proteinLog = protein)

    @Test
    fun `la moyenne porte sur les 7 jours avant aujourd'hui`() {
        val calories = (1L..7L).associate { daysAgo(it) to 2000 + it.toInt() * 100 }
        // Aujourd'hui (en cours) et le 8e jour n'entrent pas dans la fenêtre.
        val averages = NutritionRules.weekAverages(tracker(calories + (today.toString() to 500) + (daysAgo(8) to 9000)), today)
        assertEquals(2400, averages.kcal) // (2100 + … + 2700) / 7
    }

    @Test
    fun `seuls les jours avec des données comptent, mesure par mesure`() {
        val averages = NutritionRules.weekAverages(
            tracker(
                // Deux jours notés, un jour remis à zéro, quatre jours vides.
                calories = mapOf(daysAgo(1) to 2400, daysAgo(3) to 2000, daysAgo(4) to 0),
                water = mapOf(daysAgo(2) to 2100),
                protein = mapOf(daysAgo(1) to 140, daysAgo(2) to 120, daysAgo(5) to 155),
            ),
            today,
        )
        assertEquals(2200, averages.kcal)
        assertEquals(2100, averages.waterMl)
        assertEquals(138, averages.proteinGrams) // 415 / 3 = 138,33
        assertFalse(averages.isEmpty)
    }

    @Test
    fun `une mesure sans jour n'a pas de moyenne`() {
        val averages = NutritionRules.weekAverages(tracker(calories = mapOf(daysAgo(1) to 2400)), today)
        assertEquals(2400, averages.kcal)
        assertNull(averages.waterMl)
        assertNull(averages.proteinGrams)
    }

    @Test
    fun `sans aucune donnée dans la fenêtre, le groupe est masqué`() {
        assertTrue(NutritionRules.weekAverages(tracker(), today).isEmpty)
        // Seulement aujourd'hui, ou seulement des jours à 0 : rien non plus.
        val onlyToday = tracker(calories = mapOf(today.toString() to 1850), water = mapOf(daysAgo(2) to 0))
        assertTrue(NutritionRules.weekAverages(onlyToday, today).isEmpty)
    }

    @Test
    fun `la moyenne s'arrondit à l'unité, demi vers le haut`() {
        val averages = NutritionRules.weekAverages(tracker(calories = mapOf(daysAgo(1) to 2000, daysAgo(2) to 2001)), today)
        assertEquals(2001, averages.kcal) // 2000,5
        val water = NutritionRules.weekAverages(tracker(water = mapOf(daysAgo(1) to 1000, daysAgo(2) to 1000, daysAgo(3) to 1001)), today)
        assertEquals(1000, water.waterMl) // 1000,33
    }
}
