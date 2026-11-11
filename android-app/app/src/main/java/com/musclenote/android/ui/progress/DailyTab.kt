package com.musclenote.android.ui.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.ui.components.ChartPoint
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.LineChart
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.LocalDate
import kotlin.math.roundToLong

/** Journal affiché par [DailyTab] : calories (kcal) ou eau (saisie en ml, affichée en L). */
enum class DailyKind { Calories, Water }

/**
 * Onglets « Calories » et « Eau » (§ 2.10) : aujourd'hui, moyenne des 7 derniers jours
 * calendaires et jours où l'objectif du jour a été atteint (§ 3.10, 🔄 D15) ; graphique avec la
 * ligne de l'objectif **actuel** ; récapitulatif. L'eau est stockée en ml et affichée en litres.
 */
@Composable
fun DailyTab(kind: DailyKind, tracker: DailyTracker, today: LocalDate, dates: DateFormats) {
    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    val calories = kind == DailyKind.Calories
    val color = if (calories) colors.calories else colors.water
    val log = if (calories) tracker.caloriesLog else tracker.waterLog
    val goalHistory = if (calories) tracker.calorieGoalHistory else tracker.waterGoalHistory
    val currentGoal = if (calories) tracker.calorieGoal else tracker.waterGoal

    val series = remember(log) { ProgressStats.dailySeries(log) }
    val stats = remember(log, goalHistory, currentGoal, today) { ProgressStats.dailyStats(log, goalHistory, currentGoal, today) }

    /** Valeur affichée : kcal entières, ou litres au dixième. */
    @Composable
    fun amount(value: Double): String = if (calories) formatInteger(value) else formatOneDecimal(value / 1000)

    if (stats == null) {
        ProgressEmptyState(
            title = stringResource(R.string.progress_cal_no_data),
            hint = stringResource(if (calories) R.string.progress_cal_no_data_hint else R.string.progress_water_no_data_hint),
            emoji = if (calories) "🔥" else "💧",
        )
    } else {
        StatsRow(
            listOf(
                StatItem(
                    amount(stats.today.toDouble()),
                    stringResource(if (calories) R.string.progress_stat_today else R.string.progress_stat_today_liters),
                    color,
                ),
                StatItem(stats.average7?.let { amount(it) } ?: "—", stringResource(R.string.progress_stat_avg7), color),
                StatItem(stats.goalDaysHit.toString(), stringResource(R.string.progress_stat_goal_days_hit), color),
            ),
        )
    }

    if (series.size >= 2) {
        ProgressCard {
            CardTitle(
                stringResource(if (calories) R.string.progress_cal_chart_title else R.string.progress_water_chart_title),
                Modifier.padding(bottom = 12.dp),
                subtitle = stringResource(
                    if (calories) R.string.progress_cal_chart_sub else R.string.progress_water_chart_sub,
                    series.size,
                    amount(currentGoal.toDouble()),
                ),
            )
            if (calories) {
                LineChart(
                    points = remember(series, dates) { series.map { ChartPoint(dates.dayMonth(it.day), it.value) } },
                    color = color,
                    height = 200.dp,
                    unit = "kcal",
                    goal = currentGoal.toDouble(),
                    // ≥ 1000 → « 2,5k ».
                    formatValue = { v ->
                        if (v >= 1000) "${formatDecimal((v / 100).roundToLong() / 10.0, locale, maxFractionDigits = 1)}k" else v.roundToLong().toString()
                    },
                )
            } else {
                LineChart(
                    // ml → L au dixième.
                    points = remember(series, dates) { series.map { ChartPoint(dates.dayMonth(it.day), (it.value / 100).roundToLong() / 10.0) } },
                    color = color,
                    height = 200.dp,
                    unit = "L",
                    goal = currentGoal / 1000.0,
                    formatValue = { formatDecimal(it, locale, maxFractionDigits = 1) },
                )
            }
        }
    }

    if (stats != null) {
        val unit = if (calories) "kcal" else "L"
        RecapCard(
            stringResource(R.string.progress_recap_title),
            listOf(
                stringResource(R.string.progress_recap_daily_goal) to ("${amount(currentGoal.toDouble())} $unit" to colors.foreground),
                stringResource(R.string.progress_recap_best_day) to ("${amount(stats.best.toDouble())} $unit" to color),
                stringResource(R.string.progress_recap_days_tracked) to (stats.daysTracked.toString() to colors.foreground),
            ),
        )
    }
}
