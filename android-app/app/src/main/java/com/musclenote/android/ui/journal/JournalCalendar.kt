package com.musclenote.android.ui.journal

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.musclenote.android.R
import com.musclenote.android.data.journal.CalendarRules
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.designsystem.components.CalendarDay
import com.musclenote.android.ui.designsystem.components.CalendarMonth
import com.musclenote.android.ui.designsystem.components.MonthCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * Calendrier du Journal (SPEC § 15.15.1, 🔄 D41) : [months] (du plus ancien au mois courant), une
 * page de [state] par mois ; [counts] = séances terminées par jour local de début. Toucher un jour
 * passé ou aujourd'hui → [onDayClick] (sheet « Journée »).
 */
@Composable
internal fun JournalCalendar(
    months: List<YearMonth>,
    state: PagerState,
    counts: Map<LocalDate, Int>,
    today: LocalDate,
    dates: DateFormats,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    // « L M M J V S D », « M T W T F S S » : initiales de la langue, du lundi au dimanche.
    val weekdays = remember(locale) { CalendarRules.WEEK.map { it.getDisplayName(TextStyle.NARROW_STANDALONE, locale) } }
    MonthCalendar(
        state = state,
        month = { page -> calendarMonth(months[page.coerceIn(months.indices)], counts, today, dates) },
        weekdayLabels = weekdays,
        previousDescription = stringResource(R.string.journal_calendar_previous),
        nextDescription = stringResource(R.string.journal_calendar_next),
        openDayLabel = stringResource(R.string.journal_calendar_open_day),
        onDayClick = onDayClick,
        modifier = modifier,
    )
}

/** Un mois du calendrier : titre « Novembre 2026 », cases, résumé « 6 séances en novembre ». */
@Composable
internal fun calendarMonth(month: YearMonth, counts: Map<LocalDate, Int>, today: LocalDate, dates: DateFormats): CalendarMonth {
    val cells = CalendarRules.monthGrid(month).cells.map { day -> day?.let { calendarDay(it, counts[it] ?: 0, today, dates) } }
    val total = CalendarRules.monthSessions(counts, month)
    val name = dates.monthName(month)
    val summary = if (total == 0) {
        stringResource(R.string.journal_calendar_month_none, name)
    } else {
        pluralStringResource(R.plurals.journal_calendar_month_sessions, total, total, name)
    }
    return CalendarMonth(dates.monthTitle(month), cells, summary)
}

/**
 * Un jour : son chiffre, et ce que lit TalkBack — « jeudi 12 novembre, 2 séances », « dimanche
 * 15 novembre, aujourd'hui, aucune séance », « lundi 16 novembre, à venir ».
 */
@Composable
private fun calendarDay(day: LocalDate, sessions: Int, today: LocalDate, dates: DateFormats): CalendarDay {
    val upcoming = day > today
    val todayPart = if (day == today) stringResource(R.string.journal_calendar_today) else null
    val sessionsPart = when {
        upcoming -> stringResource(R.string.journal_calendar_upcoming)
        sessions > 0 -> pluralStringResource(R.plurals.journal_calendar_sessions, sessions, sessions)
        else -> stringResource(R.string.journal_calendar_no_session)
    }
    return CalendarDay(
        date = day,
        label = day.dayOfMonth.toString(),
        hasSession = sessions > 0,
        isToday = day == today,
        isUpcoming = upcoming,
        description = listOfNotNull(dates.fullDateInSentence(day), todayPart, sessionsPart).joinToString(", "),
    )
}
