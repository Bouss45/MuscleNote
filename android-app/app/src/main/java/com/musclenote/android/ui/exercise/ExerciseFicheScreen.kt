package com.musclenote.android.ui.exercise

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.progress.ChartPeriod
import com.musclenote.android.data.progress.CurveSummary
import com.musclenote.android.data.progress.ExerciseMetric
import com.musclenote.android.data.progress.ProgressCurves
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.data.progress.SessionPoint
import com.musclenote.android.data.workout.ExerciseSession
import com.musclenote.android.data.workout.WorkoutStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.designsystem.components.CurvePoint
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.HeroNumber
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.PeriodSelector
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.TrendCurve
import com.musclenote.android.ui.designsystem.templates.FicheLazyTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.systemScreenInsets
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fiche exercice (SPEC § 15.9.1), gabarit fiche : la dernière séance de la période en chiffre héros
 * et sa variation depuis le premier point, la courbe de la métrique (touchable), la période, le
 * record personnel et le 1RM estimé, puis toutes les séances — toucher → éditeur ([onEditWorkout]).
 * Seules les séances terminées et les séries validées comptent (🔄 D15).
 */
@Composable
fun ExerciseFicheScreen(
    exerciseId: String,
    onBack: () -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    // Archivés compris : un exercice supprimé mais encore référencé garde son historique.
    val definition = remember(customExercises, exerciseId) { exerciseDefinitions(customExercises).firstOrNull { it.id == exerciseId } }
    val zone = container.time.zone()
    val today = rememberToday()
    if (definition == null) {
        FicheLazyTemplate(
            modifier = modifier,
            topBar = { FicheTopBar(onBack) },
            titleBlock = {},
            insets = insets,
        ) {
            block("not-found") {
                EmptyState(
                    icon = R.drawable.ds_ic_monitoring,
                    title = stringResource(R.string.exercise_history_not_found),
                    message = null,
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                )
            }
        }
        return
    }
    val history = remember(workouts, exerciseId, zone) { WorkoutStats.exerciseHistory(workouts, exerciseId, zone) }
    ExerciseFicheContent(definition, history, today, zone, onBack, onEditWorkout, modifier, insets)
}

/**
 * Contenu de la fiche pour [history] (séances de l'exercice, la plus récente d'abord) : séparé du
 * dépôt pour les aperçus. [initialPeriod] : période affichée à l'ouverture (« Tout »).
 */
