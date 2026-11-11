package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pesées manuelles (§ 3.11). */
class BodyWeightRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    @Test
    fun `une pesée est datée à midi local du jour choisi`() {
        // Hiver : midi à Paris = 11 h UTC ; été : 10 h UTC.
        val winter = BodyWeightRules.add(emptyList(), LocalDate.parse("2026-01-15"), 80.0, zone)
        assertEquals(listOf(BodyWeightEntry("2026-01-15T11:00:00.000Z", 80.0)), winter)
        assertEquals("2026-07-15T10:00:00.000Z", BodyWeightRules.add(emptyList(), LocalDate.parse("2026-07-15"), 80.0, zone).single().date)
    }

    @Test
    fun `une pesée remplace celle du même jour local et la liste reste triée`() {
        val entries = listOf(
            BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0),
            BodyWeightEntry("2026-03-10T22:30:00.000Z", 80.0), // 23 h 30 le 10 à Paris
            BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0),
        )
        val next = BodyWeightRules.add(entries, LocalDate.parse("2026-03-10"), 79.5, zone)
        assertEquals(
            listOf(
                BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0),
                BodyWeightEntry("2026-03-10T11:00:00.000Z", 79.5),
                BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0),
            ),
            next,
        )
    }

    @Test
    fun `suppression par égalité exacte de la date`() {
        val entries = listOf(BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0), BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0))
        assertEquals(listOf(entries[1]), BodyWeightRules.remove(entries, "2026-03-14T11:00:00.000Z"))
        assertEquals(entries, BodyWeightRules.remove(entries, "2026-03-14T11:00:00Z"))
    }

    @Test
    fun `le poids doit être compris entre 0 exclu et 500`() {
        assertFalse(BodyWeightRules.isValidWeight(0.0))
        assertFalse(BodyWeightRules.isValidWeight(-70.0))
        assertTrue(BodyWeightRules.isValidWeight(0.1))
        assertTrue(BodyWeightRules.isValidWeight(500.0))
        assertFalse(BodyWeightRules.isValidWeight(500.1))
    }
}
