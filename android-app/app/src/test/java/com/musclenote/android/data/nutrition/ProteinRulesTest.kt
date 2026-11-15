package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Objectif de protéines et protéines du jour (SPEC § 15.16.1 – 15.16.3, 🔄 D42). */
class ProteinRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today: LocalDate = LocalDate.parse("2026-11-15")

    private val perKg = ProteinGoal.DEFAULT
    private val fixed150 = ProteinGoal(mode = ProteinGoalMode.Fixed, perKg = 1.6, grams = 150)

    // ─── Objectif selon le poids ─────────────────────────────────────────────────────

    @Test
    fun `l'objectif par kg est le poids fois le facteur, arrondi au gramme`() {
        assertEquals(125, ProteinRules.goalFromWeight(78.4, 1.6)) // 125,44
        assertEquals(96, ProteinRules.goalFromWeight(80.0, 1.2))
        assertEquals(176, ProteinRules.goalFromWeight(80.0, 2.2))
        assertEquals(94, ProteinRules.goalFromWeight(72.5, 1.3)) // 94,25
    }

    @Test
    fun `l'arrondi se fait au demi supérieur, sans erreur de virgule flottante`() {
        assertEquals(149, ProteinRules.goalFromWeight(82.5, 1.8)) // 148,5
        // 77,5 × 1,4 vaut 108,49999… en double : le calcul exact donne 108,5 → 109.
        assertEquals(109, ProteinRules.goalFromWeight(77.5, 1.4))
        assertEquals(166, ProteinRules.goalFromWeight(75.5, 2.2)) // 166,1
        // Poids au centième (pesée saisie à 2 décimales).
        assertEquals(126, ProteinRules.goalFromWeight(78.45, 1.6)) // 125,52
    }

    @Test
    fun `avec un poids connu, le mode par kg donne l'objectif et son facteur`() {
        assertEquals(ProteinTarget(125, perKg = 1.6), ProteinRules.target(perKg, latestWeightKg = 78.4))
        assertEquals(ProteinTarget(141, perKg = 1.8), ProteinRules.target(perKg.copy(perKg = 1.8), latestWeightKg = 78.4))
    }

    @Test
    fun `la valeur fixe ne dépend pas du poids`() {
        assertEquals(ProteinTarget(150, perKg = null), ProteinRules.target(fixed150, latestWeightKg = 78.4))
        assertEquals(ProteinTarget(150, perKg = null), ProteinRules.target(fixed150, latestWeightKg = null))
    }

    @Test
    fun `sans pesée, l'objectif est la valeur fixe si elle existe, sinon il n'y en a pas`() {
        assertNull(ProteinRules.target(perKg, latestWeightKg = null))
        assertNull(ProteinRules.target(perKg, latestWeightKg = 0.0))
        // Mode par kg choisi, pesées supprimées depuis : la valeur fixe saisie avant sert.
        assertEquals(ProteinTarget(150, perKg = null), ProteinRules.target(perKg.copy(grams = 150), latestWeightKg = null))
    }

    @Test
    fun `sans pesée, seul le mode valeur fixe est proposé`() {
        assertFalse(ProteinRules.perKgAvailable(null))
        assertFalse(ProteinRules.perKgAvailable(0.0))
        assertTrue(ProteinRules.perKgAvailable(78.4))
        assertEquals(ProteinGoalMode.Fixed, ProteinRules.initialMode(perKg, latestWeightKg = null))
        assertEquals(ProteinGoalMode.PerKg, ProteinRules.initialMode(perKg, latestWeightKg = 78.4))
        assertEquals(ProteinGoalMode.Fixed, ProteinRules.initialMode(fixed150, latestWeightKg = 78.4))
    }

    @Test
    fun `la valeur fixe se préremplit avec celle enregistrée, sinon l'objectif par kg du jour`() {
        assertEquals(150, ProteinRules.fixedPrefill(fixed150, latestWeightKg = 78.4))
        assertEquals(125, ProteinRules.fixedPrefill(perKg, latestWeightKg = 78.4))
        assertNull(ProteinRules.fixedPrefill(perKg, latestWeightKg = null))
    }

    @Test
    fun `le poids utilisé est la dernière pesée connue, séance en cours comprise`() {
        val weighIn = BodyWeightEntry("2026-11-12T11:00:00.000Z", 78.4)
        val yesterday = workout("2026-11-14T17:00:00.000Z", bodyWeight = 79.0)
        val noWeight = workout("2026-11-15T07:00:00.000Z", bodyWeight = 0.0)
        assertEquals(78.4, ProteinRules.latestWeight(today, listOf(noWeight), null, listOf(weighIn), zone)!!, 0.0)
        // Le jour le plus récent l'emporte : la séance d'hier, sur la pesée d'il y a trois jours.
        assertEquals(79.0, ProteinRules.latestWeight(today, listOf(yesterday), null, listOf(weighIn), zone)!!, 0.0)
        val current = workout("2026-11-15T16:30:00.000Z", bodyWeight = 79.6)
        assertEquals(79.6, ProteinRules.latestWeight(today, listOf(yesterday), current, listOf(weighIn), zone)!!, 0.0)
        assertNull(ProteinRules.latestWeight(today, listOf(noWeight), null, emptyList(), zone))
    }

    // ─── Objectif atteint ou non ─────────────────────────────────────────────────────

    @Test
    fun `l'objectif est atteint à partir de sa valeur exacte`() {
        assertFalse(ProteinRules.reached(112, 150))
        assertTrue(ProteinRules.reached(150, 150))
        assertTrue(ProteinRules.reached(171, 150))
        assertFalse("sans objectif, jamais atteint", ProteinRules.reached(171, null))
        assertFalse(ProteinRules.reached(0, null))
    }

    @Test
    fun `l'eau suit la même règle`() {
        assertFalse(NutritionRules.waterReached(2499, 2500))
        assertTrue(NutritionRules.waterReached(2500, 2500))
    }

    // ─── Formulaire de l'objectif ────────────────────────────────────────────────────

    @Test
    fun `le facteur saisi doit être entre 1,2 et 2,2`() {
        assertEquals(1.6, ProteinRules.perKgValue("1,6")!!, 0.0)
        assertEquals(1.2, ProteinRules.perKgValue("1,2")!!, 0.0)
        assertEquals(2.2, ProteinRules.perKgValue("2.2")!!, 0.0)
        assertNull(ProteinRules.perKgValue("1,1"))
        assertNull(ProteinRules.perKgValue("2,3"))
        assertNull(ProteinRules.perKgValue(""))
    }

    @Test
    fun `les pas du facteur restent entre 1,2 et 2,2`() {
        assertEquals(1.7, ProteinRules.stepPerKg(1.6, +1), 0.0)
        assertEquals(1.5, ProteinRules.stepPerKg(1.6, -1), 0.0)
        assertEquals(2.2, ProteinRules.stepPerKg(2.2, +1), 0.0)
        assertEquals(1.2, ProteinRules.stepPerKg(1.2, -1), 0.0)
        // Champ vide (0) : le pas part de la borne basse.
        assertEquals(1.2, ProteinRules.stepPerKg(0.0, +1), 0.0)
    }

    @Test
    fun `la valeur fixe saisie doit être supérieure à 0`() {
        assertEquals(150, ProteinRules.fixedValue("150"))
        assertNull(ProteinRules.fixedValue("0"))
        assertNull(ProteinRules.fixedValue(""))
    }

    @Test
    fun `enregistrer un mode garde l'autre réglage`() {
        val fixed = ProteinRules.withFixed(perKg.copy(perKg = 1.8), 160)
        assertEquals(ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.8, grams = 160), fixed)
        assertEquals(ProteinGoal(ProteinGoalMode.PerKg, perKg = 2.0, grams = 160), ProteinRules.withPerKg(fixed, 2.0))
    }

    @Test
    fun `un réglage invalide revient aux défauts`() {
        assertEquals(ProteinGoal.DEFAULT, ProteinRules.normalized(ProteinGoal(ProteinGoalMode.PerKg, perKg = 5.0)))
        assertEquals(1.7, ProteinRules.normalized(ProteinGoal(ProteinGoalMode.PerKg, perKg = 1.65)).perKg, 0.0)
        assertNull(ProteinRules.normalized(ProteinGoal(ProteinGoalMode.PerKg, perKg = 1.6, grams = 0)).grams)
        // Valeur fixe sans grammes : le mode par kg.
        assertEquals(ProteinGoal.DEFAULT, ProteinRules.normalized(ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.6, grams = null)))
        assertEquals(fixed150, ProteinRules.normalized(fixed150))
    }

    // ─── Protéines du jour ───────────────────────────────────────────────────────────

    @Test
    fun `les protéines s'ajoutent au jour et se corrigent, jamais sous 0`() {
        var tracker = NutritionRules.addProtein(DailyTracker.DEFAULT, "2026-11-15", 40)
        tracker = NutritionRules.addProtein(tracker, "2026-11-15", 72)
        assertEquals(112, tracker.proteinLog["2026-11-15"])
        tracker = NutritionRules.setProtein(tracker, "2026-11-15", 90)
        assertEquals(90, tracker.proteinLog["2026-11-15"])
        tracker = NutritionRules.addProtein(tracker, "2026-11-15", -200)
        assertEquals(0, tracker.proteinLog["2026-11-15"])
        assertEquals(0, NutritionRules.setProtein(tracker, "2026-11-14", -5).proteinLog["2026-11-14"])
        // Les calories et l'eau ne bougent pas.
        assertTrue(tracker.caloriesLog.isEmpty() && tracker.waterLog.isEmpty())
    }

    private var nextId = 0

    private fun workout(date: String, bodyWeight: Double) =
        Workout(id = "w${++nextId}", name = "Push", date = date, bodyWeight = bodyWeight, exercises = emptyList(), durationMinutes = 60)
}
