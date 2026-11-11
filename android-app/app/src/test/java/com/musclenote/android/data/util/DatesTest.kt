package com.musclenote.android.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Dates saisies par l'utilisateur (§ 10.3). */
class DatesTest {

    @Test
    fun `buildLocalDate construit la date-heure locale saisie`() {
        assertEquals(LocalDateTime.of(2026, 3, 14, 18, 5), buildLocalDate(2026, 3, 14, 18, 5))
        assertEquals(LocalDateTime.of(2026, 12, 31, 0, 0), buildLocalDate(2026, 12, 31, 0, 0))
        assertEquals(LocalDateTime.of(2026, 1, 1, 23, 59), buildLocalDate(2026, 1, 1, 23, 59))
    }

    @Test
    fun `sans heure, buildLocalDate prend midi`() {
        assertEquals(LocalDateTime.of(2026, 3, 14, 12, 0), buildLocalDate(2026, 3, 14))
    }

    @Test
    fun `buildLocalDate refuse les dates qui n'existent pas au lieu de les reporter`() {
        assertNull(buildLocalDate(2026, 2, 31))
        assertNull(buildLocalDate(2026, 2, 29)) // 2026 n'est pas bissextile
        assertEquals(LocalDateTime.of(2028, 2, 29, 12, 0), buildLocalDate(2028, 2, 29))
        assertNull(buildLocalDate(2026, 4, 31))
        assertNull(buildLocalDate(2026, 13, 1))
        assertNull(buildLocalDate(2026, 0, 10))
        assertNull(buildLocalDate(2026, 3, 0))
        assertNull(buildLocalDate(2026, 3, 32))
    }

    @Test
    fun `buildLocalDate borne l'année, l'heure et la minute`() {
        assertNull(buildLocalDate(1999, 12, 31))
        assertEquals(LocalDateTime.of(2000, 1, 1, 12, 0), buildLocalDate(2000, 1, 1))
        assertEquals(LocalDateTime.of(2100, 12, 31, 12, 0), buildLocalDate(2100, 12, 31))
        assertNull(buildLocalDate(2101, 1, 1))
        assertNull(buildLocalDate(26, 3, 14))
        assertNull(buildLocalDate(2026, 3, 14, 24, 0))
        assertNull(buildLocalDate(2026, 3, 14, -1, 0))
        assertNull(buildLocalDate(2026, 3, 14, 12, 60))
        assertNull(buildLocalDate(2026, 3, 14, 12, -1))
    }

    @Test
    fun `buildLocalDate refuse une valeur non entière`() {
        assertNull(buildLocalDate(null, 3, 14))
        assertNull(buildLocalDate(2026, null, 14))
        assertNull(buildLocalDate(2026, 3, null))
        assertNull(buildLocalDate(2026, 3, 14, null, 0))
        assertNull(buildLocalDate(2026, 3, 14, 12, null))
    }

    @Test
    fun `une saisie illisible ne devient pas 0`() {
        // Comme parseInt en JS : « ab » donne NaN, donc une date invalide, et non minuit.
        assertNull(buildLocalDate(2026, 3, 14, parseIntegerOrNull("ab"), parseIntegerOrNull("00")))
        assertEquals(LocalDateTime.of(2026, 3, 14, 0, 0), buildLocalDate(2026, 3, 14, parseIntegerOrNull("00"), parseIntegerOrNull("0")))
    }
}
