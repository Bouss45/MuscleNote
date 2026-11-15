package com.musclenote.android.ui.journal

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.journal.CalendarRules
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.data.journal.JournalTotals
import com.musclenote.android.data.journal.WeekLabel
import com.musclenote.android.data.journal.WeekProgress
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.templates.HubLazyTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Onglet Journal (SPEC § 15.8.4), gabarit hub en liste paresseuse et listes groupées (🔄 D40) : le
 * calendrier du mois en tête (🔄 D41, § 15.15.1 ; toucher un jour → sheet « Journée »), le groupe
 * des totaux depuis le début, puis un groupe par semaine (du lundi au dimanche, « 2/3 » ou « 3/3 ·
 * validée », § 15.15.3) avec une ligne par séance terminée — toucher → résumé ([onOpenSession],
 * § 15.12), appui long → modifier ([onEditWorkout]), changer la date, supprimer. Sans séance : état
 * vide qui démarre ([onStartWorkout]) ou reprend ([onOpenWorkout]) une séance, sans calendrier.
 */
@Composable
fun JournalScreen(
    onOpenSession: (workoutId: String) -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = tabScreenInsets(),
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val currentWorkout by repository.currentWorkout.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    val today = rememberToday()
    val dates = rememberDateFormats(zone)
    val weeks = remember(workouts, zone) { JournalRules.weeks(workouts, zone) }
    val totals = remember(workouts) { JournalRules.totals(workouts) }
    val actions = rememberSessionActions()

    // 🔄 D41 : calendrier — mois de la plus ancienne donnée au mois courant, ouvert sur le mois courant.
    val nutrition = container.nutrition
    val bodyWeights by nutrition.bodyWeights.collectAsStateWithLifecycle()
    val tracker by nutrition.tracker.collectAsStateWithLifecycle()
    val supplements by nutrition.supplements.collectAsStateWithLifecycle()
    val currentMonth = YearMonth.from(today)
    val firstMonth = remember(workouts, bodyWeights, tracker, supplements, zone, today) {
        CalendarRules.firstMonth(workouts, bodyWeights, tracker, supplements, zone, today)
    }
    val months = remember(firstMonth, currentMonth) { CalendarRules.months(firstMonth, currentMonth) }
    val sessionCounts = remember(workouts, zone) { CalendarRules.sessionCounts(workouts, zone) }
    // Le premier mois change (import, séance la plus ancienne supprimée) → retour au mois courant.
    val calendarState = key(firstMonth) { rememberPagerState(initialPage = months.lastIndex) { months.size } }
    var openDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    // 🔄 D40 : listes groupées (DESIGN.md § 10.1) — « Depuis le début » et chaque semaine en groupe.
    HubLazyTemplate(
        modifier = modifier,
        titleBar = { ScreenTitleBar(stringResource(R.string.journal_title)) },
        insets = insets,
        grouped = true,
        highlight = if (weeks.isEmpty()) {
            null
        } else {
            { JournalCalendar(months, calendarState, sessionCounts, today, dates, onDayClick = { openDay = it }) }
        },
    ) {
        if (weeks.isEmpty()) {
            block("empty") {
                val inProgress = currentWorkout != null
                EmptyState(
                    icon = R.drawable.ds_ic_calendar_month,
                    title = stringResource(R.string.journal_empty_title),
                    message = stringResource(R.string.journal_empty_message),
                    actionLabel = stringResource(if (inProgress) R.string.journal_empty_resume else R.string.journal_empty_start),
                    onAction = if (inProgress) onOpenWorkout else onStartWorkout,
                )
            }
            return@HubLazyTemplate
        }
        groupBlock("totals", label = { SectionHeader(stringResource(R.string.journal_all_time), grouped = true) }) {
            TotalsStats(totals)
        }
        weeks.forEach { week ->
            group(
                key = week.monday ?: "unknown",
                label = {
                    // 🔄 D41 : « 2/3 », « 3/3 · validée » (règle de la série de l'Accueil) ; « Date inconnue » garde son compteur.
                    val progress = week.monday?.let { CalendarRules.weekProgress(week.sessions.size) }
                    SectionHeader(
                        weekTitle(week.monday, today, dates),
                        trailingText = progress?.let { weekProgressText(it) }
                            ?: pluralStringResource(R.plurals.common_sessions_count, week.sessions.size, week.sessions.size),
                        trailingTrend = if (progress?.validated == true) Trend.Up else Trend.Neutral,
                        trailingDescription = progress?.let { weekProgressDescription(it) },
                        grouped = true,
                    )
                },
                items = week.sessions,
                itemKey = { summary -> summary.workout.id },
                contentType = "session",
            ) { summary, position ->
                SessionRow(
                    summary = summary,
                    dates = dates,
                    onOpen = { onOpenSession(summary.workout.id) },
                    onLongPress = { actions.actionsFor = summary.workout.id },
                    showDivider = !position.isLast,
                )
            }
        }
    }
    SessionActionSheets(actions, workouts, onEditWorkout)
    openDay?.let { day -> DaySheet(day, onOpenSession, onDismiss = { openDay = null }) }
}

/** « 2/3 », « 3/3 · validée » (§ 15.15.3). */
@Composable
private fun weekProgressText(progress: WeekProgress): String = stringResource(
    if (progress.validated) R.string.journal_week_validated else R.string.journal_week_count,
    progress.sessions,
    progress.target,
)

/** Lu par TalkBack : « 2 séances sur 3 », « 3 séances sur 3, semaine validée ». */
@Composable
private fun weekProgressDescription(progress: WeekProgress): String = pluralStringResource(
    if (progress.validated) R.plurals.journal_week_validated_description else R.plurals.journal_week_count_description,
    progress.sessions,
    progress.sessions,
    progress.target,
)

/** « Depuis le début » : séances, volume (t), temps en salle et moyenne par séance (§ 3.7), dans leur groupe. */
@Composable
private fun TotalsStats(totals: JournalTotals) {
    val stats = buildList {
        add(Stat(formatInteger(totals.sessions.toDouble()), stringResource(R.string.journal_stat_sessions)))
        add(Stat(stringResource(R.string.journal_tonnes, formatInteger(totals.volumeTonnes.toDouble())), stringResource(R.string.journal_stat_volume)))
        totals.gymMinutes?.let { minutes ->
            val value = if (minutes < MINUTES_PER_HOUR) {
                stringResource(R.string.duration_minutes, minutes)
            } else {
                stringResource(R.string.duration_hours, (minutes / MINUTES_PER_HOUR.toDouble()).roundToInt())
            }
            add(Stat(value, stringResource(R.string.journal_stat_gym_time)))
        }
        totals.averageMinutes?.let { add(Stat(compactDurationText(it), stringResource(R.string.journal_stat_average))) }
    }
    StatRow(stats, Modifier.padding(Studio.spacing.m))
}

private const val MINUTES_PER_HOUR = 60

/** « Cette semaine », « Semaine dernière », « Semaine du 26 oct. » (année si elle diffère), « Date inconnue ». */
@Composable
private fun weekTitle(monday: LocalDate?, today: LocalDate, dates: DateFormats): String {
    if (monday == null) return stringResource(R.string.journal_unknown_date)
    return when (val label = JournalRules.weekLabel(monday, today)) {
        WeekLabel.Current -> stringResource(R.string.journal_week_current)
        WeekLabel.Previous -> stringResource(R.string.journal_week_previous)
        is WeekLabel.Of -> stringResource(
            R.string.journal_week_of,
            if (label.withYear) dates.dayMonthYear(label.monday) else dates.dayShortMonth(label.monday),
        )
    }
}
