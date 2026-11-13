package com.musclenote.android.data.workout

import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Pavé numérique de la séance en cours (SPEC § 15.7.4, 🔄 D32). */
class SetEntryRulesTest {

    private val fr: Locale = Locale.FRENCH
    private val en: Locale = Locale.ENGLISH

    private fun type(text: String, key: Char, field: SetEntryField = SetEntryField.Weight, fresh: Boolean = false, locale: Locale = fr) =
        SetEntryRules.type(text, fresh, key, field, locale)

    @Test
    fun `la première touche d'un champ neuf remplace sa valeur`() {
        assertEquals("8", type("82,5", '8', fresh = true))
        assertEquals("0,", type("82,5", ',', fresh = true))
        assertEquals("82,5", type("82,", '5'))
    }

    @Test
    fun `effacer vide un champ neuf, sinon retire le dernier caractère`() {
        assertEquals("", SetEntryRules.backspace("82,5", fresh = true))
        assertEquals("82,", SetEntryRules.backspace("82,5", fresh = false))
        assertEquals("0", SetEntryRules.backspace("0,", fresh = false))
        assertEquals("", SetEntryRules.backspace("", fresh = false))
    }

    @Test
    fun `virgule, un seul séparateur, zéro devant sur un champ vide`() {
        assertEquals("0,", type("", ','))
        assertEquals("82,5", type("82,5", ','))
        assertEquals("82,", type("82", ','))
    }

    @Test
    fun `le séparateur suit la langue`() {
        assertEquals("82.", type("82", '.', locale = en))
        // La virgule n'est pas la touche du pavé anglais : sans effet.
        assertEquals("82", type("82", ',', locale = en))
        assertEquals('.', SetEntryRules.decimalSeparator(en))
        assertEquals(',', SetEntryRules.decimalSeparator(fr))
    }

    @Test
    fun `pas de séparateur pour les reps`() {
        assertEquals("12", type("12", ',', SetEntryField.Reps))
        assertEquals("", type("12", ',', SetEntryField.Reps, fresh = true))
    }

    @Test
    fun `pas de zéro inutile en tête`() {
        assertEquals("7", type("0", '7'))
        assertEquals("0,", type("0", ','))
        assertEquals("0,0", type("0,", '0'))
        assertEquals("5", type("0", '5', SetEntryField.Reps))
    }

    @Test
    fun `deux décimales au plus`() {
        assertEquals("62,25", type("62,2", '5'))
        assertEquals("62,25", type("62,25", '5'))
    }

    @Test
    fun `longueur maximale de chaque champ`() {
        assertEquals("123", type("123", '4', SetEntryField.Reps))
        assertEquals("102,25", type("102,2", '5'))
        assertEquals("1000,5", type("1000,", '5'))
        assertEquals("99999", type("99999", '9', SetEntryField.Distance))
        assertEquals("1234", type("1234", '5', SetEntryField.Duration))
        assertEquals("12,5", type("12,5", '0', SetEntryField.Speed))
        assertEquals("1500", type("1500", '0', SetEntryField.Incline))
        assertEquals("100000", type("100000", '0'))
        // Même un champ neuf garde sa longueur maximale (champ vide : la touche passe).
        assertEquals("9", type("123", '9', SetEntryField.Reps, fresh = true))
    }

