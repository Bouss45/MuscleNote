package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.SupplementStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stock des compléments, doses par jour et jours restants (SPEC § 15.16.4, 🔄 D42). */
class SupplementStockTest {

    private val d10 = "2026-11-10"
    private val d11 = "2026-11-11"
    private val d12 = "2026-11-12"
    private val today = "2026-11-15"

    private fun creatine(
        intakes: Map<String, Int> = emptyMap(),
        stock: SupplementStock? = null,
        dosesPerDay: Int = 1,
        lowStockDays: Int = 7,
    ) = Supplement(
        id = "crea", name = "Créatine", dosage = "5", unit = "g", reminderEnabled = false, reminders = emptyList(),
        intakes = intakes, dosesPerDay = dosesPerDay, stock = stock, lowStockDays = lowStockDays,
    )

    // ─── Stock après prises et rachat ────────────────────────────────────────────────

    @Test
    fun `le stock baisse des doses prises les jours suivant la saisie`() {
        val stock = SupplementStock(doses = 30, day = d10)
        assertEquals(30, StockRules.remaining(stock, emptyMap()))
        assertEquals(28, StockRules.remaining(stock, mapOf(d11 to 1, d12 to 1)))
        assertEquals(25, StockRules.remaining(stock, mapOf(d11 to 2, d12 to 3)))
        // Une prise d'avant la saisie ne compte pas.
        assertEquals(30, StockRules.remaining(stock, mapOf("2026-11-09" to 1)))
    }

    @Test
    fun `une dose prise le jour de la saisie ne compte qu'une fois`() {
        // Prise avant la saisie : déjà dans les doses comptées.
        assertEquals(30, StockRules.remaining(SupplementStock(30, d10, dayIntake = 1), mapOf(d10 to 1)))
        // Prise après la saisie, le même jour : elle sort du stock.
        assertEquals(29, StockRules.remaining(SupplementStock(30, d10, dayIntake = 0), mapOf(d10 to 1)))
        // Prise d'avant la saisie décochée ensuite : elle revient dans le stock.
        assertEquals(31, StockRules.remaining(SupplementStock(30, d10, dayIntake = 1), emptyMap()))
    }

    @Test
    fun `saisir le stock part d'aujourd'hui, prises du jour comprises`() {
        val supplement = StockRules.setStock(creatine(intakes = mapOf(d12 to 1, today to 2)), doses = 24, today = today)
        assertEquals(SupplementStock(doses = 24, day = today, dayIntake = 2), supplement.stock)
        assertEquals(24, StockRules.status(supplement)!!.doses)
    }

    @Test
    fun `racheté ajoute les doses au stock restant`() {
        val before = creatine(intakes = mapOf(d11 to 1, d12 to 1), stock = SupplementStock(10, d10))
        assertEquals(8, StockRules.status(before)!!.doses)
        val after = StockRules.restock(before, added = 30, today = today)
        assertEquals(SupplementStock(doses = 38, day = today), after.stock)
        assertEquals(38, StockRules.status(after)!!.doses)
        // Les prises suivantes continuent de le décompter.
        assertEquals(37, StockRules.status(after.copy(intakes = after.intakes + ("2026-11-16" to 1)))!!.doses)
    }

    @Test
    fun `racheter sans stock suivi, ou ne plus le suivre`() {
        assertEquals(SupplementStock(30, today), StockRules.restock(creatine(), added = 30, today = today).stock)
        assertNull(StockRules.clearStock(creatine(stock = SupplementStock(10, d10))).stock)
        assertNull(StockRules.status(creatine()))
    }

    // ─── Jamais négatif ──────────────────────────────────────────────────────────────

    @Test
    fun `le stock ne descend jamais sous 0`() {
        val stock = SupplementStock(doses = 2, day = d10)
        assertEquals(0, StockRules.remaining(stock, mapOf(d11 to 1, d12 to 1)))
        assertEquals(0, StockRules.remaining(stock, mapOf(d11 to 1, d12 to 1, today to 1)))
        assertEquals(0, StockRules.remaining(SupplementStock(0, d10), mapOf(d11 to 3)))
    }

    @Test
    fun `racheter depuis un stock vide ne garde pas de dette`() {
        val empty = creatine(intakes = mapOf(d11 to 1, d12 to 1, today to 1), stock = SupplementStock(2, d10))
        assertEquals(0, StockRules.status(empty)!!.doses)
        val restocked = StockRules.restock(empty, added = 30, today = today)
        assertEquals(30, StockRules.status(restocked)!!.doses)
    }

