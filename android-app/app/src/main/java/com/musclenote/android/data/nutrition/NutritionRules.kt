package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.workout.EntryFormat
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.data.util.dayKey
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

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
 * État des calories du jour (SPEC § 15.10.1) : ton de [NutritionRules.calorieTone], kcal qui
 * restent (ton [CalorieTone.Below]) ou dépassement (ton [CalorieTone.Over]), 0 sinon ; [progress]
 * = remplissage de la piste, plafonné à 1.
 */
data class CalorieStatus(val tone: CalorieTone, val remaining: Int, val excess: Int, val progress: Float)

/**
 * Moyennes sur les 7 jours avant aujourd'hui (§ 15.16.6, 🔄 D42), chacune sur ses seuls jours > 0 ;
 * `null` = aucun jour pour cette mesure.
 */
data class NutritionAverages(val kcal: Int?, val waterMl: Int?, val proteinGrams: Int?) {
    /** Aucune mesure n'a de jour : le groupe « Moyenne sur 7 jours » est masqué. */
    val isEmpty: Boolean get() = kcal == null && waterMl == null && proteinGrams == null
}

/** Mode d'un formulaire Calories, Eau ou Protéines (§ 15.10.2) : ajouter au jour, ou fixer son total. */
enum class EntryMode { Add, Total }

/**
 * Quantités de Nutrition saisies au pavé numérique (§ 15.10.5) : kcal et ml entiers, objectif d'eau
 * en litres (2 décimales).
 */
enum class NutritionEntry(override val maxLength: Int, override val decimals: Boolean, override val step: Double) : EntryFormat {
    Calories(5, false, 50.0),
    Water(5, false, 250.0),
    CalorieGoal(5, false, 100.0),
    WaterGoal(5, true, 0.25),

    /** Protéines du jour (🔄 D42, § 15.16.3) : grammes entiers, pas ±5. */
    Protein(4, false, 5.0),

    /** Valeur fixe de l'objectif de protéines : pas ±10 g. */
    ProteinFixed(4, false, 10.0),

    /** Facteur de l'objectif de protéines : une décimale (« 1,6 »), pas ±0,1 (bornés par [ProteinRules]). */
    ProteinPerKg(3, true, 0.1),

    /** Stock d'un complément et doses rachetées (§ 15.16.5) : pas ±10 doses. */
    Stock(4, false, 10.0),

    /** Seuil du stock bas, en jours, et doses par jour : pas ±1. */
    SmallCount(2, false, 1.0),
}

/** Résumé des rappels d'un complément (§ 2.11) : sous-titre de sa ligne. */
sealed interface ReminderSummary {
    /** Rappels désactivés : « Pas de rappel ». */
    data object Off : ReminderSummary

    /** Activés, aucun rappel : « Rappels activés ». */
    data object On : ReminderSummary

    /** Un seul rappel : « Rappel à 08:00 ». */
    data class At(val hour: Int, val minute: Int) : ReminderSummary

    /** Plusieurs : « 3 rappels ». */
    data class Count(val count: Int) : ReminderSummary
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

    /** `log[day] = max(0, ancien + delta)` en ml. */
    fun addWater(tracker: DailyTracker, day: String, deltaMl: Int): DailyTracker =
        tracker.copy(waterLog = tracker.waterLog.withDay(day, (tracker.waterLog[day] ?: 0) + deltaMl))

    /** « Total du jour » de l'eau (🔄 D35) : `log[day] = max(0, ml)` ; 0 = l'ancienne remise à zéro. */
    fun setWater(tracker: DailyTracker, day: String, ml: Int): DailyTracker =
        tracker.copy(waterLog = tracker.waterLog.withDay(day, ml))

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

    /** État des calories du jour par rapport à [goal] (surface « Aujourd'hui », § 15.10.1). */
    fun calorieStatus(kcal: Int, goal: Int): CalorieStatus {
        val tone = calorieTone(kcal, goal)
        val progress = if (goal > 0) (kcal.toFloat() / goal).coerceIn(0f, 1f) else 0f
        return CalorieStatus(
            tone = tone,
            remaining = if (tone == CalorieTone.Below && goal > 0) (goal - kcal).coerceAtLeast(0) else 0,
            excess = if (tone == CalorieTone.Over) kcal - goal else 0,
            progress = progress,
        )
    }

    /** Protéines du jour += [amount] g (🔄 D42), résultat ≥ 0. */
    fun addProtein(tracker: DailyTracker, day: String, amount: Int): DailyTracker =
        tracker.copy(proteinLog = tracker.proteinLog.withDay(day, (tracker.proteinLog[day] ?: 0) + amount))

    /** « Total du jour » des protéines : `log[day] = max(0, grams)`. */
    fun setProtein(tracker: DailyTracker, day: String, grams: Int): DailyTracker =
        tracker.copy(proteinLog = tracker.proteinLog.withDay(day, grams))

