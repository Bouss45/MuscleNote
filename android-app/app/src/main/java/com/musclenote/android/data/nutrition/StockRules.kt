package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.SupplementStock

/**
 * État du stock d'un complément (§ 15.16.4) : [doses] restantes, [daysLeft] (`null` sans doses par
 * jour valables), [low] = stock bas (jours restants ≤ seuil).
 */
data class StockStatus(val doses: Int, val daysLeft: Int?, val low: Boolean)

/**
 * Stock des compléments (SPEC § 15.16.4, § 15.16.8, 🔄 D42), en fonctions pures : doses saisies un
 * jour donné, décomptées par l'historique des prises, jamais sous 0 ; jours restants et stock bas.
 */
object StockRules {
    const val MIN_DOSES_PER_DAY = 1
    const val MAX_DOSES_PER_DAY = 20
    const val MAX_LOW_STOCK_DAYS = 99

    /**
     * Stock restant : `max(0, doses − (intakes[day] − dayIntake) − Σ intakes[d > day])` — les doses
     * prises depuis la saisie (une dose déjà prise ce jour-là n'est pas retirée deux fois).
     */
    fun remaining(stock: SupplementStock, intakes: Map<String, Int>): Int {
        val sameDay = (intakes[stock.day] ?: 0) - stock.dayIntake
        val after = intakes.entries.sumOf { (day, doses) -> if (day > stock.day) doses.toLong() else 0L }
        return (stock.doses - sameDay - after).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Jours restants : partie entière de [doses] / [dosesPerDay] ; `null` si les doses par jour sont ≤ 0. */
    fun daysLeft(doses: Int, dosesPerDay: Int): Int? = if (dosesPerDay <= 0) null else doses / dosesPerDay

    /** Stock bas : jours restants au seuil ou en dessous (« à 7 jours » = rouge dès 7). */
    fun isLow(daysLeft: Int?, threshold: Int): Boolean = daysLeft != null && daysLeft <= threshold

    /** État du stock du complément, `null` s'il n'est pas suivi. */
    fun status(supplement: Supplement): StockStatus? {
        val stock = supplement.stock ?: return null
        val doses = remaining(stock, supplement.intakes)
        val days = daysLeft(doses, supplement.dosesPerDay)
        return StockStatus(doses, days, isLow(days, supplement.lowStockDays))
    }

    /** Stock saisi aujourd'hui ([today]) : il compte les prises de ce jour déjà enregistrées. */
    fun setStock(supplement: Supplement, doses: Int, today: String): Supplement =
        supplement.copy(stock = SupplementStock(doses.coerceAtLeast(0), today, dayIntake = supplement.intakes[today] ?: 0))

    /** « Racheté » : [added] doses s'ajoutent au stock restant (0 sans stock suivi), à partir d'aujourd'hui. */
    fun restock(supplement: Supplement, added: Int, today: String): Supplement {
        val current = supplement.stock?.let { remaining(it, supplement.intakes) } ?: 0
        return setStock(supplement, current + added.coerceAtLeast(0), today)
    }

    /** « Ne plus suivre le stock ». */
    fun clearStock(supplement: Supplement): Supplement = supplement.copy(stock = null)

    /**
     * Doses par jour, bornées à 1 – 20 ; si le complément est pris aujourd'hui ([today]), la prise du jour
     * prend la nouvelle valeur (les jours passés gardent la leur).
     */
    fun setDosesPerDay(supplement: Supplement, dosesPerDay: Int, today: String): Supplement {
        val doses = dosesPerDay.coerceIn(MIN_DOSES_PER_DAY, MAX_DOSES_PER_DAY)
        val intakes = if ((supplement.intakes[today] ?: 0) >= 1) supplement.intakes + (today to doses) else supplement.intakes
        return supplement.copy(dosesPerDay = doses, intakes = intakes)
    }

    /** Seuil du stock bas, borné à 0 – 99 jours. */
    fun setLowStockDays(supplement: Supplement, days: Int): Supplement =
        supplement.copy(lowStockDays = days.coerceIn(0, MAX_LOW_STOCK_DAYS))

    /** Doses par jour lues dans un fichier : hors 1 – 20 → 1. */
    fun normalizedDosesPerDay(value: Int?): Int =
        value?.takeIf { it in MIN_DOSES_PER_DAY..MAX_DOSES_PER_DAY } ?: Supplement.DEFAULT_DOSES_PER_DAY

    /** Seuil lu dans un fichier : hors 0 – 99 → 7. */
    fun normalizedLowStockDays(value: Int?): Int =
        value?.takeIf { it in 0..MAX_LOW_STOCK_DAYS } ?: Supplement.DEFAULT_LOW_STOCK_DAYS

    // ─── Saisies au pavé (§ 15.16.5) ──────────────────────────────────────────────────

    /** Stock saisi : champ non vide, 0 compris. */
    fun stockValue(text: String): Int? = text.toIntOrNull()?.takeIf { it >= 0 }

    /** Doses rachetées : > 0. */
    fun restockValue(text: String): Int? = text.toIntOrNull()?.takeIf { it > 0 }

    /** Seuil du stock bas : 0 à 99 jours. */
    fun lowStockDaysValue(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..MAX_LOW_STOCK_DAYS }

    /** Doses par jour : 1 à 20. */
    fun dosesPerDayValue(text: String): Int? = text.toIntOrNull()?.takeIf { it in MIN_DOSES_PER_DAY..MAX_DOSES_PER_DAY }
}
