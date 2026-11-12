package com.musclenote.android.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.ui.components.ChartPoint
import com.musclenote.android.ui.components.ExerciseSearchBar
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.LineChart
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.MuscleGroupChips
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.progress.CardTitle
import com.musclenote.android.ui.progress.ProgressCard
import com.musclenote.android.ui.progress.ProgressEmptyState
import com.musclenote.android.ui.progress.WeighInDialog
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.text.NumberFormat

/* Composants partagés de `ui/components/` et petits composants communs à plusieurs écrans. */

private val weightPoints = listOf(
    ChartPoint("01/10", 79.4),
    ChartPoint("08/10", 79.1),
    ChartPoint("15/10", 78.8),
    ChartPoint("22/10", 78.9),
    ChartPoint("29/10", 78.3),
    ChartPoint("05/11", 78.0),
    ChartPoint("12/11", 77.8),
)

/** Poids de corps : courbe, graduations, point maximal étiqueté. */
@ComponentPreviews
@Composable
private fun LineChartPreview() = ComponentPreview {
    val format = NumberFormat.getNumberInstance(appLocale()).apply { maximumFractionDigits = 1 }
    ProgressCard {
        CardTitle(stringResource(R.string.common_body_weight), subtitle = "6 semaines")
        LineChart(points = weightPoints, color = MuscleNoteTheme.colors.info, unit = "kg", formatValue = { format.format(it) })
    }
}

/** Calories avec la ligne d'objectif. */
@ComponentPreviews
@Composable
private fun LineChartGoalPreview() = ComponentPreview {
    LineChart(
        points = listOf(2310, 2580, 2450, 2720, 2390, 2640, 1850).mapIndexed { i, kcal -> ChartPoint("${6 + i}/11", kcal.toDouble()) },
        color = MuscleNoteTheme.colors.calories,
        height = 200.dp,
        unit = "kcal",
        goal = 2600.0,
    )
}

/** Moins de deux points : texte d'attente. */
@ComponentPreviews
@Composable
private fun LineChartOnePointPreview() = ComponentPreview {
    LineChart(points = weightPoints.take(1), height = 130.dp, unit = "kg")
}

/** En-tête d'onglet. */
@ComponentPreviews
@Composable
private fun ScreenHeaderPreview() = ComponentPreview {
    ScreenHeader(title = stringResource(R.string.settings_title))
}

/** Badges des 9 groupes musculaires, taille normale et petite. */
@ComponentPreviews
@Composable
private fun MuscleGroupBadgePreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (group in MuscleGroup.entries) MuscleGroupBadge(group)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (group in MuscleGroup.entries) MuscleGroupBadge(group, small = true)
        }
    }
}

/** Recherche d'exercice et filtre par groupe (sélecteur d'exercices). */
@ComponentPreviews
@Composable
private fun ExerciseSearchPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseSearchBar(search = "", onSearchChange = {}, group = null, onGroupChange = {})
        ExerciseSearchBar(search = "dévelop", onSearchChange = {}, group = MuscleGroup.Pecs, onGroupChange = {})
        MuscleGroupChips(selected = MuscleGroup.Jambes, onSelect = {}, allowAll = true)
    }
}

/** Confirmation en ligne (retrait d'un exercice, suppression d'une séance). */
@ComponentPreviews
@Composable
private fun InlineConfirmPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InlineConfirm(
            message = stringResource(R.string.exercise_card_remove_confirm, "Développé couché"),
            confirmLabel = stringResource(R.string.exercise_card_remove),
            onCancel = {},
            onConfirm = {},
        )
        InlineConfirm(
            message = stringResource(R.string.exercise_card_remove_confirm, "Squat"),
            confirmLabel = stringResource(R.string.common_delete),
            onCancel = {},
            onConfirm = {},
            hint = stringResource(R.string.progress_empty_no_session_hint),
            confirmIcon = R.drawable.ic_delete,
            large = true,
        )
    }
}

/** État vide des onglets de Progrès. */
@ComponentPreviews
@Composable
private fun ProgressEmptyStatePreview() = ComponentPreview {
    ProgressEmptyState(
        title = stringResource(R.string.progress_cal_no_data),
        hint = stringResource(R.string.progress_cal_no_data_hint),
        emoji = "🔥",
    )
}

/** Modale de saisie (FormDialog) : nouvelle pesée. */
@PhonePreviews
@Composable
private fun FormDialogPreview() = ScreenPreview {
    WeighInDialog(today = PreviewData.today, onDismiss = {}, onSave = { _, _ -> })
}
