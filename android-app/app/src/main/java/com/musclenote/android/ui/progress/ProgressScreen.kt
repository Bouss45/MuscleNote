package com.musclenote.android.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.progress.DailyStats
import com.musclenote.android.data.progress.DayValue
import com.musclenote.android.data.progress.MuscleGroupProgress
import com.musclenote.android.data.progress.ExerciseProgress
import com.musclenote.android.data.progress.ProgressCurves
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.components.formatSignedDelta
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.designsystem.components.CurvePoint
import com.musclenote.android.ui.designsystem.components.CurveHeightDense
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.MetricSurface
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.SurfaceAction
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.TrendCurve
import com.musclenote.android.ui.designsystem.templates.HubLazyTemplate
import com.musclenote.android.ui.designsystem.templates.HubSections
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.exercise.CurveEnd
import com.musclenote.android.ui.exercise.curveDescription
import com.musclenote.android.ui.exercise.metricDelta
import com.musclenote.android.ui.exercise.metricText
import com.musclenote.android.ui.exercise.trend
import com.musclenote.android.ui.settings.WeighInReminderSheet
import com.musclenote.android.ui.workout.ConfirmSheet
import java.time.LocalDate
import java.time.ZoneId

/** Journal affiché par la section « Calories et eau » : calories (kcal) ou eau (saisie en ml, affichée en L). */
enum class DailyKind { Calories, Water }

/**
 * Onglet Progrès (SPEC § 15.9.2), gabarit hub en liste paresseuse : surface « Poids de corps »,
 * puis « Exercices » (puces des groupes utilisés, stats du groupe, une ligne par exercice → fiche
 * [onOpenExercise]), « Calories et eau », « Pesées ». Calculs aux § 3.10 et § 15.9.3.
 */
