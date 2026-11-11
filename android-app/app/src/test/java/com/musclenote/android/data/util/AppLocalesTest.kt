package com.musclenote.android.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Locale de formatage (§ 3.14, § 9.4) : celle des textes affichés, français par défaut. */
class AppLocalesTest {

    private fun formatting(vararg tags: String) = AppLocales.formatting(tags.map(Locale::forLanguageTag))

    @Test
    fun `français ou anglais en tête de liste, région conservée`() {
        assertEquals(Locale.forLanguageTag("fr-CA"), formatting("fr-CA", "en-US"))
        assertEquals(Locale.forLanguageTag("en-GB"), formatting("en-GB", "fr-FR"))
    }

    @Test
    fun `une langue sans textes est sautée comme par la résolution des ressources`() {
        assertEquals(Locale.forLanguageTag("en-US"), formatting("de-DE", "en-US"))
    }

    @Test
    fun `aucune langue de l'app dans la liste donne le français`() {
        assertEquals(Locale.FRENCH, formatting("de-DE", "it-IT"))
        assertEquals(Locale.FRENCH, formatting())
    }
}
