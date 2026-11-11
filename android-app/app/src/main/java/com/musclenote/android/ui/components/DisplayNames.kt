package com.musclenote.android.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

/** Locale effective de l'app (langue du système ou choix FR / EN, § 9.4), pour formater. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

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

/** Formats de date de l'app, dans la locale et le fuseau courants (§ 9.4). */
class DateFormats(locale: Locale, private val zone: ZoneId) {
    /** Jour de semaine abrégé, jour, mois abrégé : « mar. 11 nov. », « Tue, Nov 11 ». */
    private val weekdayDayMonth = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM", locale)

    fun weekdayDayMonth(instant: Instant): String = weekdayDayMonth.format(instant.atZone(zone))

    /** Heure `HH:mm`. */
    fun time(instant: Instant): String = time.format(instant.atZone(zone))

    /** `dd/MM`, pour les libellés des graphiques. */
    fun dayMonth(instant: Instant): String = dayMonth.format(instant.atZone(zone))
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
