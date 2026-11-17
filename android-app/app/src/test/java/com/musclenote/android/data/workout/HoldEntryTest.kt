package com.musclenote.android.data.workout

import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.WorkoutSet
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Durée d'une série au temps et sa saisie au pavé numérique (SPEC § 15.18.3, 🔄 D44). */
class HoldEntryTest {

    private val fr: Locale = Locale.FRENCH
    private val en: Locale = Locale.ENGLISH
    private val hold = SetEntryField.Hold

    private fun type(text: String, key: Char, fresh: Boolean = false, locale: Locale = fr) =
        SetEntryRules.type(text, fresh, key, hold, locale)

    private fun set(seconds: Int?, weight: Double = 0.0) = WorkoutSet("h", 0, weight, false, durationSeconds = seconds)

    // ─── Chiffres lus depuis la droite ──────────────────────────────────────────────

    @Test
    fun `les deux derniers chiffres sont les secondes, les autres les minutes`() {
        assertEquals(0, HoldDuration.secondsOf(""))
        assertEquals(0, HoldDuration.secondsOf("0"))
        assertEquals(5, HoldDuration.secondsOf("5"))
        assertEquals(45, HoldDuration.secondsOf("45"))
        assertEquals(90, HoldDuration.secondsOf("130"))
        assertEquals(750, HoldDuration.secondsOf("1230"))
        assertEquals(3600, HoldDuration.secondsOf("6000"))
    }

    @Test
    fun `des secondes au-delà de 59 comptent comme des secondes`() {
        assertEquals(90, HoldDuration.secondsOf("90"))
        assertEquals(99, HoldDuration.secondsOf("99"))
        // 9 min 75 s = 10 min 15 s.
        assertEquals(615, HoldDuration.secondsOf("975"))
    }

    @Test
    fun `la valeur est bornée à 99 59`() {
        assertEquals(HoldDuration.MAX_SECONDS, HoldDuration.secondsOf("9999"))
        assertEquals(5999, HoldDuration.secondsOf("9959"))
        assertEquals(5999, HoldDuration.MAX_SECONDS)
    }

    @Test
    fun `pendant la saisie les chiffres s'affichent tels quels en m ss`() {
        assertEquals("0:00", HoldDuration.display(""))
        assertEquals("0:00", HoldDuration.display("0"))
        assertEquals("0:05", HoldDuration.display("5"))
        assertEquals("0:45", HoldDuration.display("45"))
        assertEquals("0:90", HoldDuration.display("90"))
        assertEquals("1:30", HoldDuration.display("130"))
        assertEquals("12:30", HoldDuration.display("1230"))
    }

    @Test
    fun `hors saisie la valeur relue s'affiche en m ss, minutes non bornées`() {
        assertEquals("0:00", HoldDuration.format(0))
        assertEquals("0:45", HoldDuration.format(45))
        assertEquals("1:30", HoldDuration.format(90))
        assertEquals("12:05", HoldDuration.format(725))
        assertEquals("99:59", HoldDuration.format(5999))
        assertEquals("100:00", HoldDuration.format(6000))
        assertEquals("0:00", HoldDuration.format(-3))
    }

    @Test
    fun `temps sous tension en h mm ss à partir d'une heure`() {
        assertEquals("3:45", HoldDuration.formatLong(225))
        assertEquals("59:59", HoldDuration.formatLong(3599))
        assertEquals("1:00:00", HoldDuration.formatLong(3600))
        assertEquals("1:02:05", HoldDuration.formatLong(3725))
    }

    @Test
    fun `un champ rouvert part de la valeur relue`() {
        assertEquals("130", HoldDuration.digitsOf(90))
        assertEquals("5", HoldDuration.digitsOf(5))
        assertEquals("0", HoldDuration.digitsOf(0))
        assertEquals("1000", HoldDuration.digitsOf(600))
        assertEquals("9959", HoldDuration.digitsOf(5999))
        assertEquals("130", SetEntryRules.entryText(90.0, hold, fr))
        assertEquals("1:30", SetEntryRules.display("130", hold))
        // Les autres champs gardent leur texte.
        assertEquals("82,5", SetEntryRules.entryText(82.5, SetEntryField.Weight, fr))
        assertEquals("82,", SetEntryRules.display("82,", SetEntryField.Weight))
    }

