package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.parseDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Objectif de protéines du jour (§ 15.16.1) : [grams] ; [perKg] = le facteur quand il vient du poids
 * (mode « par kg »), `null` pour une valeur fixe.
 */
data class ProteinTarget(val grams: Int, val perKg: Double?)

/**
 * Objectif de protéines (SPEC § 15.16.1 – 15.16.3, 🔄 D42), en fonctions pures : par kg de la dernière
 * pesée connue (facteur de 1,2 à 2,2), ou valeur fixe ; sans pesée, la valeur fixe seule. Non daté.
 */
object ProteinRules {
    const val MIN_PER_KG = ProteinGoal.MIN_PER_KG
    const val MAX_PER_KG = ProteinGoal.MAX_PER_KG
    private const val PER_KG_STEP = 0.1

    /**
     * Poids × facteur arrondi au gramme, demi vers le haut, calculé exactement : poids au centième ×
     * facteur au dixième, en millièmes de gramme entiers (77,5 × 1,4 = 108,5 → 109, pas 108).
     */
    fun goalFromWeight(weightKg: Double, perKg: Double): Int {
        val hundredthsKg = (weightKg * 100).roundToLong()
        val tenths = (perKg * 10).roundToLong()
        return ((hundredthsKg * tenths + 500) / 1000).toInt()
    }

    /** Le mode « par kg » demande un poids connu. */
    fun perKgAvailable(latestWeightKg: Double?): Boolean = latestWeightKg != null && latestWeightKg > 0

    /**
     * Objectif du jour : par kg avec le poids [latestWeightKg] ; valeur fixe ; « par kg » sans poids →
     * la valeur fixe si elle a été saisie ; sinon `null` (pas d'objectif).
     */
    fun target(goal: ProteinGoal, latestWeightKg: Double?): ProteinTarget? {
        val fixed = goal.grams?.takeIf { it > 0 }
        return when {
            goal.mode == ProteinGoalMode.Fixed && fixed != null -> ProteinTarget(fixed, perKg = null)
            perKgAvailable(latestWeightKg) -> ProteinTarget(goalFromWeight(latestWeightKg!!, goal.perKg), perKg = goal.perKg)
            fixed != null -> ProteinTarget(fixed, perKg = null)
            else -> null
        }
    }

    /** Objectif atteint : il y en a un, et les grammes du jour l'égalent ou le dépassent. */
    fun reached(grams: Int, target: Int?): Boolean = target != null && grams >= target

    /**
     * Dernière pesée connue au jour [today] (§ 15.13.1) : pesée manuelle, sinon poids saisi au début
     * d'une séance — séances terminées [workouts] et séance en cours [current] —, le jour le plus récent.
     */
    fun latestWeight(today: LocalDate, workouts: List<Workout>, current: Workout?, weighIns: List<BodyWeightEntry>, zone: ZoneId): Double? =
        BodyWeightRules.latestKnown(today, if (current == null) workouts else workouts + current, weighIns, zone)?.weight

    // ─── Formulaire de l'objectif (§ 15.16.3) ─────────────────────────────────────────

    /** Puce choisie à l'ouverture : le mode enregistré, « Valeur fixe » sans poids connu. */
    fun initialMode(goal: ProteinGoal, latestWeightKg: Double?): ProteinGoalMode =
        if (perKgAvailable(latestWeightKg)) goal.mode else ProteinGoalMode.Fixed

    /** Valeur fixe préremplie : celle enregistrée, sinon l'objectif par kg du jour, sinon rien. */
    fun fixedPrefill(goal: ProteinGoal, latestWeightKg: Double?): Int? =
        goal.grams?.takeIf { it > 0 }
            ?: latestWeightKg?.takeIf { perKgAvailable(it) }?.let { goalFromWeight(it, goal.perKg) }

    /** Facteur saisi, au dixième, `null` hors de 1,2 – 2,2 ou champ vide. */
    fun perKgValue(text: String): Double? {
        if (text.isBlank()) return null
        return tenth(parseDecimal(text)).takeIf { it in MIN_PER_KG..MAX_PER_KG }
    }

    /** Pas ±0,1 du facteur, bornés à 1,2 et 2,2 (un champ vide part de la borne basse). */
    fun stepPerKg(value: Double, direction: Int): Double =
        tenth(value + direction * PER_KG_STEP).coerceIn(MIN_PER_KG, MAX_PER_KG)

    /** Valeur fixe saisie, `null` si elle n'est pas > 0. */
    fun fixedValue(text: String): Int? = text.toIntOrNull()?.takeIf { it > 0 }

    /** « Enregistrer » en mode « par kg » : le facteur change, la valeur fixe est gardée. */
    fun withPerKg(goal: ProteinGoal, perKg: Double): ProteinGoal = goal.copy(mode = ProteinGoalMode.PerKg, perKg = perKg)

    /** « Enregistrer » en mode « valeur fixe » : la valeur change, le facteur est gardé. */
    fun withFixed(goal: ProteinGoal, grams: Int): ProteinGoal = goal.copy(mode = ProteinGoalMode.Fixed, grams = grams)

    /**
     * Réglage ramené aux règles (§ 15.16.8) : facteur hors bornes → 1,6, sinon au dixième ; valeur fixe
     * ≤ 0 → absente ; mode « valeur fixe » sans valeur → « par kg ».
     */
    fun normalized(goal: ProteinGoal): ProteinGoal {
        val perKg = tenth(goal.perKg).takeIf { goal.perKg.isFinite() && it in MIN_PER_KG..MAX_PER_KG } ?: ProteinGoal.DEFAULT_PER_KG
        val grams = goal.grams?.takeIf { it > 0 }
        val mode = if (goal.mode == ProteinGoalMode.Fixed && grams == null) ProteinGoalMode.PerKg else goal.mode
        return ProteinGoal(mode, perKg, grams)
    }

    private fun tenth(value: Double): Double = (value * 10).roundToInt() / 10.0
}
