package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Un jour du calendrier : [label] = le chiffre affiché ; [description] = ce que lit TalkBack (date
 * en toutes lettres, séances, « aujourd'hui », « à venir »).
 */
@Immutable
data class CalendarDay(
    val date: LocalDate,
    val label: String,
    val hasSession: Boolean,
    val isToday: Boolean,
    val isUpcoming: Boolean,
    val description: String,
)

/** Un mois : titre (« Novembre 2026 »), cases rangée par rangée (`null` = case vide), résumé. */
@Immutable
data class CalendarMonth(val title: String, val cells: List<CalendarDay?>, val summary: String)

/** Rangées réservées : un mois tient sur 4 à 6 rangées, la hauteur ne saute pas d'un mois à l'autre. */
private const val MaxRows = 6
private val CellHeight = 48.dp
private val DayCircle = 36.dp
private val TodayRing = 44.dp
private val RingWidth = 1.5.dp
/** Marge du chiffre dans son rond : en grand texte, « 28 » rétrécit avant de toucher le bord. */
private val DayNumberInset = 6.dp
private val HeaderInset = 4.dp
private val WeekdaysGap = 4.dp
private val GridGap = 6.dp
private val SummaryVertical = 14.dp
private const val PressedInverseAlpha = 0.8f

/**
 * Calendrier du mois — `MonthCalendar` (DESIGN.md § 9.1, SPEC § 15.15.1) : groupe du héros du
 * Journal. En-tête (mois en `titleItem`, flèches « mois précédent / suivant »), initiales des jours
 * du lundi au dimanche, grille de jours dans un [HorizontalPager] (glisser change de mois), résumé
 * sous un filet. Un jour à séance = rond plein `inverse` ; aujourd'hui = anneau `textSecondary`
 * (autour du rond plein s'il y a une séance) ; à venir = chiffre secondaire, inactif. Chaque jour est
 * un bouton de 48 de haut, lu par TalkBack avec sa [CalendarDay.description].
 *
 * [month] donne le mois d'une page de [state] (pages du plus ancien au plus récent).
 */
@Composable
fun MonthCalendar(
    state: PagerState,
    month: @Composable (page: Int) -> CalendarMonth,
    weekdayLabels: List<String>,
    previousDescription: String,
    nextDescription: String,
    openDayLabel: String,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val current = month(state.currentPage)
    val motion = Studio.motion.standard
    ListGroup(modifier) {
        Column(Modifier.padding(start = Studio.spacing.s, end = Studio.spacing.s, top = Studio.spacing.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current.title,
                    Modifier.weight(1f).padding(start = HeaderInset).semantics { heading() },
                    style = Studio.typography.titleItem,
                )
                StudioIconButton(
                    R.drawable.ds_ic_chevron_left,
                    previousDescription,
                    onClick = { scope.launch { state.animateScrollToPage(state.currentPage - 1, animationSpec = motion.spec()) } },
                    enabled = state.currentPage > 0,
                    tone = ButtonTone.Subtle,
                )
                StudioIconButton(
                    R.drawable.ds_ic_chevron_right,
                    nextDescription,
                    onClick = { scope.launch { state.animateScrollToPage(state.currentPage + 1, animationSpec = motion.spec()) } },
                    enabled = state.currentPage < state.pageCount - 1,
                    tone = ButtonTone.Subtle,
                )
            }
            Spacer(Modifier.height(WeekdaysGap))
            // Initiales des jours : décoratives, chaque jour se lit en entier.
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = Studio.typography.small.copy(fontWeight = FontWeight.SemiBold),
                        color = Studio.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(GridGap))
            HorizontalPager(
                state = state,
                modifier = Modifier.fillMaxWidth().height(CellHeight * MaxRows),
                verticalAlignment = Alignment.Top,
            ) { page ->
                MonthGrid(month(page).cells, openDayLabel, onDayClick)
            }
        }
        val separator = Studio.colors.separator
        Text(
            current.summary,
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(separator, size = Size(size.width, 1.dp.toPx())) }
                .padding(horizontal = Studio.spacing.screenGrouped, vertical = SummaryVertical),
            style = Studio.typography.caption,
            color = Studio.colors.textSecondary,
        )
    }
}

/** Les rangées d'un mois, 7 cases chacune. */
@Composable
private fun MonthGrid(cells: List<CalendarDay?>, openDayLabel: String, onDayClick: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).heightIn(min = CellHeight), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, openDayLabel) { onDayClick(day.date) }
                    }
                }
            }
        }
    }
}

/** Un jour : rond plein (séance), anneau (aujourd'hui), chiffre ; bouton sauf pour un jour à venir. */
@Composable
private fun DayCell(day: CalendarDay, openDayLabel: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val colors = Studio.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(CellHeight)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = !day.isUpcoming,
                role = Role.Button,
                onClickLabel = openDayLabel,
                onClick = onClick,
            )
            .semantics { contentDescription = day.description },
        contentAlignment = Alignment.Center,
    ) {
        if (day.isToday && day.hasSession) {
            Box(Modifier.size(TodayRing).border(RingWidth, colors.textSecondary, Studio.shapes.full))
        }
        val circle = when {
            // Pressé : l'inverse éclairci, comme le bouton principal.
            day.hasSession -> Modifier.background(if (pressed) colors.inverse.copy(alpha = PressedInverseAlpha) else colors.inverse, Studio.shapes.full)
            day.isToday -> Modifier
                .then(if (pressed) Modifier.background(colors.surfaceGroupPressed, Studio.shapes.full) else Modifier)
                .border(RingWidth, colors.textSecondary, Studio.shapes.full)
            pressed -> Modifier.background(colors.surfaceGroupPressed, Studio.shapes.full)
            else -> Modifier
        }
        Box(Modifier.size(DayCircle).then(circle).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            FitText(
                day.label,
                style = Studio.typography.emphasis.copy(
                    fontWeight = if (day.hasSession || day.isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                color = when {
                    day.hasSession -> colors.onInverse
                    day.isUpcoming -> colors.textSecondary
                    else -> colors.textPrimary
                },
                modifier = Modifier.padding(horizontal = DayNumberInset),
            )
        }
    }
}
