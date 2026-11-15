package com.musclenote.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.home.HomeHighlight
import com.musclenote.android.data.home.HomeRules
import com.musclenote.android.data.home.HomeWeek
import com.musclenote.android.data.home.SessionType
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.data.update.UpdateRules
import com.musclenote.android.data.workout.WeekStatus
import com.musclenote.android.data.workout.WorkoutNotificationRules
import com.musclenote.android.data.workout.WorkoutStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.downloadUpdate
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.ButtonSize
import com.musclenote.android.ui.designsystem.components.ButtonTone
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.DateTitleBar
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.LiveSessionCard
import com.musclenote.android.ui.designsystem.components.NextSessionCard
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.WeekBar
import com.musclenote.android.ui.designsystem.components.WeekBars
import com.musclenote.android.ui.designsystem.templates.HubTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.journal.SessionActionSheets
import com.musclenote.android.ui.journal.SessionRow
import com.musclenote.android.ui.journal.rememberSessionActions
import com.musclenote.android.ui.workout.rememberLiveTimes
import java.time.ZoneId

/**
 * Onglet Accueil (SPEC § 15.8.2), gabarit hub : la date et l'engrenage ; **une** surface — la
 * séance en cours (« Reprendre »), sinon la prochaine séance avec le type suggéré (« Démarrer ») ;
 * puis, s'il y a lieu, l'annonce de mise à jour (§ 3.17), le transfert depuis l'ancienne app, la
 * dernière séance et la semaine en cours. La barre d'onglets est posée par la navigation.
 *
 * [onStartWorkout] ouvre « Démarrer une séance » (avec un type : à l'étape du poids de corps).
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartWorkout: (preset: PresetId?, name: String?) -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenSession: (workoutId: String) -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = tabScreenInsets(),
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val currentWorkout by repository.currentWorkout.collectAsStateWithLifecycle()
    val restTimer by container.restTimer.state.collectAsStateWithLifecycle()
    val localPreferences by container.preferences.localPreferences.collectAsStateWithLifecycle()
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current

    val zone = container.time.zone()
    // Relu à chaque reprise : la semaine en cours change chaque lundi, « il y a N jours » chaque jour.
    val today = rememberToday()
    val dates = rememberDateFormats(zone)
    val highlight = remember(currentWorkout, workouts, today, zone) { HomeRules.highlight(currentWorkout, workouts, today, zone) }
    val week = remember(workouts, today, zone) { HomeRules.thisWeek(workouts, today, zone) }
    val actions = rememberSessionActions()

    // 🔄 D25 : nouvelle Release pour une app installée sans Obtainium, tant qu'elle n'est pas masquée.
    val installedVersion = container.updates.currentVersion
    val update = if (container.updateChecksEnabled) {
        UpdateRules.available(updateState, installedVersion)?.takeIf { it.version != updateState.dismissedVersion }
    } else {
        null
    }
    // 🔄 D4/D5 : tant que l'app n'a aucune donnée, explique le transfert depuis l'app Expo.
    val showMigrate = workouts.isEmpty() && currentWorkout == null && !localPreferences.migrateCardDismissed

    HubTemplate(
        modifier = modifier,
        titleBar = { DateTitleBar(dates.fullDate(today), stringResource(R.string.tabs_settings), onOpenSettings) },
        highlight = {
            when (highlight) {
                is HomeHighlight.Live -> LiveHighlight(highlight.workout, restTimer, onOpenWorkout)
                is HomeHighlight.Next -> NextHighlight(
                    types = highlight.types,
                    onStart = { type ->
                        haptics.heavy()
                        onStartWorkout(type?.preset, type?.lastWorkout?.name?.takeIf { type.preset == null })
                    },
                    onOther = {
                        haptics.light()
                        onStartWorkout(null, null)
                    },
                )
            }
        },
        insets = insets,
    ) {
        if (update != null) {
            UpdateSection(
                release = update,
                installedVersion = installedVersion,
                onDownload = { downloadUpdate(context, update) },
                onLater = {
                    haptics.light()
                    container.launchMutation { container.updates.dismiss(update.version) }
                },
            )
        }
        if (showMigrate) {
            MigrateSection(
                onHide = {
                    haptics.light()
                    container.launchMutation { container.preferences.dismissMigrateCard() }
                },
            )
        }
        workouts.firstOrNull()?.let { last ->
            LastSessionSection(
                workout = last,
                zone = zone,
                dates = dates,
                onOpen = { onOpenSession(last.id) },
                onLongPress = { actions.actionsFor = last.id },
                onSeeAll = onOpenJournal,
            )
            ThisWeekSection(week)
        }
    }
    SessionActionSheets(actions, workouts, onEditWorkout)
}

/** Surface « En cours » : chrono, repos et prochaine série, séries faites, poids, « Reprendre ». */
@Composable
private fun LiveHighlight(workout: Workout, rest: RestTimerState?, onResume: () -> Unit) {
    val container = LocalAppContainer.current
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val definitions = remember(customExercises) { exerciseDefinitions(customExercises).associateBy { it.id } }
    val times = rememberLiveTimes(workout, rest, container.time)
    // § 15.5 : la prochaine série, comme la notification ; un exercice inconnu est ignoré.
    val next = remember(workout, definitions) { WorkoutNotificationRules.nextSet(workout) { it in definitions } }
    val total = workout.exercises.sumOf { it.sets.size }
    val done = workout.exercises.sumOf { e -> e.sets.count { it.completed } }
    LiveSessionCard(
        liveLabel = stringResource(R.string.home_live_label),
        sessionName = workoutDisplayName(workout),
        elapsed = times.elapsed,
        actionLabel = stringResource(R.string.home_resume),
        onAction = onResume,
        restLabel = stringResource(R.string.rest_timer_label),
        restTime = times.rest,
        restDetail = next?.let { set ->
            definitions[set.exercise.exerciseId]?.let { stringResource(R.string.home_rest_detail, exerciseName(it), set.number) }
        },
        setsDone = done,
        setsTotal = total,
        progressStart = pluralStringResource(R.plurals.workout_subtitle_no_weight, total, done, total),
        progressEnd = workout.bodyWeight.takeIf { it > 0 }?.let { stringResource(R.string.common_kg_value, formatNumber(it)) },
    )
}

