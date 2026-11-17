package com.musclenote.android.ui.exercise

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.musclenote.android.R
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.progress.ChartPeriod
import com.musclenote.android.data.progress.Direction
import com.musclenote.android.data.progress.ExerciseMetric
import com.musclenote.android.data.workout.HoldDuration
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.formatSignedDelta
import com.musclenote.android.ui.designsystem.components.Trend
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/*
 * Textes communs à la fiche exercice, à Progrès et au résumé de séance (SPEC § 15.9, § 15.12) : valeur
 * d'une métrique et son unité, variation signée, sens, meilleure série, libellés des périodes et
 * description TalkBack d'une courbe.
 */

/** Chiffre d'une métrique, sans unité : `82,5` (kg, min), `15` (reps), `1:30` (durée au temps). */
@Composable
@ReadOnlyComposable
fun metricNumber(value: Double, metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.Reps -> formatInteger(value)
    ExerciseMetric.Hold -> HoldDuration.format(value.roundToInt())
    else -> formatNumber(value)
}

/** Unité d'une métrique, après un chiffre héros ; aucune pour une durée `m:ss` (§ 15.18.6). */
fun metricUnit(metric: ExerciseMetric): String? = when (metric) {
    ExerciseMetric.Load -> "kg"
    ExerciseMetric.Reps -> "reps"
    ExerciseMetric.Duration -> "min"
    ExerciseMetric.Hold -> null
}

/** Valeur et unité : « 82,5 kg », « 15 reps », « 45 min », « 1:30 ». */
@Composable
@ReadOnlyComposable
fun metricText(value: Double, metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.Hold -> metricNumber(value, metric)
    else -> stringResource(
        when (metric) {
            ExerciseMetric.Load -> R.string.common_kg_value
            ExerciseMetric.Reps -> R.string.common_reps_value
            else -> R.string.common_min_value
        },
        metricNumber(value, metric),
    )
}

/**
 * Variation signée avec l'unité (« +7,5 kg », « −3 reps »), « identique » si nulle ; une durée en
 * secondes sous la minute (« +15 s »), en `m:ss` au-delà (« +1:05 », § 15.18.6).
 */
@Composable
@ReadOnlyComposable
fun metricDelta(variation: Double, metric: ExerciseMetric): String = when {
    variation == 0.0 -> stringResource(R.string.delta_same)
    metric == ExerciseMetric.Hold -> signedHold(variation.roundToInt())
    else -> "${formatSignedDelta(variation, integer = metric == ExerciseMetric.Reps)} ${metricUnit(metric)}"
}

/** « +15 s », « −1:05 » (signe moins U+2212). */
@Composable
@ReadOnlyComposable
private fun signedHold(seconds: Int): String {
    val sign = if (seconds < 0) "\u2212" else "+"
    val size = seconds.absoluteValue
    return sign + if (size < 60) stringResource(R.string.duration_seconds_value, size) else HoldDuration.format(size)
}

/** Une durée lue par TalkBack (§ 15.18.3) : « 1 min 30 s », « 45 s », « 2 min ». */
@Composable
@ReadOnlyComposable
fun spokenDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.duration_spoken_seconds, rest)
        rest == 0 -> stringResource(R.string.duration_spoken_minutes, minutes)
        else -> stringResource(R.string.duration_spoken_minutes_seconds, minutes, rest)
    }
}

/** Valeur lue par TalkBack : une durée en mots (« 1 min 30 s »), le reste comme [metricText]. */
@Composable
@ReadOnlyComposable
fun metricSpoken(value: Double, metric: ExerciseMetric): String =
    if (metric == ExerciseMetric.Hold) spokenDuration(value.roundToInt()) else metricText(value, metric)

/** Variation lue par TalkBack : une durée en mots, le reste comme [metricDelta]. */
@Composable
@ReadOnlyComposable
fun metricDeltaSpoken(variation: Double, metric: ExerciseMetric): String =
    if (metric == ExerciseMetric.Hold) spokenHoldDelta(variation) else metricDelta(variation, metric)

/** Une variation de durée lue par TalkBack : « plus 15 s » se lit d'après le signe affiché. */
@Composable
@ReadOnlyComposable
fun spokenHoldDelta(variation: Double): String {
    val seconds = variation.roundToInt()
    if (seconds == 0) return stringResource(R.string.delta_same)
    return (if (seconds < 0) "\u2212" else "+") + spokenDuration(seconds.absoluteValue)
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
    ExerciseMetric.Hold -> R.string.curve_metric_hold
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

/**
 * Meilleure série et 1RM d'un exercice dans une séance (§ 15.9.1, point 7) : « Meilleure série 80 × 8 ·
 * 1RM ~101 kg » ; « 12 reps » à 0 kg ; 1RM omis s'il vaut 0 ; cardio « 30 min · 5,2 km » (« 30 min »
 * sans distance) ; au temps « 1:30 », « 1:30 · 10 kg » avec du lest, sans 1RM (§ 15.18.6). `null` sans
 * série.
 */
@Composable
@ReadOnlyComposable
fun bestSetLine(best: WorkoutSet?, oneRepMax: Double, kind: SetKind): String? {
    val isCardio = kind == SetKind.Cardio
    val bestText = best?.let { set ->
        when {
            kind == SetKind.Timed -> {
                val duration = HoldDuration.format(set.durationSeconds ?: 0)
                if (set.weight > 0) stringResource(R.string.exercise_fiche_hold_set_weight, duration, formatNumber(set.weight)) else duration
            }
            isCardio -> {
                val minutes = formatNumber(set.duration ?: 0.0)
                val km = set.distance?.takeIf { it > 0 }
                if (km != null) {
                    stringResource(R.string.exercise_fiche_cardio_set, minutes, formatNumber(km))
                } else {
                    stringResource(R.string.exercise_fiche_cardio_set_minutes, minutes)
                }
            }
            set.weight > 0 -> stringResource(R.string.workout_previous_strength, formatNumber(set.weight), set.reps)
            else -> stringResource(R.string.workout_previous_reps, set.reps)
        }
    }
    return listOfNotNull(
        bestText?.let { stringResource(R.string.exercise_fiche_best_set, it) },
        oneRepMax.takeIf { it > 0 && kind == SetKind.Strength }?.let { stringResource(R.string.exercise_fiche_one_rm, formatNumber(it)) },
    ).joinToString(" · ").ifEmpty { null }
}