    // ─── Touches du pavé ────────────────────────────────────────────────────────────

    @Test
    fun `chiffres ajoutés à droite, quatre au plus`() {
        assertEquals("1", type("", '1'))
        assertEquals("13", type("1", '3'))
        assertEquals("130", type("13", '0'))
        assertEquals("1300", type("130", '0'))
        assertNull(type("1300", '5'))
    }

    @Test
    fun `pas de zéro inutile en tête, séparateur sans effet`() {
        assertEquals("7", type("0", '7'))
        assertNull(type("13", ','))
        assertNull(type("13", '.', locale = en))
        assertNull(type("", ','))
        // Champ neuf : la touche refusée ne le vide pas.
        assertNull(type("130", ',', fresh = true))
    }

    @Test
    fun `un champ neuf est remplacé, effacer le vide ou retire le dernier chiffre`() {
        assertEquals("4", type("130", '4', fresh = true))
        assertEquals("", SetEntryRules.backspace("130", fresh = true))
        assertEquals("13", SetEntryRules.backspace("130", fresh = false))
        assertEquals("0:13", SetEntryRules.display("13", hold))
    }

    @Test
    fun `chaque touche s'applique à la série en secondes`() {
        assertEquals(90, SetEntryRules.apply(set(30), hold, "90").durationSeconds)
        assertEquals(90, SetEntryRules.apply(set(30), hold, "130").durationSeconds)
        assertEquals(0, SetEntryRules.apply(set(30), hold, "").durationSeconds)
        assertEquals(5999, SetEntryRules.apply(set(30), hold, "9999").durationSeconds)
        // Le lest ne bouge pas.
        assertEquals(10.0, SetEntryRules.apply(set(30, weight = 10.0), hold, "45").weight, 0.0)
    }

    @Test
    fun `lecture et écriture du champ, une série sans durée vaut 0`() {
        assertEquals(0.0, hold.read(set(null)), 0.0)
        assertEquals(45.0, hold.read(set(45)), 0.0)
        assertEquals(60, hold.write(set(null), 60.0).durationSeconds)
        assertEquals(5999, hold.write(set(null), 7000.0).durationSeconds)
        assertEquals(0, hold.write(set(null), -5.0).durationSeconds)
    }

    @Test
    fun `pas de 5 s sur la valeur relue, entre 0 et 99 59`() {
        assertEquals(95.0, SetEntryRules.step(90.0, 1, hold, fr)!!, 0.0)
        assertEquals(85.0, SetEntryRules.step(90.0, -1, hold, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(3.0, -1, hold, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(0.0, -1, hold, fr)!!, 0.0)
        assertEquals(5999.0, SetEntryRules.step(5997.0, 1, hold, fr)!!, 0.0)
        assertEquals(5999.0, SetEntryRules.step(5999.0, 1, hold, fr)!!, 0.0)
        // « 90 » saisi (0:90) puis +5 s : 1:35.
        val typed = SetEntryRules.apply(set(30), hold, "90")
        assertEquals("1:35", SetEntryRules.format(SetEntryRules.step(hold.read(typed), 1, hold, fr)!!, hold, fr))
    }

    @Test
    fun `affichage d'une durée dans les lignes`() {
        assertEquals("1:30", SetEntryRules.format(90.0, hold, fr))
        assertEquals("0:00", SetEntryRules.format(0.0, hold, en))
    }

    // ─── Champs d'une série au temps ───────────────────────────────────────────────

    @Test
    fun `durée puis charge, Suivant puis Valider la série`() {
        assertEquals(listOf(SetEntryField.Hold, SetEntryField.Weight), SetEntryRules.fields(SetKind.Timed))
        assertEquals(SetEntryField.Weight, SetEntryRules.next(SetEntryField.Hold, SetKind.Timed))
        assertNull(SetEntryRules.next(SetEntryField.Weight, SetKind.Timed))
        assertEquals(listOf(SetEntryField.Weight, SetEntryField.Reps), SetEntryRules.fields(SetKind.Strength))
        assertEquals(SetEntryField.Distance, SetEntryRules.next(SetEntryField.Duration, SetKind.Cardio))
    }
}
