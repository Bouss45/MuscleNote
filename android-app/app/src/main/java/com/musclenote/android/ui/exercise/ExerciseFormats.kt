package com.musclenote.android.ui.exercise

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.musclenote.android.R
import com.musclenote.android.data.progress.ChartPeriod
import com.musclenote.android.data.progress.Direction
import com.musclenote.android.data.progress.ExerciseMetric
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.formatSignedDelta
import com.musclenote.android.ui.designsystem.components.Trend

/*
 * Textes communs à la fiche exercice et à Progrès (SPEC § 15.9) : valeur d'une métrique et son unité,
 * variation signée, sens, libellés des périodes et description TalkBack d'une courbe.
 */

/** Chiffre d'une métrique, sans unité : `82,5` (kg, min), `15` (reps). */
@Composable
@ReadOnlyComposable
fun metricNumber(value: Double, metric: ExerciseMetric): String =
    if (metric == ExerciseMetric.Reps) formatInteger(value) else formatNumber(value)

/** Unité d'une métrique, après un chiffre héros. */
fun metricUnit(metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.Load -> "kg"
    ExerciseMetric.Reps -> "reps"
    ExerciseMetric.Duration -> "min"
}

/** Valeur et unité : « 82,5 kg », « 15 reps », « 45 min ». */
@Composable
@ReadOnlyComposable
fun metricText(value: Double, metric: ExerciseMetric): String = stringResource(
    when (metric) {
        ExerciseMetric.Load -> R.string.common_kg_value
        ExerciseMetric.Reps -> R.string.common_reps_value
        ExerciseMetric.Duration -> R.string.common_min_value
    },
    metricNumber(value, metric),
)

/** Variation signée avec l'unité (« +7,5 kg », « −3 reps »), « identique » si nulle. */
@Composable
@ReadOnlyComposable
fun metricDelta(variation: Double, metric: ExerciseMetric): String = when {
    variation == 0.0 -> stringResource(R.string.delta_same)
    else -> "${formatSignedDelta(variation, integer = metric == ExerciseMetric.Reps)} ${metricUnit(metric)}"
}

/** Couleur d'une variation : la hausse est un progrès (DESIGN.md § 1). */
fun Direction.trend(): Trend = when (this) {
    Direction.Up -> Trend.Up
    Direction.Down -> Trend.Down
    Direction.Same -> Trend.Neutral
}

/** Libellé court du sélecteur (« 3M ») et ce qu'en lit TalkBack (« 3 mois »). */
@StringRes
fun ChartPeriod.labelRes(): Int = when (this) {
    ChartPeriod.OneMonth -> R.string.period_1m
    ChartPeriod.ThreeMonths -> R.string.period_3m
    ChartPeriod.SixMonths -> R.string.period_6m
    ChartPeriod.OneYear -> R.string.period_1y
    ChartPeriod.All -> R.string.period_all
}

@StringRes
fun ChartPeriod.descriptionRes(): Int = when (this) {
    ChartPeriod.OneMonth -> R.string.period_1m_description
    ChartPeriod.ThreeMonths -> R.string.period_3m_description
    ChartPeriod.SixMonths -> R.string.period_6m_description
    ChartPeriod.OneYear -> R.string.period_1y_description
    ChartPeriod.All -> R.string.period_all_description
}

/** Ce qu'une courbe d'exercice montre : « Charge max par séance »… */
@StringRes
fun ExerciseMetric.curveLabelRes(): Int = when (this) {
    ExerciseMetric.Load -> R.string.curve_metric_load
    ExerciseMetric.Reps -> R.string.curve_metric_reps
    ExerciseMetric.Duration -> R.string.curve_metric_duration
}

/** Un point d'une description de courbe : valeur avec unité, et jour. */
data class CurveEnd(val value: String, val day: String)

/**
 * Description TalkBack d'une courbe (§ 15.9.1) : « Charge max par séance, 3 mois : de 92,5 kg le
 * 14 août à 100 kg le 5 nov., +7,5 kg » ; un point : « …, 1 mois : 95 kg le 5 nov. » ; aucun :
 * « …, 1 mois : aucune séance ».
 */
@Composable
@ReadOnlyComposable
fun curveDescription(curve: String, period: String, start: CurveEnd?, end: CurveEnd?, change: String?): String = when {
    start == null || end == null -> stringResource(R.string.curve_description_empty, curve, period)
    change == null -> stringResource(R.string.curve_description_single, curve, period, end.value, end.day)
    else -> stringResource(R.string.curve_description_range, curve, period, start.value, start.day, end.value, end.day, change)
}