@Composable
fun ProgressScreen(
    onOpenExercise: (exerciseId: String) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenNutrition: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = tabScreenInsets(),
    /** Rappel de pesée touché (§ 15.13.4) : « Nouvelle pesée » s'ouvre, puis [onWeighInOpened]. */
    openWeighIn: Boolean = false,
    onWeighInOpened: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val currentWorkout by container.workouts.currentWorkout.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val bodyWeights by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val tracker by container.nutrition.tracker.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    val today = rememberToday()
    val dates = rememberDateFormats(zone)
    val haptics = rememberHaptics()

    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var dailyKind by rememberSaveable { mutableStateOf(DailyKind.Calories) }
    var addingWeighIn by rememberSaveable { mutableStateOf(false) }
    var editingReminder by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openWeighIn) {
        if (openWeighIn) {
            addingWeighIn = true
            onWeighInOpened()
        }
    }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    // Archivés compris : ils nomment encore l'historique (§ 3.9).
    val definitions = remember(customExercises) { exerciseDefinitions(customExercises) }
    val weightSeries = remember(workouts, bodyWeights, zone) { ProgressStats.bodyWeightSeries(workouts, bodyWeights, zone) }
    val counts = remember(workouts, definitions) { ProgressStats.muscleSessionCounts(workouts, definitions) }
    val groups = remember(counts) { MuscleGroup.entries.filter { (counts[it] ?: 0) > 0 } }
    // Par défaut, le groupe qui a le plus de séances (le premier dans l'ordre du § 6.2 à égalité).
    val group = groupId?.let(MuscleGroup::fromId)?.takeIf { it in groups } ?: groups.maxByOrNull { counts.getValue(it) }
    val groupProgress = remember(workouts, definitions, group, zone) { group?.let { ProgressStats.muscleGroupProgress(workouts, definitions, it, zone) } }
    val daily = remember(tracker, dailyKind, today) { DailySummary.of(tracker, dailyKind, today) }

    HubLazyTemplate(
        modifier = modifier,
        titleBar = { ScreenTitleBar(stringResource(R.string.tabs_progress)) },
        highlight = {
            BodyWeightSurface(weightSeries, dates, today, onAdd = { addingWeighIn = true }, onReminder = { editingReminder = true })
        },
        insets = insets,
    ) {
        if (group == null || groupProgress == null) {
            block("exercises-empty") {
                val inProgress = currentWorkout != null
                EmptyState(
                    icon = R.drawable.ds_ic_fitness_center,
                    title = stringResource(R.string.progress_empty_no_session),
                    message = stringResource(R.string.progress_empty_no_session_hint),
                    actionLabel = stringResource(if (inProgress) R.string.journal_empty_resume else R.string.journal_empty_start),
                    onAction = if (inProgress) onOpenWorkout else onStartWorkout,
                )
            }
        } else {
            exercisesSection(groups, group, groupProgress, onSelectGroup = { groupId = it.id }, onOpenExercise = onOpenExercise)
        }
        nutritionSection(daily, today, dates, onSelectKind = { dailyKind = it }, onOpenNutrition = onOpenNutrition)
        weighInsSection(bodyWeights, zone, dates, onDelete = { deleting = it })
    }

    if (addingWeighIn) {
        WeighInSheet(
            today = today,
            lastWeight = weightSeries.lastOrNull(),
            dates = dates,
            onDismiss = { addingWeighIn = false },
            onSave = { day, weight ->
                addingWeighIn = false
                container.launchMutation { container.nutrition.addBodyWeight(day, weight) }
            },
        )
    }

    // Raccourci du rappel de pesée (§ 15.13.3) : la même sheet que dans Paramètres › Rappels.
    if (editingReminder) WeighInReminderSheet(onDismiss = { editingReminder = false })

    deleting?.let { date -> bodyWeights.firstOrNull { it.date == date } }?.let { entry ->
        ConfirmSheet(
            title = stringResource(R.string.progress_weigh_in_delete_title),
            message = stringResource(R.string.progress_weigh_in_delete_message, weighInDay(entry, zone, dates), formatNumber(entry.weight)),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.warning()
                deleting = null
                container.launchMutation { container.nutrition.removeBodyWeight(entry.date) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Jour d'une pesée : « 12 nov. 2026 » (sa date brute si elle est illisible). */
private fun weighInDay(entry: BodyWeightEntry, zone: ZoneId, dates: DateFormats): String =
    parseInstantOrNull(entry.date, zone)?.let(dates::dayMonthYear) ?: entry.date

/** Points d'une courbe de jours, réduits au-delà de [max] (§ 15.9.3). */
private fun dayPoints(series: List<DayValue>, max: Int = ProgressCurves.CURVE_MAX_POINTS, scale: Double = 1.0): List<CurvePoint> =
    ProgressCurves.downsample(series, max, { it.day.toEpochDay().toDouble() }, { it.value })
        .map { CurvePoint(it.day.toEpochDay().toFloat(), (it.value * scale).toFloat()) }

/**
 * Surface « Poids de corps » (§ 15.9.2, point 2) : actuel, évolution en gris (pas d'objectif
 * déclaré), courbe compacte, nombre de jours et min–max ; le bouton + ouvre « Nouvelle pesée », la
 * cloche le rappel de pesée (§ 15.13.3).
 */
@Composable
private fun BodyWeightSurface(series: List<DayValue>, dates: DateFormats, today: LocalDate, onAdd: () -> Unit, onReminder: () -> Unit) {
    val stats = remember(series) { ProgressStats.bodyWeightStats(series) }
    val last = series.lastOrNull()
    val first = series.firstOrNull()
    val change = stats?.change?.takeIf { series.size > 1 }
    val title = stringResource(R.string.progress_weight_title)
    val days = pluralStringResource(R.plurals.common_days_count, series.size, series.size)
    MetricSurface(
        label = if (last == null) title else stringResource(R.string.progress_weight_caption, relativeDay(last.day, today, dates)),
        value = stats?.let { formatNumber(it.current) },
        unit = "kg",
        points = remember(series) { dayPoints(series) },
        curveDescription = curveDescription(
            curve = title,
            period = days,
            start = first?.let { CurveEnd(stringResource(R.string.common_kg_value, formatNumber(it.value)), dates.shortDay(it.day, today)) },
            end = last?.let { CurveEnd(stringResource(R.string.common_kg_value, formatNumber(it.value)), dates.shortDay(it.day, today)) },
            change = change?.let { weightDelta(it) },
        ),
        addDescription = stringResource(R.string.body_weight_add),
        onAdd = onAdd,
        delta = change?.let { weightDelta(it) },
        trend = Trend.Neutral,
        deltaContext = change?.let { stringResource(R.string.delta_since, dates.shortDay(first!!.day, today)) },
        footerStart = stats?.let { days },
        footerEnd = stats?.let { stringResource(R.string.progress_weight_range, formatNumber(it.min), formatNumber(it.max)) },
        emptyMessage = stringResource(R.string.progress_weight_none),
        secondaryAction = SurfaceAction(R.drawable.ds_ic_notifications, stringResource(R.string.weigh_in_reminder_title), onReminder),
    )
}

@Composable
private fun weightDelta(change: Double): String =
    if (change == 0.0) stringResource(R.string.delta_same) else stringResource(R.string.common_kg_value, formatSignedDelta(change))

/** « aujourd'hui », « hier », sinon « 12 nov. ». */
@Composable
private fun relativeDay(day: LocalDate, today: LocalDate, dates: DateFormats): String = when (day) {
    today -> stringResource(R.string.common_today_lower)
    today.minusDays(1) -> stringResource(R.string.common_yesterday_lower)
    else -> dates.shortDay(day, today)
}

/**
 * Section « Exercices » (§ 15.9.2, point 3) : puces des [groups] ayant au moins une séance, stats du
 * groupe choisi, une ligne par exercice (mini-courbe, dernière séance, variation).
 */
private fun HubSections.exercisesSection(
    groups: List<MuscleGroup>,
    group: MuscleGroup,
    progress: MuscleGroupProgress,
    onSelectGroup: (MuscleGroup) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    section(key = "exercises", header = { SectionHeader(stringResource(R.string.progress_exercises)) }) {
        item(key = "exercises-groups", contentType = "chips") {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                ) {
                    groups.forEach { candidate ->
                        FilterChip(stringResource(candidate.labelRes), selected = candidate == group, onClick = { onSelectGroup(candidate) })
                    }
                }
                Spacer(Modifier.height(Studio.spacing.l))
                GroupStats(progress.sessions, progress.maxMetric, progress.maxVolume, isCardio = group == MuscleGroup.Cardio)
                Spacer(Modifier.height(Studio.spacing.xs))
            }
        }
        itemsIndexed(progress.exercises, key = { _, exercise -> "exercise:${exercise.definition.id}" }, contentType = { _, _ -> "exercise" }) { index, exercise ->
            ExerciseRow(exercise, onClick = { onOpenExercise(exercise.definition.id) }, showDivider = index < progress.exercises.lastIndex)
        }
    }
}

/** Stats d'un groupe : séances, charge max (durée max en cardio), vol. max (distance max). */
@Composable
private fun GroupStats(sessions: Int, maxMetric: Double?, maxVolume: Double, isCardio: Boolean) {
    StatRow(
        listOf(
            Stat(formatInteger(sessions.toDouble()), stringResource(R.string.progress_group_sessions)),
            Stat(
                when {
                    maxMetric == null -> "—"
                    isCardio -> stringResource(R.string.common_min_value, formatNumber(maxMetric))
                    else -> stringResource(R.string.common_kg_value, formatNumber(maxMetric))
                },
                stringResource(if (isCardio) R.string.progress_group_max_duration else R.string.progress_group_max_load),
            ),
            Stat(
                // Volume en tonnes au dixième (§ 3.8), distance en km au dixième.
                if (isCardio) {
                    stringResource(R.string.common_km_value, formatOneDecimal(maxVolume))
                } else {
                    stringResource(R.string.journal_tonnes, formatOneDecimal(maxVolume / 1000))
                },
                stringResource(if (isCardio) R.string.progress_group_max_distance else R.string.progress_group_max_volume),
            ),
        ),
    )
}

/**
 * Un exercice : nom, « 19 séances · record 100 kg », mini-courbe de tout l'historique, métrique de
 * la dernière séance et variation depuis la première (la fiche en période « Tout »).
 */
@Composable
private fun ExerciseRow(exercise: ExerciseProgress, onClick: () -> Unit, showDivider: Boolean) {
    val metric = exercise.metric
    val points = remember(exercise) {
        if (exercise.points.size < 2) {
            emptyList()
        } else {
            ProgressCurves.downsample(exercise.points, ProgressCurves.MINI_CURVE_MAX_POINTS, { it.x }, { it.value })
                .map { CurvePoint(it.x.toFloat(), it.value.toFloat()) }
        }
    }
    val trend = exercise.trend
    ListRow(
        title = exerciseName(exercise.definition),
        subtitle = stringResource(
            R.string.progress_exercise_subtitle,
            pluralStringResource(R.plurals.common_sessions_count, exercise.sessionCount, exercise.sessionCount),
            metricText(exercise.record, metric),
        ),
        trailing = ListRowTrailing.CurveValue(
            points = points,
            value = metricText(exercise.points.last().value, metric),
            delta = trend?.let { metricDelta(it, metric) },
            trend = trend?.let { ProgressCurves.direction(it).trend() } ?: Trend.Neutral,
        ),
        onClick = onClick,
        showDivider = showDivider,
    )
}

/** Journal choisi de la section « Calories et eau » : sa série, ses stats (§ 3.10), son objectif actuel. */
private data class DailySummary(val kind: DailyKind, val series: List<DayValue>, val stats: DailyStats?, val goal: Int) {
    val calories: Boolean get() = kind == DailyKind.Calories

    companion object {
        fun of(tracker: DailyTracker, kind: DailyKind, today: LocalDate): DailySummary {
            val calories = kind == DailyKind.Calories
            val log = if (calories) tracker.caloriesLog else tracker.waterLog
            val goal = if (calories) tracker.calorieGoal else tracker.waterGoal
            return DailySummary(
                kind = kind,
                series = ProgressStats.dailySeries(log),
                stats = ProgressStats.dailyStats(log, if (calories) tracker.calorieGoalHistory else tracker.waterGoalHistory, goal, today),
                goal = goal,
            )
        }
    }
}

/**
 * Section « Calories et eau » (§ 15.9.2, point 4) : puces, stats du journal choisi, courbe avec la
 * ligne d'objectif actuel, objectif / meilleure journée / jours suivis ; sans donnée, l'état vide.
 */
private fun HubSections.nutritionSection(
    daily: DailySummary,
    today: LocalDate,
    dates: DateFormats,
    onSelectKind: (DailyKind) -> Unit,
    onOpenNutrition: () -> Unit,
) {
    val calories = daily.calories
    val stats = daily.stats
    val series = daily.series
    section(key = "nutrition", header = { SectionHeader(stringResource(R.string.progress_nutrition)) }) {
        item(key = "nutrition-kinds", contentType = "chips") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
                FilterChip(stringResource(R.string.progress_tab_calories), selected = calories, onClick = { onSelectKind(DailyKind.Calories) })
                FilterChip(stringResource(R.string.progress_tab_water), selected = !calories, onClick = { onSelectKind(DailyKind.Water) })
            }
        }
        if (stats == null) {
            item(key = "nutrition-empty", contentType = "empty") {
                Column {
                    Spacer(Modifier.height(Studio.spacing.l))
                    EmptyState(
                        icon = R.drawable.ds_ic_restaurant,
                        title = stringResource(R.string.progress_cal_no_data),
                        message = stringResource(if (calories) R.string.progress_cal_no_data_hint else R.string.progress_water_no_data_hint),
                        actionLabel = stringResource(R.string.progress_daily_open_nutrition),
                        onAction = onOpenNutrition,
                    )
                }
            }
            return@section
        }
        item(key = "nutrition-stats", contentType = "stats") {
            Column {
                Spacer(Modifier.height(Studio.spacing.l))
                StatRow(
                    listOf(
                        Stat(amount(stats.today.toDouble(), calories), stringResource(if (calories) R.string.progress_daily_today else R.string.progress_daily_today_liters)),
                        Stat(stats.average7?.let { amount(it, calories) } ?: "—", stringResource(R.string.progress_daily_avg7)),
                        Stat(formatInteger(stats.goalDaysHit.toDouble()), stringResource(R.string.progress_daily_goal_days)),
                    ),
                )
            }
        }
        if (series.size >= 2) {
            item(key = "nutrition-curve", contentType = "curve") {
                Column {
                    Spacer(Modifier.height(Studio.spacing.screen))
                    val first = series.first()
                    val last = series.last()
                    TrendCurve(
                        points = remember(series, calories) { dayPoints(series, scale = if (calories) 1.0 else 1 / 1000.0) },
                        contentDescription = curveDescription(
                            curve = stringResource(if (calories) R.string.progress_tab_calories else R.string.progress_tab_water),
                            period = pluralStringResource(R.plurals.common_days_count, series.size, series.size),
                            start = CurveEnd(amountWithUnit(first.value, calories), dates.shortDay(first.day, today)),
                            end = CurveEnd(amountWithUnit(last.value, calories), dates.shortDay(last.day, today)),
                            change = dailyDelta(last.value - first.value, calories),
                        ),
                        height = CurveHeightDense,
                        startLabel = dates.shortDay(first.day, today),
                        endLabel = dates.shortDay(last.day, today),
                        goal = (if (calories) daily.goal.toDouble() else daily.goal / 1000.0).toFloat(),
                        goalLabel = stringResource(R.string.curve_goal),
                    )
                    Spacer(Modifier.height(Studio.spacing.xs))
                }
            }
        }
        item(key = "nutrition-goal", contentType = "recap") {
            ListRow(stringResource(R.string.progress_recap_daily_goal), trailing = ListRowTrailing.Value(amountWithUnit(daily.goal.toDouble(), calories)))
        }
        item(key = "nutrition-best", contentType = "recap") {
            ListRow(stringResource(R.string.progress_recap_best_day), trailing = ListRowTrailing.Value(amountWithUnit(stats.best.toDouble(), calories)))
        }
        item(key = "nutrition-days", contentType = "recap") {
            ListRow(
                stringResource(R.string.progress_recap_days_tracked),
                trailing = ListRowTrailing.Value(formatInteger(stats.daysTracked.toDouble())),
                showDivider = false,
            )
        }
    }
}

