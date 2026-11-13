package com.musclenote.android.data.workout

import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInteger
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Champ d'une série saisi au pavé numérique (SPEC § 15.7.4) : longueur maximale (§ 2.6),
 * décimales permises, pas des touches − / +, et lecture / écriture dans la série.
 */
enum class SetEntryField(val maxLength: Int, val decimals: Boolean, val step: Double) {
    Weight(6, true, 2.5),
    Reps(3, false, 1.0),
    Duration(4, true, 1.0),
    Distance(5, true, 0.5),
    Speed(4, true, 0.5),
    Incline(4, true, 1.0);

    /** Valeur du champ dans [set] ; un champ cardio absent vaut 0. */
    fun read(set: WorkoutSet): Double = when (this) {
        Weight -> set.weight
        Reps -> set.reps.toDouble()
        Duration -> set.duration ?: 0.0
        Distance -> set.distance ?: 0.0
        Speed -> set.speed ?: 0.0
        Incline -> set.incline ?: 0.0
    }

    /** [set] avec [value] dans ce champ (reps arrondies à l'entier). */
    fun write(set: WorkoutSet, value: Double): WorkoutSet = when (this) {
        Weight -> set.copy(weight = value)
        Reps -> set.copy(reps = value.roundToInt())
        Duration -> set.copy(duration = value)
        Distance -> set.copy(distance = value)
        Speed -> set.copy(speed = value)
        Incline -> set.copy(incline = value)
    }
}

/**
 * Saisie d'une valeur de série au pavé numérique de la séance en cours (SPEC § 15.7.4, 🔄 D32),
 * en fonctions pures. Le texte garde le séparateur décimal de la langue ; [parseDecimal] le relit.
 * Un champ « neuf » vient d'être ouvert : sa première touche remplace sa valeur.
 */
object SetEntryRules {
    /** Décimales au plus après le séparateur. */
    const val MAX_DECIMALS = 2

    private val STRENGTH = listOf(SetEntryField.Weight, SetEntryField.Reps)
    private val CARDIO = listOf(SetEntryField.Duration, SetEntryField.Distance, SetEntryField.Speed, SetEntryField.Incline)

    /** Champs d'une série, dans l'ordre de « Suivant ». */
    fun fields(isCardio: Boolean): List<SetEntryField> = if (isCardio) CARDIO else STRENGTH

    /** Champ suivant de la série (« Suivant ») ; `null` sur le dernier (« Valider la série »). */
    fun next(field: SetEntryField, isCardio: Boolean): SetEntryField? {
        val fields = fields(isCardio)
        return fields.getOrNull(fields.indexOf(field) + 1)
    }

    /** Séparateur décimal de la touche du pavé : « , » en français, « . » en anglais. */
    fun decimalSeparator(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /**
     * Touche [key] (chiffre ou séparateur de la [locale]) appliquée à [text]. Sans effet : un
     * séparateur sur un champ entier ou en double, une décimale au-delà de [MAX_DECIMALS], un texte
     * plus long que la longueur maximale du champ, toute autre touche. Pas de zéro inutile en tête ;
     * un séparateur sur un champ vide donne « 0, ».
     */
    fun type(text: String, fresh: Boolean, key: Char, field: SetEntryField, locale: Locale): String {
        val separator = decimalSeparator(locale)
        val base = if (fresh) "" else text
        val next = when {
            key == separator -> when {
                !field.decimals || separator in base -> return base
                base.isEmpty() -> "0$separator"
                else -> base + separator
            }
            key.isDigit() -> when {
                separator in base && base.substringAfter(separator).length >= MAX_DECIMALS -> return base
                base == "0" -> key.toString()
                else -> base + key
            }
            else -> return base
        }
        return if (next.length > field.maxLength) base else next
    }

    /** Effacer : tout le champ s'il est neuf, sinon le dernier caractère. */
    fun backspace(text: String, fresh: Boolean): String = if (fresh) "" else text.dropLast(1)

    /**
     * Touche de pas : [value] ± le pas du champ ([direction] = +1 ou −1), jamais sous 0, arrondie
     * au centième (demi vers +∞, § 3.5) ; `null` (sans effet) si le résultat affiché dépasserait la
     * longueur maximale du champ.
     */
    fun step(value: Double, direction: Int, field: SetEntryField, locale: Locale): Double? {
        val next = ((value + direction * field.step).coerceAtLeast(0.0) * 100).roundToLong() / 100.0
        return next.takeIf { format(it, field, locale).length <= field.maxLength }
    }

    /** Valeur affichée (lignes et pavé) : 2 décimales au plus, sans zéro final ni milliers. */
    fun format(value: Double, field: SetEntryField, locale: Locale): String =
        formatDecimal(value, locale, if (field.decimals) MAX_DECIMALS else 0)

    /** Texte saisi lu dans la série (🔄 D20 : à chaque touche) ; un champ vide vaut 0. */
    fun apply(set: WorkoutSet, field: SetEntryField, text: String): WorkoutSet =
        field.write(set, if (field.decimals) parseDecimal(text) else parseInteger(text).toDouble())

    /**
     * Série à ouvrir après « Valider la série » au pavé : la première série non validée après
     * [setId] dans [sets] ; `null` s'il n'y en a pas (le pavé se ferme).
     */
    fun nextOpenSet(sets: List<WorkoutSet>, setId: String): WorkoutSet? {
        val index = sets.indexOfFirst { it.id == setId }
        if (index < 0) return null
        return sets.drop(index + 1).firstOrNull { !it.completed }
    }
}
