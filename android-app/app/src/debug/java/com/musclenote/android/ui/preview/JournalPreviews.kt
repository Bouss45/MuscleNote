package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.journal.CalendarRules
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.journal.DaySheetContent
import com.musclenote.android.ui.journal.SessionActionsContent
import com.musclenote.android.ui.journal.SessionRow
import com.musclenote.android.ui.journal.WorkoutDateForm
import java.time.LocalDate

/*
 * Pièces du Journal et de la « Dernière séance » de l'Accueil (SPEC § 15.8.4) : ligne d'une séance,
 * sheet de ses actions (appui long), formulaire « Modifier la date », sheet « Journée » d'un jour du
 * calendrier (§ 15.15.2). Données de PreviewData.
 */

/** Lignes : séance de force (volume), séance la plus récente, séance sans durée connue. */
@StudioComponentPreviews
@Composable
private fun SessionRowPreview() = StudioComponentPreview {
    val dates = rememberDateFormats(PreviewData.zone)
    val workouts = PreviewData.workouts
    StateCaption("Séance avec durée et volume")
    SessionRow(JournalRules.summary(workouts[0], PreviewData.zone), dates, onOpen = {}, onLongPress = {})
    StateCaption("Séance finalisée sans durée, sans volume : séries validées")
    SessionRow(
        JournalRules.summary(
            workouts[1].copy(
                durationMinutes = null,
                exercises = workouts[1].exercises.map { e -> e.copy(sets = e.sets.map { it.copy(weight = 0.0) }) },
            ),
            PreviewData.zone,
        ),
        dates,
        onOpen = {},
        onLongPress = {},
        showDivider = false,
    )
}

/** Sheet de l'appui long : modifier, date, supprimer, fermer. */
@StudioComponentPreviews
@Composable
private fun SessionActionsSheetPreview() = StudioComponentPreview {
    SessionActionsContent(
        name = "Pull",
        dateLine = "mer. 11 nov. · 18:30",
        onEdit = {},
        onEditDate = {},
        onDelete = {},
        onClose = {},
        bottomInset = 0.dp,
    )
}

/** « Modifier la date » : préremplie avec la date locale de la séance. */
@StudioComponentPreviews
@Composable
private fun WorkoutDateFormPreview() = StudioComponentPreview {
    WorkoutDateForm(PreviewData.workouts[0], PreviewData.zone, onDismiss = {}, onSave = {}, bottomInset = 0.dp)
}

/** « Modifier la date » après une date impossible : le message remplace la phrase d'aide. */
@StudioComponentPreviews
@Composable
private fun WorkoutDateFormInvalidPreview() = StudioComponentPreview {
    WorkoutDateForm(PreviewData.workouts[0], PreviewData.zone, onDismiss = {}, onSave = {}, bottomInset = 0.dp, initiallyInvalid = true)
}

/** Sheet « Journée » du [day] d'après PreviewData (séances, pesées, compléments, calories et eau). */
@Composable
private fun DaySheetPreview(day: LocalDate, weighIns: List<BodyWeightEntry> = PreviewData.bodyWeights) =
    StudioComponentPreview {
        val dates = rememberDateFormats(PreviewData.zone)
        val journal = CalendarRules.dayJournal(
            day,
            PreviewData.today,
            PreviewData.workouts,
            weighIns,
            PreviewData.tracker,
            PreviewData.supplements,
            PreviewData.taken,
            PreviewData.zone,
        )
        DaySheetContent(journal, title = dates.fullDate(day, PreviewData.today), dates = dates, onOpenSession = {}, onClose = {})
    }

/** Mercredi 11 novembre : séance Pull (poids saisi au début), trois compléments dont la whey en deux doses, calories et eau. */
@StudioComponentPreviews
@Composable
private fun DaySheetFullPreview() = DaySheetPreview(PreviewData.today.minusDays(1))

/** Aujourd'hui (jeudi 12) pesé à midi, sans séance terminée : pesée, créatine cochée, calories et eau en cours. */
@StudioComponentPreviews
@Composable
private fun DaySheetTodayPreview() = DaySheetPreview(PreviewData.today, weighIns = PreviewData.bodyWeights + PreviewData.weighInToday)

/** Un jour de l'an dernier sans rien de noté : la phrase seule, date avec l'année. */
@StudioComponentPreviews
@Composable
private fun DaySheetEmptyPreview() = DaySheetPreview(PreviewData.today.minusYears(1))