    /** Objectif d'eau du jour atteint (§ 2.11). */
    fun waterReached(ml: Int, goalMl: Int): Boolean = ml >= goalMl

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

    /**
     * Historique des prises (§ 15.15.4, 🔄 D41) après avoir coché ([taken]) ou décoché le complément
     * le jour [day] : [doses] ce jour-là — ses doses par jour, 🔄 D42 — (un enregistrement existant est
     * gardé, jamais doublé), ou plus de prise ce jour-là. Les autres jours ne bougent pas.
     */
    fun recordIntake(intakes: Map<String, Int>, day: String, taken: Boolean, doses: Int = 1): Map<String, Int> = when {
        taken -> if ((intakes[day] ?: 0) >= 1) intakes else intakes + (day to doses.coerceAtLeast(1))
        else -> intakes - day
    }

    /** Compléments existants pris aujourd'hui (« 1/3 pris », § 15.10.1). */
    fun takenCount(supplements: List<Supplement>, taken: TakenRecord, today: String): Int =
        supplements.count { isTaken(taken, it.id, today) }

    // ─── Moyenne sur 7 jours (§ 15.16.6, 🔄 D42) ───────────────────────────────────────

    /** Jours de la moyenne : de J − 7 à J − 1 (aujourd'hui, en cours, n'y entre pas). */
    private const val AVERAGE_DAYS = 7L

    /** Calories, eau et protéines moyennes des 7 jours avant [today], chacune sur ses jours > 0. */
    fun weekAverages(tracker: DailyTracker, today: LocalDate): NutritionAverages {
        val days = (1..AVERAGE_DAYS).map { dayKey(today.minusDays(it)) }
        return NutritionAverages(
            kcal = average(tracker.caloriesLog, days),
            waterMl = average(tracker.waterLog, days),
            proteinGrams = average(tracker.proteinLog, days),
        )
    }

    /** Moyenne des valeurs > 0 de [log] sur [days], arrondie à l'unité (demi vers le haut). */
    private fun average(log: Map<String, Int>, days: List<String>): Int? {
        val values = days.mapNotNull { day -> log[day]?.takeIf { it > 0 } }
        if (values.isEmpty()) return null
        val sum = values.sumOf { it.toLong() }
        return ((2 * sum + values.size) / (2L * values.size)).toInt()
    }

    // ─── Saisie au pavé (§ 15.10.5) ───────────────────────────────────────────────────

    /**
     * Valeur d'un formulaire Calories ou Eau, `null` si elle est refusée : « Ajouter » > 0, « Total
     * du jour » champ non vide (0 remet le jour à zéro).
     */
    fun entryValue(mode: EntryMode, text: String): Int? {
        if (text.isEmpty()) return null
        val value = text.toIntOrNull() ?: return null
        return when (mode) {
            EntryMode.Add -> value.takeIf { it > 0 }
            EntryMode.Total -> value.takeIf { it >= 0 }
        }
    }

    /** Total du jour après la saisie (« Nouveau total ») ; borné à 0 comme les journaux. */
    fun newTotal(current: Int, mode: EntryMode, value: Int): Int = when (mode) {
        EntryMode.Add -> (current + value).coerceAtLeast(0)
        EntryMode.Total -> value.coerceAtLeast(0)
    }

    /** Objectif de calories saisi, `null` s'il n'est pas > 0. */
    fun calorieGoalValue(text: String): Int? = text.toIntOrNull()?.takeIf { it > 0 }

    /** Objectif d'eau saisi en litres → ml (`round(v × 1000)`), `null` s'il n'est pas > 0. */
    fun waterGoalValue(text: String): Int? {
        if (text.isEmpty()) return null
        return (parseDecimal(text) * 1000).roundToInt().takeIf { it > 0 }
    }

    /** Objectif d'eau prérempli en litres (« 2 », « 2,5 », « 2,25 »). */
    fun waterGoalText(goalMl: Int, locale: Locale): String =
        SetEntryRules.format(goalMl / 1000.0, NutritionEntry.WaterGoal, locale)

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

    /** Résumé des rappels d'un complément (§ 2.11). */
    fun reminderSummary(supplement: Supplement): ReminderSummary {
        val reminders = supplement.reminders
        return when {
            !supplement.reminderEnabled -> ReminderSummary.Off
            reminders.isEmpty() -> ReminderSummary.On
            reminders.size == 1 -> ReminderSummary.At(reminders[0].hour, reminders[0].minute)
            else -> ReminderSummary.Count(reminders.size)
        }
    }

    fun addReminder(supplement: Supplement, newId: () -> String): Supplement {
        val (hour, minute) = nextReminderTime(supplement.reminders.lastOrNull()?.let { it.hour to it.minute })
        return supplement.copy(reminders = supplement.reminders + Reminder(newId(), hour, minute))
    }
}
