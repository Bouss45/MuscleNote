package com.musclenote.android.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.data.util.buildLocalDate
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.util.parseIntegerOrNull
import com.musclenote.android.ui.components.ChartPoint
import com.musclenote.android.ui.components.DateField
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.DateSeparator
import com.musclenote.android.ui.components.FormDialog
import com.musclenote.android.ui.components.FormDialogButtons
import com.musclenote.android.ui.components.FormDialogError
import com.musclenote.android.ui.components.FormDialogTitle
import com.musclenote.android.ui.components.LineChart
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.LocalDate
import java.time.ZoneId

private const val WEIGHT_MAX_LENGTH = 6

/**
 * Onglet « Poids corps » (§ 2.10) : actuel / évolution / min–max, graphique des séances et des
 * pesées (un point par jour, § 3.10), journal des pesées manuelles.
 */
@Composable
fun BodyWeightTab(
    workouts: List<Workout>,
    bodyWeights: List<BodyWeightEntry>,
    zone: ZoneId,
    dates: DateFormats,
    onAddWeighIn: () -> Unit,
    onRemoveWeighIn: (date: String) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    val series = remember(workouts, bodyWeights, zone) { ProgressStats.bodyWeightSeries(workouts, bodyWeights, zone) }
    val stats = remember(series) { ProgressStats.bodyWeightStats(series) }

    if (stats != null) {
        val change = stats.change
        StatsRow(
            listOf(
                StatItem("${formatNumber(stats.current)} kg", stringResource(R.string.progress_weight_current), colors.info),
                StatItem(
                    "${if (change > 0) "+" else ""}${formatOneDecimal(change)} kg",
                    stringResource(R.string.progress_weight_change),
                    when {
                        change > 0 -> colors.destructive
                        change < 0 -> colors.success
                        else -> colors.foreground
                    },
                ),
                StatItem(
                    "${formatNumber(stats.min)}–${formatNumber(stats.max)}",
                    stringResource(R.string.progress_weight_min_max),
                    colors.foreground,
                ),
            ),
        )
    }

    ProgressCard {
        CardTitle(
            stringResource(R.string.progress_weight_chart_title),
            Modifier.padding(bottom = 12.dp),
            subtitle = pluralStringResource(R.plurals.common_days_count, series.size, series.size),
        )
        LineChart(
            points = remember(series, dates) { series.map { ChartPoint(dates.dayMonth(it.day), it.value) } },
            color = colors.info,
            height = 200.dp,
            unit = "kg",
            formatValue = { formatDecimal(it, locale, maxFractionDigits = 1) },
        )
    }

    WeightLogCard(bodyWeights, zone, dates, onAddWeighIn, onRemoveWeighIn)

    if (workouts.isEmpty()) {
        ProgressEmptyState(
            title = stringResource(R.string.progress_empty_no_session),
            hint = stringResource(R.string.progress_empty_no_session_hint),
            icon = R.drawable.ic_fitness_center,
        )
    }
}

/** « Journal de poids » : « + Ajouter une pesée », pesées de la plus récente à la plus ancienne. */
@Composable
private fun WeightLogCard(
    bodyWeights: List<BodyWeightEntry>,
    zone: ZoneId,
    dates: DateFormats,
    onAdd: () -> Unit,
    onRemove: (date: String) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    ProgressCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.body_weight_log_title), Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.primary)
                    .clickable {
                        haptics.light()
                        onAdd()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.body_weight_add), color = colors.primaryForeground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (bodyWeights.isEmpty()) {
            Text(stringResource(R.string.body_weight_empty), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
        }
        for (entry in bodyWeights) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_calendar_today), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(13.dp))
                Text(
                    parseInstantOrNull(entry.date, zone)?.let(dates::dayMonthYear) ?: entry.date,
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text("${formatNumber(entry.weight)} kg", color = colors.info, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.medium()
                            onRemove(entry.date)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.common_delete),
                        tint = colors.destructive,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/**
 * Modale « Nouvelle pesée » (§ 2.10) : poids (décimal, focus automatique) et date JJ / MM / AAAA
 * préremplie avec aujourd'hui. Refus si le poids n'est pas dans `0 < poids ≤ 500` ou si la date
 * est invalide (§ 10.3) : « Vérifie le poids et la date », la modale reste ouverte.
 */
@Composable
fun WeighInDialog(today: LocalDate, onDismiss: () -> Unit, onSave: (day: LocalDate, weight: Double) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    var weight by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(today.dayOfMonth.toString()) }
    var month by rememberSaveable { mutableStateOf(today.monthValue.toString()) }
    var year by rememberSaveable { mutableStateOf(today.year.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun save() {
        val value = parseDecimal(weight)
        val date = buildLocalDate(parseIntegerOrNull(year), parseIntegerOrNull(month), parseIntegerOrNull(day))
        if (!BodyWeightRules.isValidWeight(value) || date == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(date.toLocalDate(), value)
        }
    }

    FormDialog(onDismiss = onDismiss) {
        FormDialogTitle(stringResource(R.string.body_weight_new_entry))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.body_weight_weight_label))
            val focusRequester = remember { FocusRequester() }
            val shape = RoundedCornerShape(10.dp)
            BasicTextField(
                value = weight,
                onValueChange = { if (it.length <= WEIGHT_MAX_LENGTH) weight = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.background)
                            .border(1.dp, colors.border, shape)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        if (weight.isEmpty()) {
                            Text(stringResource(R.string.body_weight_weight_placeholder), color = colors.mutedForeground, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.body_weight_date_label))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateField(day, { day = it }, stringResource(R.string.date_day), maxLength = 2)
                DateSeparator("/")
                DateField(month, { month = it }, stringResource(R.string.date_month), maxLength = 2)
                DateSeparator("/")
                DateField(year, { year = it }, stringResource(R.string.date_year), maxLength = 4, weight = 2f, imeAction = ImeAction.Done)
            }
        }
        if (invalid) FormDialogError(stringResource(R.string.body_weight_invalid_entry))
        FormDialogButtons(onCancel = onDismiss, onSave = { save() })
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
