package com.musclenote.android.ui.components

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.AppLocales
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Locale effective de l'app, pour formater (§ 3.14) : celle des textes affichés — français ou
 * anglais, système dans une autre langue → français (§ 9.4).
 */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.appFormattingLocale()

/** [AppLocales.formatting] appliquée aux locales de la configuration. */
fun Configuration.appFormattingLocale(): Locale = AppLocales.formatting(List(locales.size()) { locales.get(it) })

/** Nombre décimal selon la locale de l'app (`78,5` en français). */
@Composable
@ReadOnlyComposable
fun formatNumber(value: Double): String = formatDecimal(value, appLocale())

/**
 * Entier arrondi (demis vers le haut), avec le séparateur de milliers de la locale : `5 230` en
 * français, `5,230` en anglais (§ 9.4). Pour les volumes et autres grands totaux.
 */
@Composable
@ReadOnlyComposable
fun formatInteger(value: Double): String = NumberFormat.getIntegerInstance(appLocale()).format(value.roundToLong())

/**
 * Nombre à une décimale exactement (`2,0`, `-1,8`), séparateurs de la locale, demis vers le haut :
 * litres, tonnes, kilomètres et évolutions de l'onglet Progrès (§ 2.10).
 */
@Composable
@ReadOnlyComposable
fun formatOneDecimal(value: Double): String = NumberFormat.getNumberInstance(appLocale()).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
    roundingMode = RoundingMode.HALF_UP
}.format(value)

/** Signe moins typographique des variations (« −2,5 kg »). */
const val MINUS_SIGN = "\u2212"

/**
 * Variation toujours signée (`+7,5`, `−2,5`) : une décimale exactement, ou entière si [integer]
 * (reps). Une variation nulle n'est pas signée ici : l'écran affiche « identique ».
 */
@Composable
@ReadOnlyComposable
fun formatSignedDelta(value: Double, integer: Boolean = false): String {
    val magnitude = if (integer) formatInteger(kotlin.math.abs(value)) else formatOneDecimal(kotlin.math.abs(value))
    return when {
        value > 0 -> "+$magnitude"
        value < 0 -> "$MINUS_SIGN$magnitude"
        else -> magnitude
    }
}

/** Formats de date de l'app, dans la locale et le fuseau courants (§ 9.4). */
class DateFormats(private val locale: Locale, private val zone: ZoneId) {
    private val weekdayDayMonthFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("dd/MM", locale)
    private val dayMonthYearFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)
    private val dayShortMonthFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    private val fullDateFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    private val weekdayDayMonthYearFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy"), locale)
    private val monthYearFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMyyyy"), locale)

    /** Jour de semaine abrégé, jour, mois abrégé : « mar. 11 nov. », « Tue, Nov 11 ». */
    fun weekdayDayMonth(instant: Instant): String = weekdayDayMonthFormatter.format(instant.atZone(zone))

    /** Jour de semaine abrégé, jour, mois abrégé d'un jour local : « jeu. 5 nov. ». */
    fun weekdayDayMonth(day: LocalDate): String = weekdayDayMonthFormatter.format(day)

    /** Avec l'année : « jeu. 5 nov. 2025 », « Thu, Nov 5, 2025 ». */
    fun weekdayDayMonthYear(instant: Instant): String = weekdayDayMonthYearFormatter.format(instant.atZone(zone))

    /** Avec l'année, d'un jour local. */
    fun weekdayDayMonthYear(day: LocalDate): String = weekdayDayMonthYearFormatter.format(day)

    /** Mois abrégé et année : « févr. 2026 », « Feb 2026 » (fiche exercice). */
    fun monthYear(instant: Instant): String = monthYearFormatter.format(instant.atZone(zone))

    /**
     * Date d'une séance : « jeu. 5 nov. », avec l'année quand elle n'est pas celle de [today]
     * (« jeu. 5 nov. 2025 »).
     */
    fun sessionDate(instant: Instant, today: LocalDate): String =
        if (instant.atZone(zone).year == today.year) weekdayDayMonth(instant) else weekdayDayMonthYear(instant)

    /** Jour court : « 14 août », avec l'année quand elle n'est pas celle de [today] (« 12 févr. 2025 »). */
    fun shortDay(day: LocalDate, today: LocalDate): String =
        if (day.year == today.year) dayShortMonth(day) else dayMonthYear(day)

    /** Jour d'une pesée ou d'un point, avec l'année si besoin : « sam. 14 nov. », « dim. 2 nov. 2025 ». */
    fun weekdayDay(day: LocalDate, today: LocalDate): String =
        if (day.year == today.year) weekdayDayMonth(day) else weekdayDayMonthYear(day)

    /** Heure `HH:mm`. */
    fun time(instant: Instant): String = timeFormatter.format(instant.atZone(zone))

    /** `dd/MM`, pour les libellés des graphiques. */
    fun dayMonth(instant: Instant): String = dayMonthFormatter.format(instant.atZone(zone))

    /** `dd/MM` d'un jour local (journaux quotidiens). */
    fun dayMonth(day: LocalDate): String = dayMonthFormatter.format(day)

    /** Jour, mois abrégé, année : « 11 nov. 2026 », « Nov 11, 2026 ». */
    fun dayMonthYear(instant: Instant): String = dayMonthYearFormatter.format(instant.atZone(zone))

    /** Jour et mois abrégé d'un jour local : « 26 oct. », « Oct 26 » (semaines du Journal). */
    fun dayShortMonth(day: LocalDate): String = dayShortMonthFormatter.format(day)

    /** Jour, mois abrégé et année d'un jour local : « 29 déc. 2025 », « Dec 29, 2025 ». */
    fun dayMonthYear(day: LocalDate): String = dayMonthYearFormatter.format(day)

    /** Date complète, majuscule en tête : « Samedi 14 novembre », « Saturday, November 14 » (Accueil). */
    fun fullDate(day: LocalDate): String =
        fullDateFormatter.format(day).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
fun rememberDateFormats(zone: ZoneId): DateFormats {
    val locale = appLocale()
    return remember(locale, zone) { DateFormats(locale, zone) }
}

/**
 * Nom affiché d'une séance (§ 3.1) : libellé traduit du préréglage, sinon le nom saisi, sinon
 * la date de la séance.
 */
@Composable
@ReadOnlyComposable
fun workoutDisplayName(workout: Workout): String {
    val preset = workout.preset
    if (preset != null) return stringResource(preset.labelRes)
    if (workout.name.isNotBlank()) return workout.name
    val zone = ZoneId.systemDefault()
    val start = parseInstantOrNull(workout.date, zone) ?: return ""
    return start.atZone(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()))
}

/** Nom affiché d'un exercice (§ 6.1) : traduit pour un exercice intégré, saisi pour un perso. */
@Composable
@ReadOnlyComposable
fun exerciseName(definition: ExerciseDefinition): String =
    definition.nameRes?.let { stringResource(it) } ?: definition.customName.orEmpty()