/** Valeur affichée d'un journal : kcal entières, ou litres au dixième (saisis en ml). */
@Composable
private fun amount(value: Double, calories: Boolean): String = if (calories) formatInteger(value) else formatOneDecimal(value / 1000)

@Composable
private fun amountWithUnit(value: Double, calories: Boolean): String =
    stringResource(if (calories) R.string.common_kcal_value else R.string.common_liters_value, amount(value, calories))

/** Écart signé d'un journal, avec son unité, pour la description TalkBack. */
@Composable
private fun dailyDelta(change: Double, calories: Boolean): String = when {
    change == 0.0 -> stringResource(R.string.delta_same)
    calories -> stringResource(R.string.common_kcal_value, formatSignedDelta(change, integer = true))
    else -> stringResource(R.string.common_liters_value, formatSignedDelta(ProgressStats.roundTenth(change / 1000)))
}

/**
 * Section « Pesées » (§ 15.9.2, point 5) : les pesées manuelles, la plus récente en haut ; toucher
 * → confirmation de suppression ([onDelete] reçoit la date de la pesée).
 */
private fun HubSections.weighInsSection(bodyWeights: List<BodyWeightEntry>, zone: ZoneId, dates: DateFormats, onDelete: (String) -> Unit) {
    section(
        key = "weigh-ins",
        header = {
            SectionHeader(
                stringResource(R.string.progress_weigh_ins),
                trailingText = bodyWeights.size.takeIf { it > 0 }?.let { formatInteger(it.toDouble()) },
            )
        },
    ) {
        if (bodyWeights.isEmpty()) {
            item(key = "weigh-ins-empty", contentType = "empty") {
                Text(stringResource(R.string.body_weight_empty), style = Studio.typography.paragraph, color = Studio.colors.textSecondary)
            }
            return@section
        }
        itemsIndexed(bodyWeights, key = { _, entry -> "weigh-in:${entry.date}" }, contentType = { _, _ -> "weigh-in" }) { index, entry ->
            ListRow(
                title = weighInDay(entry, zone, dates),
                trailing = ListRowTrailing.Value(stringResource(R.string.common_kg_value, formatNumber(entry.weight))),
                onClick = { onDelete(entry.date) },
                onClickLabel = stringResource(R.string.progress_weigh_in_delete),
                showDivider = index < bodyWeights.lastIndex,
            )
        }
    }
}
