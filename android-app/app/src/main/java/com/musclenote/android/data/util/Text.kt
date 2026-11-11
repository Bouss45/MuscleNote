package com.musclenote.android.data.util

import java.text.Normalizer
import java.util.UUID

private val combiningMarks = Regex("[\\u0300-\\u036f]")

/**
 * Forme de comparaison d'un texte (SPEC § 10.6) : décomposition NFD, diacritiques combinants
 * retirés, minuscules, `trim()`. « Développé » et « developpe » donnent la même chaîne.
 */
fun normalizeSearch(s: String): String =
    combiningMarks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

/** Nouvel identifiant : UUID v4 (SPEC § 3.15). Les ids lus sont des chaînes opaques. */
fun newId(): String = UUID.randomUUID().toString()
