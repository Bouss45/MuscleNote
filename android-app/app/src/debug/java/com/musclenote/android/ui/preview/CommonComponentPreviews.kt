package com.musclenote.android.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.ui.components.ExerciseSearchBar
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.MuscleGroupChips
import com.musclenote.android.ui.components.ScreenHeader

/* Composants partagés de `ui/components/` et petits composants communs à plusieurs écrans. */

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