    // ─── Doses par jour ──────────────────────────────────────────────────────────────

    @Test
    fun `cocher enregistre les doses par jour du complément`() {
        assertEquals(mapOf(today to 2), NutritionRules.recordIntake(emptyMap(), today, taken = true, doses = 2))
        assertEquals(mapOf(today to 1), NutritionRules.recordIntake(emptyMap(), today, taken = true))
    }

    @Test
    fun `changer les doses par jour met à jour la prise d'aujourd'hui seulement`() {
        val taken = StockRules.setDosesPerDay(creatine(intakes = mapOf(d12 to 1, today to 1)), 2, today)
        assertEquals(2, taken.dosesPerDay)
        assertEquals(mapOf(d12 to 1, today to 2), taken.intakes)
        // Pas coché aujourd'hui : l'historique ne bouge pas.
        val notTaken = StockRules.setDosesPerDay(creatine(intakes = mapOf(d12 to 1)), 3, today)
        assertEquals(mapOf(d12 to 1), notTaken.intakes)
        // Bornes 1 à 20.
        assertEquals(1, StockRules.setDosesPerDay(creatine(), 0, today).dosesPerDay)
        assertEquals(20, StockRules.setDosesPerDay(creatine(), 35, today).dosesPerDay)
    }

    // ─── Jours restants ──────────────────────────────────────────────────────────────

    @Test
    fun `les jours restants sont la partie entière du stock divisé par les doses par jour`() {
        assertEquals(24, StockRules.daysLeft(24, 1))
        assertEquals(12, StockRules.daysLeft(25, 2))
        assertEquals(0, StockRules.daysLeft(1, 2))
        assertEquals(0, StockRules.daysLeft(0, 1))
    }

    @Test
    fun `0 dose par jour ne donne pas de jours, jamais en rouge`() {
        assertNull(StockRules.daysLeft(24, 0))
        assertNull(StockRules.daysLeft(24, -1))
        val status = StockRules.status(creatine(stock = SupplementStock(24, today), dosesPerDay = 0))!!
        assertEquals(StockStatus(doses = 24, daysLeft = null, low = false), status)
    }

    @Test
    fun `le stock est bas au seuil et en dessous`() {
        assertFalse(StockRules.isLow(daysLeft = 8, threshold = 7))
        assertTrue(StockRules.isLow(daysLeft = 7, threshold = 7))
        assertTrue(StockRules.isLow(daysLeft = 4, threshold = 7))
        assertTrue("seuil 0 : rouge une fois vide", StockRules.isLow(daysLeft = 0, threshold = 0))
        assertFalse(StockRules.isLow(daysLeft = 1, threshold = 0))
        assertFalse(StockRules.isLow(daysLeft = null, threshold = 7))
    }

    @Test
    fun `l'état du stock suit le seuil du complément`() {
        val stock = SupplementStock(8, today)
        assertEquals(StockStatus(doses = 8, daysLeft = 4, low = true), StockRules.status(creatine(stock = stock, dosesPerDay = 2)))
        assertEquals(StockStatus(doses = 8, daysLeft = 8, low = false), StockRules.status(creatine(stock = stock)))
        assertEquals(StockStatus(doses = 8, daysLeft = 8, low = true), StockRules.status(creatine(stock = stock, lowStockDays = 10)))
        assertEquals(10, StockRules.setLowStockDays(creatine(), 10).lowStockDays)
        assertEquals(99, StockRules.setLowStockDays(creatine(), 150).lowStockDays)
    }

    // ─── Saisies au pavé ─────────────────────────────────────────────────────────────

    @Test
    fun `les saisies du stock sont validées`() {
        assertEquals(0, StockRules.stockValue("0"))
        assertEquals(24, StockRules.stockValue("24"))
        assertNull(StockRules.stockValue(""))
        assertEquals(30, StockRules.restockValue("30"))
        assertNull(StockRules.restockValue("0"))
        assertNull(StockRules.restockValue(""))
        assertEquals(0, StockRules.lowStockDaysValue("0"))
        assertEquals(99, StockRules.lowStockDaysValue("99"))
        assertNull(StockRules.lowStockDaysValue(""))
        assertEquals(1, StockRules.dosesPerDayValue("1"))
        assertEquals(20, StockRules.dosesPerDayValue("20"))
        assertNull(StockRules.dosesPerDayValue("0"))
        assertNull(StockRules.dosesPerDayValue("21"))
        assertNull(StockRules.dosesPerDayValue(""))
    }
}