/**
 * Surface « Prochaine séance » : le type sélectionné (au départ le suggéré), sa dernière fois et ses
 * exercices, les puces des types puis « Autre… », « Démarrer ».
 */
@Composable
private fun NextHighlight(types: List<SessionType>, onStart: (SessionType?) -> Unit, onOther: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val type = types.getOrNull(selected) ?: types.firstOrNull()
    val labels = types.map { workoutDisplayName(it.lastWorkout) }
    val detail = if (type == null) {
        stringResource(R.string.home_next_new_detail)
    } else {
        val lastTime = when (type.daysSince) {
            0 -> stringResource(R.string.home_next_last_today)
            1 -> stringResource(R.string.home_next_last_yesterday)
            else -> pluralStringResource(R.plurals.home_next_last_days, type.daysSince, type.daysSince)
        }
        if (type.exerciseCount > 0) {
            stringResource(
                R.string.home_next_detail,
                lastTime,
                pluralStringResource(R.plurals.common_exercises_count, type.exerciseCount, type.exerciseCount),
            )
        } else {
            lastTime
        }
    }
    NextSessionCard(
        caption = stringResource(R.string.home_next_caption),
        title = type?.let { labels[types.indexOf(it)] } ?: stringResource(R.string.home_next_new),
        detail = detail,
        types = if (types.isEmpty()) emptyList() else labels + stringResource(R.string.home_next_other),
        selectedType = if (type == null) -1 else types.indexOf(type),
        onSelectType = { index -> if (index >= types.size) onOther() else selected = index },
        actionLabel = stringResource(R.string.home_start),
        onAction = { onStart(type) },
    )
}

