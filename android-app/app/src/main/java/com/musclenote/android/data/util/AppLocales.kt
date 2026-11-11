package com.musclenote.android.data.util

import java.util.Locale

/**
 * Langues de l'app (SPEC § 9.4) : français (ressources par défaut, `values`) et anglais
 * (`values-en`).
 */
object AppLocales {
    private val SUPPORTED = setOf("fr", "en")

    /**
     * Locale de formatage des dates et nombres (§ 3.14) : celle des textes affichés. Comme la
     * résolution des ressources Android, la première locale de [locales] (langues du système, ou
     * choix FR / EN de l'app) dont la langue a des textes ; aucune → français, la langue par défaut.
     * La région est gardée (`en-GB`, `fr-CA`) pour les formats de date.
     */
    fun formatting(locales: List<Locale>): Locale = locales.firstOrNull { it.language in SUPPORTED } ?: Locale.FRENCH
}
