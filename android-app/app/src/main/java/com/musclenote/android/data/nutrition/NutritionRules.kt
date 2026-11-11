package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord

/** Couleur de la carte Calories selon le ratio kcal / objectif (§ 2.11). */
enum class CalorieTone {
    /** Ratio > 1,05 : `destructive`. */
    Over,

    /** Ratio ≥ 0,9 : `success`. */
    OnTarget,

    /** Sinon : `primary`. */
    Below,
}

/**
 * Règles du suivi du jour et des compléments (SPEC § 2.11, § 3.12), en fonctions pures testables
 * sur la JVM. Les jours sont des clés locales `"AAAA-MM-JJ"` (§ 10.2).
 */
object NutritionRules {
    /** Unités proposées pour un complément (non traduites). */
    val UNITS = listOf("g", "mg", "ml", "caps", "cp", "dose", "ui")

    /** Premier rappel d'un complément : 08:00. */
    const val DEFAULT_REMINDER_HOUR = 8

    // ─── Calories et eau ─────────────────────────────────────────────────────────────

    /** `log[day] = max(0, ancien + amount)`. */
    fun addCalories(tracker: DailyTracker, day: String, amount: Int): DailyTracker =
        tracker.copy(caloriesLog = tracker.caloriesLog.withDay(day, (tracker.caloriesLog[day] ?: 0) + amount))

    /** `log[day] = max(0, value)` (« = Définir », ⟲ avec 0). */
    fun setCalories(tracker: DailyTracker, day: String, value: Int): DailyTracker =
        tracker.copy(caloriesLog = tracker.caloriesLog.withDay(day, value))

    /** `log[day] = max(0, ancien + delta)` en ml ; ⟲ = `addWater(−valeur du jour)`. */
    fun addWater(tracker: DailyTracker, day: String, deltaMl: Int): DailyTracker =
        tracker.copy(waterLog = tracker.waterLog.withDay(day, (tracker.waterLog[day] ?: 0) + deltaMl))

    /**
     * Objectif de calories à partir du jour [today] (🔄 D15) : `history[today] = kcal` (remplace une
     * entrée du même jour), objectif courant mis à jour. Valeur ≤ 0 ignorée.
     */
    fun setCalorieGoal(tracker: DailyTracker, today: String, kcal: Int): DailyTracker {
        if (kcal <= 0) return tracker
        return tracker.copy(calorieGoal = kcal, calorieGoalHistory = tracker.calorieGoalHistory + (today to kcal))
    }

    /** Objectif d'eau (ml) à partir du jour [today] ; valeur ≤ 0 ignorée. */
    fun setWaterGoal(tracker: DailyTracker, today: String, ml: Int): DailyTracker {
        if (ml <= 0) return tracker
        return tracker.copy(waterGoal = ml, waterGoalHistory = tracker.waterGoalHistory + (today to ml))
    }

    /** Couleur de la carte Calories, sur le ratio **non plafonné** (🔄 D15). */
    fun calorieTone(kcal: Int, goal: Int): CalorieTone {
        val ratio = if (goal > 0) kcal.toDouble() / goal else 0.0
        return when {
            ratio > 1.05 -> CalorieTone.Over
            ratio >= 0.9 -> CalorieTone.OnTarget
            else -> CalorieTone.Below
        }
    }

    private fun Map<String, Int>.withDay(day: String, value: Int): Map<String, Int> = this + (day to value.coerceAtLeast(0))

    // ─── « Pris aujourd'hui » ───────────────────────────────────────────────────────────

    fun isTaken(taken: TakenRecord, supplementId: String, today: String): Boolean = taken[supplementId] == today

    /** Bascule : pris aujourd'hui → entrée supprimée ; sinon elle vaut aujourd'hui. */
    fun toggleTaken(taken: TakenRecord, supplementId: String, today: String): TakenRecord =
        if (isTaken(taken, supplementId, today)) taken - supplementId else taken + (supplementId to today)

    /**
     * Nettoyage (🔄 D15) : seules restent les entrées d'un complément existant prises aujourd'hui
     * (au chargement), et l'entrée d'un complément supprimé disparaît.
     */
    fun cleanTaken(taken: TakenRecord, supplementIds: Set<String>, today: String): TakenRecord =
        taken.filter { (id, day) -> id in supplementIds && day == today }

    // ─── Compléments et rappels ─────────────────────────────────────────────────────────

    /**
     * Nouveau complément (§ 2.11). Nom et dosage `trim()`, dosage gardé tel que saisi (`1,5`).
     * Rappels désactivés → créé **sans** rappels.
     */
    fun newSupplement(
        name: String,
        dosage: String,
        unit: String,
        remindersEnabled: Boolean,
        times: List<Pair<Int, Int>>,
        newId: () -> String,
    ): Supplement = Supplement(
        id = newId(),
        name = name.trim(),
        dosage = dosage.trim(),
        unit = unit,
        reminderEnabled = remindersEnabled,
        reminders = if (remindersEnabled) times.map { (hour, minute) -> Reminder(newId(), hour, minute) } else emptyList(),
    )

    /** Heure d'un rappel ajouté : celle du dernier + 2 h (modulo 24), même minute ; 08:00 sinon. */
    fun nextReminderTime(last: Pair<Int, Int>?): Pair<Int, Int> =
        if (last == null) DEFAULT_REMINDER_HOUR to 0 else (last.first + 2) % 24 to last.second

    fun addReminder(supplement: Supplement, newId: () -> String): Supplement {
        val (hour, minute) = nextReminderTime(supplement.reminders.lastOrNull()?.let { it.hour to it.minute })
        return supplement.copy(reminders = supplement.reminders + Reminder(newId(), hour, minute))
    }
}