    @Test
    fun `pas de chaque champ, jamais sous zéro`() {
        assertEquals(85.0, SetEntryRules.step(82.5, +1, SetEntryField.Weight, fr)!!, 0.0)
        assertEquals(80.0, SetEntryRules.step(82.5, -1, SetEntryField.Weight, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(1.0, -1, SetEntryField.Weight, fr)!!, 0.0)
        assertEquals(9.0, SetEntryRules.step(8.0, +1, SetEntryField.Reps, fr)!!, 0.0)
        assertEquals(0.0, SetEntryRules.step(0.0, -1, SetEntryField.Reps, fr)!!, 0.0)
        assertEquals(31.0, SetEntryRules.step(30.0, +1, SetEntryField.Duration, fr)!!, 0.0)
        assertEquals(5.7, SetEntryRules.step(5.2, +1, SetEntryField.Distance, fr)!!, 0.0)
        assertEquals(9.0, SetEntryRules.step(9.5, -1, SetEntryField.Speed, fr)!!, 0.0)
        assertEquals(3.0, SetEntryRules.step(2.0, +1, SetEntryField.Incline, fr)!!, 0.0)
    }

    @Test
    fun `un pas est arrondi au centième`() {
        assertEquals(0.6, SetEntryRules.step(0.1, +1, SetEntryField.Distance, fr)!!, 0.0)
        assertEquals(83.75, SetEntryRules.step(81.25, +1, SetEntryField.Weight, fr)!!, 0.0)
        assertEquals(2.83, SetEntryRules.step(0.333, +1, SetEntryField.Weight, fr)!!, 0.0)
    }

    @Test
    fun `un pas qui dépasserait la longueur maximale est refusé`() {
        assertNull(SetEntryRules.step(999.0, +1, SetEntryField.Reps, fr))
        assertNull(SetEntryRules.step(9999.0, +1, SetEntryField.Duration, fr))
        assertEquals(1000.0, SetEntryRules.step(999.0, +1, SetEntryField.Duration, fr)!!, 0.0)
        assertNull(SetEntryRules.step(999999.0, +1, SetEntryField.Weight, fr))
        assertEquals(1002.0, SetEntryRules.step(999.5, +1, SetEntryField.Weight, fr)!!, 0.0)
    }

    @Test
    fun `affichage, deux décimales au plus, sans zéro final ni milliers`() {
        assertEquals("82,5", SetEntryRules.format(82.5, SetEntryField.Weight, fr))
        assertEquals("80", SetEntryRules.format(80.0, SetEntryField.Weight, fr))
        assertEquals("81,25", SetEntryRules.format(81.25, SetEntryField.Weight, fr))
        assertEquals("0,3", SetEntryRules.format(0.1 + 0.2, SetEntryField.Distance, fr))
        assertEquals("1000", SetEntryRules.format(1000.0, SetEntryField.Weight, fr))
        assertEquals("82.5", SetEntryRules.format(82.5, SetEntryField.Weight, en))
        assertEquals("0", SetEntryRules.format(0.0, SetEntryField.Weight, fr))
        assertEquals("8", SetEntryRules.format(8.0, SetEntryField.Reps, fr))
    }

    @Test
    fun `le texte saisi est lu dans la série, champ vide à zéro`() {
        val set = WorkoutSet("s", 8, 80.0, false)
        assertEquals(82.5, SetEntryRules.apply(set, SetEntryField.Weight, "82,5").weight, 0.0)
        assertEquals(62.0, SetEntryRules.apply(set, SetEntryField.Weight, "62,").weight, 0.0)
        assertEquals(0.0, SetEntryRules.apply(set, SetEntryField.Weight, "").weight, 0.0)
        assertEquals(12, SetEntryRules.apply(set, SetEntryField.Reps, "12").reps)
        assertEquals(0, SetEntryRules.apply(set, SetEntryField.Reps, "").reps)
        val cardio = WorkoutSet("c", 0, 0.0, false, duration = 30.0)
        assertEquals(5.2, SetEntryRules.apply(cardio, SetEntryField.Distance, "5,2").distance!!, 0.0)
        assertEquals(2.0, SetEntryRules.apply(cardio, SetEntryField.Incline, "2").incline!!, 0.0)
        // La validation n'est jamais touchée par la saisie.
        assertEquals(true, SetEntryRules.apply(set.copy(completed = true), SetEntryField.Reps, "9").completed)
    }

    @Test
    fun `valeur d'un champ, cardio sans valeur à zéro`() {
        val cardio = WorkoutSet("c", 0, 0.0, false, duration = 30.0, distance = null)
        assertEquals(30.0, SetEntryField.Duration.read(cardio), 0.0)
        assertEquals(0.0, SetEntryField.Distance.read(cardio), 0.0)
        assertEquals(31.0, SetEntryField.Duration.write(cardio, 31.0).duration!!, 0.0)
        assertEquals(9, SetEntryField.Reps.write(WorkoutSet("s", 8, 80.0, false), 9.0).reps)
    }

    @Test
    fun `Suivant passe au champ suivant, rien après le dernier`() {
        assertEquals(SetEntryField.Reps, SetEntryRules.next(SetEntryField.Weight, isCardio = false))
        assertNull(SetEntryRules.next(SetEntryField.Reps, isCardio = false))
        assertEquals(SetEntryField.Distance, SetEntryRules.next(SetEntryField.Duration, isCardio = true))
        assertEquals(SetEntryField.Speed, SetEntryRules.next(SetEntryField.Distance, isCardio = true))
        assertEquals(SetEntryField.Incline, SetEntryRules.next(SetEntryField.Speed, isCardio = true))
        assertNull(SetEntryRules.next(SetEntryField.Incline, isCardio = true))
        assertEquals(listOf(SetEntryField.Weight, SetEntryField.Reps), SetEntryRules.fields(isCardio = false))
    }

    @Test
    fun `après validation, la série non validée suivante du même exercice`() {
        val sets = listOf(
            WorkoutSet("a", 8, 80.0, true),
            WorkoutSet("b", 8, 80.0, false),
            WorkoutSet("c", 8, 80.0, true),
            WorkoutSet("d", 8, 80.0, false),
        )
        assertEquals("b", SetEntryRules.nextOpenSet(sets, "a")?.id)
        assertEquals("d", SetEntryRules.nextOpenSet(sets, "b")?.id)
        assertNull(SetEntryRules.nextOpenSet(sets, "d"))
        assertNull(SetEntryRules.nextOpenSet(sets, "inconnue"))
    }
}
