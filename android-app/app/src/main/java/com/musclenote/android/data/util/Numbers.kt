package com.musclenote.android.data.util

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val decimalPrefix = Regex("""^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?""")
private val integerPrefix = Regex("""^[+-]?\d+""")

/**
 * Nombre décimal saisi au clavier (SPEC § 10.1). Les claviers français tapent `62,5` : la
 * **première** virgule devient un point, puis on lit le plus long préfixe numérique, comme
 * `parseFloat` en JS (`"62,5kg"` → 62,5 ; `"1,2,3"` → 1,2). 0 si vide ou invalide.
 * Ne jamais lire une saisie avec `toDouble()` : `"62,5"` serait refusé.
 */
fun parseDecimal(s: String): Double {
    val match = decimalPrefix.find(s.trim().replaceFirst(',', '.')) ?: return 0.0
    val value = match.value.toDoubleOrNull() ?: return 0.0
    return if (value.isFinite()) value else 0.0
}

/** Entier saisi : plus long préfixe entier (`"12,5"` → 12), 0 si vide ou invalide (§ 10.1). */
fun parseInteger(s: String): Int {
    val digits = integerPrefix.find(s.trim())?.value ?: return 0
    val value = digits.toLongOrNull() ?: return if (digits.startsWith('-')) Int.MIN_VALUE else Int.MAX_VALUE
    return value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Nombre affiché ou prérempli dans un champ : séparateur décimal de la [locale] (`62,5` en
 * français), au plus [maxFractionDigits] décimales arrondies au plus proche (demis vers le
 * haut), pas de zéros inutiles (`78`, pas `78,0`) ni de séparateur de milliers. [parseDecimal]
 * relit ce texte.
 */
fun formatDecimal(value: Double, locale: Locale, maxFractionDigits: Int = 2): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(value)
}
