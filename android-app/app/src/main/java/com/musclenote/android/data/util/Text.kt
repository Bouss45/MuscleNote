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

/** Espaces de toute sorte (insécables compris), en suite. */
private val spaceRuns = Regex("[\\s\\p{Z}]+")

/**
 * Forme de comparaison d'un nom de séance (SPEC § 15.19.2, 🔄 D45) : celle de [normalizeSearch], avec
 * en plus les suites d'espaces (insécables compris) réduites à une. « Bras  /  Abdos » et
 * « bras / abdos » donnent la même chaîne.
 */
fun normalizeWorkoutName(s: String): String = normalizeSearch(spaceRuns.replace(s, " ")).trim()

/** Nouvel identifiant : UUID v4 (SPEC § 3.15). Les ids lus sont des chaînes opaques. */
fun newId(): String = UUID.randomUUID().toString()
