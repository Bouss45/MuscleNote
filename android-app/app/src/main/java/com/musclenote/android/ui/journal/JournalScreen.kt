package com.musclenote.android.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.data.journal.JournalTotals
import com.musclenote.android.data.journal.WeekLabel
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
import com.musclenote.android.ui.designsystem.templates.HubLazyTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Onglet Journal (SPEC § 15.8.4), gabarit hub en liste paresseuse : les totaux depuis le début,
 * puis une section par semaine (du lundi au dimanche) avec une ligne par séance terminée —
 * toucher → résumé ([onOpenSession], § 15.12), appui long → modifier ([onEditWorkout]), changer la
 * date, supprimer. Sans
 * séance : état vide qui démarre ([onStartWorkout]) ou reprend ([onOpenWorkout]) une séance.
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

    HubLazyTemplate(
        modifier = modifier,
        titleBar = { ScreenTitleBar(stringResource(R.string.journal_title)) },
        insets = insets,
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
        block("totals") { TotalsSection(totals) }
        weeks.forEach { week ->
            section(
                key = week.monday ?: "unknown",
                header = {
                    SectionHeader(
                        weekTitle(week.monday, today, dates),
                        trailingText = pluralStringResource(R.plurals.common_sessions_count, week.sessions.size, week.sessions.size),
                    )
                },
            ) {
                itemsIndexed(week.sessions, key = { _, summary -> summary.workout.id }, contentType = { _, _ -> "session" }) { index, summary ->
                    SessionRow(
                        summary = summary,
                        dates = dates,
                        onOpen = { onOpenSession(summary.workout.id) },
                        onLongPress = { actions.actionsFor = summary.workout.id },
                        showDivider = index < week.sessions.lastIndex,
                    )
                }
            }
        }
    }
    SessionActionSheets(actions, workouts, onEditWorkout)
}

/** « Depuis le début » : séances, volume (t), temps en salle et moyenne par séance (§ 3.7). */
@Composable
private fun TotalsSection(totals: JournalTotals) {
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
    Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.s)) {
        SectionHeader(stringResource(R.string.journal_all_time))
        StatRow(stats)
    }
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
