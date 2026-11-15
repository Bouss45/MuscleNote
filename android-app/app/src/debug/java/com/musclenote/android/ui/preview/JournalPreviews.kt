package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.journal.SessionActionsContent
import com.musclenote.android.ui.journal.SessionRow
import com.musclenote.android.ui.journal.WorkoutDateForm

/*
 * Pièces du Journal et de la « Dernière séance » de l'Accueil (SPEC § 15.8.4) : ligne d'une séance,
 * sheet de ses actions (appui long), formulaire « Modifier la date ». Données de PreviewData.
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