@Composable
internal fun ExerciseFicheContent(
    definition: ExerciseDefinition,
    history: List<ExerciseSession>,
    today: LocalDate,
    zone: ZoneId,
    onBack: () -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
    initialPeriod: ChartPeriod = ChartPeriod.All,
) {
    val dates = rememberDateFormats(zone)
    val metric = remember(definition, history) { ProgressStats.exerciseMetric(definition.isCardio, history) }
    val series = remember(history, metric, zone) { ProgressStats.exerciseSeries(history, metric, zone) }
    var periodIndex by rememberSaveable { mutableIntStateOf(initialPeriod.ordinal) }
    val period = ChartPeriod.entries[periodIndex]
    val periodSeries = remember(series, period, today) { ProgressCurves.inPeriod(series, period, today) { it.day } }
    val summary = remember(periodSeries) { ProgressCurves.summarize(periodSeries) { it.value } }
    val drawn = remember(periodSeries) { ProgressCurves.downsample(periodSeries, ProgressCurves.CURVE_MAX_POINTS, { it.x }, { it.value }) }
    val curvePoints = remember(drawn) { drawn.map { CurvePoint(it.x.toFloat(), it.value.toFloat()) } }
    var selected by remember(drawn) { mutableStateOf<Int?>(null) }
    // Le chiffre défile au changement de période, pas quand il suit le doigt.
    var animateHero by remember { mutableStateOf(true) }

    val title: @Composable () -> Unit = {
        val group = stringResource(definition.muscleGroup.labelRes)
        FicheTitle(
            exerciseName(definition),
            subtitle = if (history.isEmpty()) {
                group
            } else {
                stringResource(
                    R.string.exercise_fiche_subtitle,
                    group,
                    pluralStringResource(R.plurals.common_sessions_count, history.size, history.size),
                    dates.monthYear(history.last().date),
                )
            },
        )
    }

    if (history.isEmpty()) {
        FicheLazyTemplate(modifier = modifier, topBar = { FicheTopBar(onBack) }, titleBlock = title, insets = insets) {
            block("empty") {
                EmptyState(
                    icon = R.drawable.ds_ic_monitoring,
                    title = stringResource(R.string.exercise_fiche_empty_title),
                    message = stringResource(R.string.exercise_fiche_empty_message),
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                )
            }
        }
        return
    }

    val recordId = summary?.record?.workoutId
    FicheLazyTemplate(
        modifier = modifier,
        topBar = { FicheTopBar(onBack) },
        titleBlock = title,
        insets = insets,
        hero = { FicheHero(summary, drawn.getOrNull(selected ?: -1), periodSeries.size, metric, dates, today, animateHero) },
        curve = {
            val point = selected?.let(drawn::getOrNull)
            TrendCurve(
                points = curvePoints,
                contentDescription = ficheCurveDescription(summary, metric, period, dates, today),
                startLabel = drawn.firstOrNull()?.takeIf { drawn.size > 1 }?.let { dates.shortDay(it.day, today) },
                endLabel = drawn.lastOrNull()?.let { dates.shortDay(it.day, today) },
                selectedIndex = selected,
                selectedLabel = point?.let { dates.shortDay(it.day, today) },
                onSelect = { index ->
                    selected = index
                    animateHero = false
                },
                emptyText = stringResource(R.string.exercise_fiche_period_empty_hint),
            )
        },
        period = {
            PeriodSelector(
                options = ChartPeriod.entries.map { stringResource(it.labelRes()) },
                selectedIndex = periodIndex,
                onSelect = { index ->
                    animateHero = true
                    periodIndex = index
                },
                descriptions = ChartPeriod.entries.map { stringResource(it.descriptionRes()) },
            )
        },
        stats = { RecordStats(definition, history, series, metric) },
    ) {
        section(
            key = "sessions",
            header = { SectionHeader(stringResource(R.string.exercise_fiche_sessions), trailingText = formatCount(history.size)) },
        ) {
            itemsIndexed(history, key = { _, session -> session.workoutId }, contentType = { _, _ -> "session" }) { index, session ->
                SessionHistoryRow(
                    session = session,
                    isCardio = definition.isCardio,
                    isRecord = session.workoutId == recordId,
                    dates = dates,
                    today = today,
                    onClick = { onEditWorkout(session.workoutId) },
                    showDivider = index < history.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun formatCount(count: Int): String = formatInteger(count.toDouble())

/**
 * Chiffre héros (§ 15.9.1, point 3) : au repos, la dernière séance de la période et sa variation
 * depuis le premier point ; au toucher, le point [touched] ; période vide : « — ».
 */
@Composable
private fun FicheHero(
    summary: CurveSummary<SessionPoint>?,
    touched: SessionPoint?,
    pointCount: Int,
    metric: ExerciseMetric,
    dates: DateFormats,
    today: LocalDate,
    animate: Boolean,
) {
    if (summary == null) {
        HeroNumber("—", label = stringResource(R.string.exercise_fiche_period_empty), animate = animate)
        return
    }
    val shown = touched ?: summary.last
    val variation = when {
        pointCount < 2 -> null
        touched != null -> ProgressCurves.variation(from = summary.first.value, to = touched.value)
        else -> summary.variation
    }
    HeroNumber(
        value = metricNumber(shown.value, metric),
        label = if (touched != null) {
            dates.sessionDate(touched.date, today)
        } else {
            stringResource(R.string.exercise_fiche_last_session, dates.sessionDate(summary.last.date, today))
        },
        unit = metricUnit(metric),
        delta = variation?.let { metricDelta(it, metric) },
        trend = variation?.let { ProgressCurves.direction(it).trend() } ?: Trend.Neutral,
        deltaContext = variation?.let { stringResource(R.string.delta_since, dates.shortDay(summary.first.day, today)) },
        animate = animate,
    )
}

/** Description TalkBack de la courbe de la fiche : période, départ, arrivée, variation. */
@Composable
private fun ficheCurveDescription(
    summary: CurveSummary<SessionPoint>?,
    metric: ExerciseMetric,
    period: ChartPeriod,
    dates: DateFormats,
    today: LocalDate,
): String {
    val end = summary?.let { CurveEnd(metricText(it.last.value, metric), dates.shortDay(it.last.day, today)) }
    return curveDescription(
        curve = stringResource(metric.curveLabelRes()),
        period = stringResource(period.descriptionRes()),
        start = summary?.let { CurveEnd(metricText(it.first.value, metric), dates.shortDay(it.first.day, today)) },
        end = end,
        change = summary?.variation?.let { metricDelta(it, metric) },
    )
}

/**
 * Rangée de stats (§ 15.9.1, point 6), sur tout l'historique : record personnel (séance la plus
 * longue en cardio) et 1RM estimé s'il est > 0.
 */
@Composable
private fun RecordStats(definition: ExerciseDefinition, history: List<ExerciseSession>, series: List<SessionPoint>, metric: ExerciseMetric) {
    val record = series.maxOf { it.value }
    val oneRepMax = if (definition.isCardio) 0.0 else history.maxOf { it.bestOneRepMax }
    StatRow(
        buildList {
            add(
                Stat(
                    metricText(record, metric),
                    stringResource(if (definition.isCardio) R.string.exercise_fiche_stat_longest else R.string.exercise_fiche_stat_record),
                ),
            )
            if (oneRepMax > 0) add(Stat(stringResource(R.string.common_kg_value, formatNumber(oneRepMax)), stringResource(R.string.exercise_fiche_stat_one_rm)))
        },
    )
}

/**
 * Une séance de l'exercice (§ 15.9.1, point 7) : date, meilleure série et 1RM, volume (ou séries,
 * ou durée en cardio), « Record » sur la séance qui détient le record de la période.
 */
@Composable
private fun SessionHistoryRow(
    session: ExerciseSession,
    isCardio: Boolean,
    isRecord: Boolean,
    dates: DateFormats,
    today: LocalDate,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val subtitle = bestSetLine(
        best = ProgressStats.bestSet(session.sets, isCardio),
        oneRepMax = if (isCardio) 0.0 else session.bestOneRepMax,
        isCardio = isCardio,
    )
    val value = when {
        isCardio -> stringResource(R.string.common_min_value, formatNumber(session.totalDuration))
        session.volume > 0 -> stringResource(R.string.session_volume_kg, formatInteger(session.volume))
        else -> pluralStringResource(R.plurals.common_sets_count, session.sets.size, session.sets.size)
    }
    ListRow(
        title = dates.sessionDate(session.date, today),
        subtitle = subtitle,
        trailing = ListRowTrailing.Value(
            value = value,
            delta = if (isRecord) stringResource(R.string.exercise_fiche_record) else null,
            trend = Trend.Up,
        ),
        onClick = onClick,
        showDivider = showDivider,
    )
}
