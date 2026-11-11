package com.musclenote.android.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Saisie et affichage des nombres (§ 10.1). */
class NumbersTest {

    @Test
    fun `parseDecimal accepte la virgule et lit le plus long préfixe numérique`() {
        val cases = mapOf(
            "62,5" to 62.5,
            "62.5" to 62.5,
            " 70 " to 70.0,
            "62,5kg" to 62.5,
            "1,2,3" to 1.2,
            ",5" to 0.5,
            "5," to 5.0,
            "-3,5" to -3.5,
            "1e3" to 1000.0,
            "" to 0.0,
            "abc" to 0.0,
            "kg62" to 0.0,
            "," to 0.0,
        )
        for ((input, expected) in cases) assertEquals("« $input »", expected, parseDecimal(input), 0.0)
    }

    @Test
    fun `parseInteger lit le plus long préfixe entier`() {
        val cases = mapOf(
            "12" to 12,
            "12,5" to 12,
            " 8 " to 8,
            "3reps" to 3,
            "-4" to -4,
            "" to 0,
            "x" to 0,
            "99999999999" to Int.MAX_VALUE,
        )
        for ((input, expected) in cases) assertEquals("« $input »", expected, parseInteger(input))
    }

    @Test
    fun `parseIntegerOrNull distingue une saisie illisible de 0`() {
        val cases = mapOf(
            "12" to 12,
            "07" to 7,
            "0" to 0,
            "12,5" to 12,
            " 8 " to 8,
            "-4" to -4,
            "" to null,
            "  " to null,
            "x" to null,
            ",5" to null,
            "99999999999" to null,
        )
        for ((input, expected) in cases) assertEquals("« $input »", expected, parseIntegerOrNull(input))
    }

    @Test
    fun `formatDecimal suit la locale sans zéros inutiles et se relit`() {
        assertEquals("62,5", formatDecimal(62.5, Locale.FRENCH))
        assertEquals("62.5", formatDecimal(62.5, Locale.ENGLISH))
        assertEquals("78", formatDecimal(78.0, Locale.FRENCH))
        assertEquals("1234,5", formatDecimal(1234.5, Locale.FRENCH))
        assertEquals("62,13", formatDecimal(62.125, Locale.FRENCH))
        for (value in listOf(0.5, 62.5, 78.0, 102.25)) {
            assertEquals(value, parseDecimal(formatDecimal(value, Locale.FRENCH)), 0.0)
            assertEquals(value, parseDecimal(formatDecimal(value, Locale.ENGLISH)), 0.0)
        }
    }
}
