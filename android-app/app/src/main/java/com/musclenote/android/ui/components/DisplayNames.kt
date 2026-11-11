package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale effective de l'app (langue du système ou choix FR / EN, § 9.4), pour formater. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

/** Nombre décimal selon la locale de l'app (`78,5` en français). */
@Composable
@ReadOnlyComposable
fun formatNumber(value: Double): String = formatDecimal(value, appLocale())

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
