package com.musclenote.android.data.workout

import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInteger
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Format d'une valeur saisie au pavé numérique : longueur maximale, décimales permises et pas des
 * touches − / +. Les champs d'une série ([SetEntryField]) et les quantités de Nutrition
 * (`NutritionEntry`, SPEC § 15.10.5) partagent les règles de [SetEntryRules].
 */
interface EntryFormat {
    val maxLength: Int
    val decimals: Boolean
    val step: Double
}

/**
 * Champ d'une série saisi au pavé numérique (SPEC § 15.7.4) : longueur maximale (§ 2.6),
 * décimales permises, pas des touches − / +, et lecture / écriture dans la série. [Hold] = durée
 * d'une série au temps, en secondes, saisie en chiffres lus depuis la droite (§ 15.18.3, 🔄 D44).
 */
enum class SetEntryField(override val maxLength: Int, override val decimals: Boolean, override val step: Double) : EntryFormat {
    Weight(6, true, 2.5),
    Reps(3, false, 1.0),
    Duration(4, true, 1.0),
    Distance(5, true, 0.5),
    Speed(4, true, 0.5),
    Incline(4, true, 1.0),
    Hold(HoldDuration.MAX_DIGITS, false, HoldDuration.STEP_SECONDS.toDouble());

    /** Valeur du champ dans [set] ; un champ cardio absent vaut 0, une série sans durée 0 s. */
    fun read(set: WorkoutSet): Double = when (this) {
        Weight -> set.weight
        Reps -> set.reps.toDouble()
        Duration -> set.duration ?: 0.0
        Distance -> set.distance ?: 0.0
        Speed -> set.speed ?: 0.0
        Incline -> set.incline ?: 0.0
        Hold -> (set.durationSeconds ?: 0).toDouble()
    }

    /** [set] avec [value] dans ce champ (reps arrondies à l'entier, durée en secondes bornée à 99:59). */
    fun write(set: WorkoutSet, value: Double): WorkoutSet = when (this) {
        Weight -> set.copy(weight = value)
        Reps -> set.copy(reps = value.roundToInt())
        Duration -> set.copy(duration = value)
        Distance -> set.copy(distance = value)
        Speed -> set.copy(speed = value)
        Incline -> set.copy(incline = value)
        Hold -> set.copy(durationSeconds = HoldDuration.clamp(value.roundToInt()))
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
    private val TIMED = listOf(SetEntryField.Hold, SetEntryField.Weight)

    /** Champs d'une série, dans l'ordre de « Suivant » (au temps : durée puis lest, § 15.18.2). */
    fun fields(kind: SetKind): List<SetEntryField> = when (kind) {
        SetKind.Strength -> STRENGTH
        SetKind.Cardio -> CARDIO
        SetKind.Timed -> TIMED
    }

    /** Champ suivant de la série (« Suivant ») ; `null` sur le dernier (« Valider la série »). */
    fun next(field: SetEntryField, kind: SetKind): SetEntryField? {
        val fields = fields(kind)
        return fields.getOrNull(fields.indexOf(field) + 1)
    }

    /** Séparateur décimal de la touche du pavé : « , » en français, « . » en anglais. */
    fun decimalSeparator(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /**
     * Touche [key] (chiffre ou séparateur de la [locale]) appliquée à [text] ; `null` si elle est
     * sans effet — le champ, même neuf, reste alors tel quel : séparateur sur un champ entier ou en
     * double, décimale au-delà de [MAX_DECIMALS], texte plus long que la longueur maximale du champ,
     * toute autre touche. Pas de zéro inutile en tête ; un séparateur sur un champ vide donne « 0, ».
     */
    fun type(text: String, fresh: Boolean, key: Char, field: EntryFormat, locale: Locale): String? {
        val separator = decimalSeparator(locale)
        val base = if (fresh) "" else text
        val next = when {
            key == separator -> when {
                !field.decimals || separator in base -> return null
                base.isEmpty() -> "0$separator"
                else -> base + separator
            }
            key.isDigit() -> when {
                separator in base && base.substringAfter(separator).length >= MAX_DECIMALS -> return null
                base == "0" -> key.toString()
                else -> base + key
            }
            else -> return null
        }
        return next.takeIf { it.length <= field.maxLength }
    }

    /** Effacer : tout le champ s'il est neuf, sinon le dernier caractère. */
    fun backspace(text: String, fresh: Boolean): String = if (fresh) "" else text.dropLast(1)

    /**
     * Touche de pas : [value] ± [step] (le pas du champ par défaut ; 0,1 kg pour une pesée, § 15.9.3),
     * [direction] = +1 ou −1, jamais sous 0, arrondie au centième (demi vers +∞, § 3.5) ; `null`
     * (sans effet) si le résultat affiché dépasserait la longueur maximale du champ.
     */
    fun step(value: Double, direction: Int, field: EntryFormat, locale: Locale, step: Double = field.step): Double? {
        // Durée : ±5 s, bornée entre 0 et 99:59 (§ 15.18.3).
        if (field == SetEntryField.Hold) return HoldDuration.step(value.roundToInt(), direction).toDouble()
        val next = ((value + direction * step).coerceAtLeast(0.0) * 100).roundToLong() / 100.0
        return next.takeIf { format(it, field, locale).length <= field.maxLength }
    }

    /**
     * Valeur affichée (lignes et pavé) : 2 décimales au plus, sans zéro final ni milliers ; une durée
     * en `m:ss` (§ 15.18.3).
     */
    fun format(value: Double, field: EntryFormat, locale: Locale): String =
        if (field == SetEntryField.Hold) HoldDuration.format(value.roundToInt())
        else formatDecimal(value, locale, if (field.decimals) MAX_DECIMALS else 0)

    /** Texte d'un champ ouvert sur [value] : celui de [format], les chiffres d'une durée (90 s → « 130 »). */
    fun entryText(value: Double, field: EntryFormat, locale: Locale): String =
        if (field == SetEntryField.Hold) HoldDuration.digitsOf(value.roundToInt()) else format(value, field, locale)

    /** Texte en saisie tel qu'il s'affiche : une durée en `m:ss` (« 90 » → `0:90`), le reste tel quel. */
    fun display(text: String, field: EntryFormat): String =
        if (field == SetEntryField.Hold) HoldDuration.display(text) else text

    /** Texte saisi lu dans la série (🔄 D20 : à chaque touche) ; un champ vide vaut 0. */
    fun apply(set: WorkoutSet, field: SetEntryField, text: String): WorkoutSet = when {
        field == SetEntryField.Hold -> field.write(set, HoldDuration.secondsOf(text).toDouble())
        field.decimals -> field.write(set, parseDecimal(text))
        else -> field.write(set, parseInteger(text).toDouble())
    }

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