/** « Mise à jour disponible » (🔄 D25) : la version → téléchargement ; « Plus tard ». */
@Composable
private fun UpdateSection(release: LatestRelease, installedVersion: String, onDownload: () -> Unit, onLater: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
        SectionHeader(stringResource(R.string.home_update_title))
        Column {
            ListRow(
                title = stringResource(R.string.home_update_row, release.version),
                subtitle = stringResource(R.string.home_update_installed, installedVersion),
                trailing = ListRowTrailing.Chevron,
                showDivider = false,
                onClick = onDownload,
            )
            StudioButton(
                stringResource(R.string.home_update_later),
                onLater,
                Modifier.align(Alignment.End),
                variant = ButtonVariant.Text,
                size = ButtonSize.Medium,
                tone = ButtonTone.Subtle,
            )
        }
    }
}

/** « Depuis l'ancienne app » (🔄 D4/D5) : la question, le mode d'emploi, « Masquer ». */
@Composable
private fun MigrateSection(onHide: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
        SectionHeader(stringResource(R.string.home_migrate_section))
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            Text(stringResource(R.string.home_migrate_title), style = Studio.typography.bodyLine, color = Studio.colors.textPrimary)
            Text(stringResource(R.string.home_migrate_text), style = Studio.typography.paragraph, color = Studio.colors.textSecondary)
            StudioButton(
                stringResource(R.string.home_hide),
                onHide,
                Modifier.align(Alignment.End),
                variant = ButtonVariant.Text,
                size = ButtonSize.Medium,
                tone = ButtonTone.Subtle,
            )
        }
    }
}

/** « Dernière séance » : sa ligne (toucher → résumé, appui long → actions) ; « Tout voir » → Journal. */
@Composable
private fun LastSessionSection(
    workout: Workout,
    zone: ZoneId,
    dates: DateFormats,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val summary = remember(workout, zone) { JournalRules.summary(workout, zone) }
    Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
        SectionHeader(
            stringResource(R.string.home_last_session),
            linkLabel = stringResource(R.string.common_see_all),
            onLinkClick = onSeeAll,
        )
        SessionRow(summary, dates, onOpen = onOpen, onLongPress = onLongPress, showDivider = false)
    }
}

/** « Cette semaine » (§ 3.6) : séances de la semaine sur 3, série en cours, record, 8 semaines. */
@Composable
private fun ThisWeekSection(week: HomeWeek) {
    val bars = week.bars
    val counts = bars.map { it.count }
    Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.s)) {
        SectionHeader(stringResource(R.string.home_this_week))
        StatRow(
            listOf(
                Stat(
                    stringResource(R.string.home_week_sessions_value, week.sessions, WorkoutStats.VALIDATED_WEEK_SESSIONS),
                    stringResource(R.string.home_week_sessions_label),
                ),
                Stat(pluralStringResource(R.plurals.home_weeks_value, week.streakWeeks, week.streakWeeks), stringResource(R.string.home_streak_label)),
                Stat(pluralStringResource(R.plurals.home_weeks_value, week.bestWeeks, week.bestWeeks), stringResource(R.string.home_record_label)),
            ),
        )
        if (bars.isNotEmpty()) {
            WeekBars(
                weeks = bars.map { WeekBar(it.count, it.status == WeekStatus.Validated) },
                maxCount = maxOf(counts.max(), WorkoutStats.VALIDATED_WEEK_SESSIONS),
                caption = stringResource(R.string.home_week_bars_caption),
                contentDescription = stringResource(
                    R.string.home_week_bars_description,
                    counts.dropLast(1).joinToString(", "),
                    counts.last(),
                ),
                modifier = Modifier.padding(top = Studio.spacing.xxs),
            )
        }
    }
}
