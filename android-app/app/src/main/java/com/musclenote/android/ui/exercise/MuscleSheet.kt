package com.musclenote.android.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.MuscleRole
import com.musclenote.android.data.catalog.MuscleRules
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.designsystem.components.BodyMap
import com.musclenote.android.ui.designsystem.components.BodyMapSize
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio

/**
 * Sheet « Muscle » (SPEC § 15.17.7) : la carte d'une vue (le muscle plein, décorative), l'étiquette
 * « Muscle », le nom et le nom savant, le rôle, puis les exercices non archivés qui le travaillent
 * (principaux puis secondaires). [trailing] et [onExerciseClick] font la ligne d'un exercice : chevron
 * vers sa fiche depuis la fiche, case du sélecteur depuis le filtre (`null` = sans action).
 * [onRemoveFromFilter] (filtre) ajoute « Retirer du filtre » au-dessus de « Fermer ».
 */
@Composable
internal fun MuscleSheetContent(
    muscle: Muscle,
    definitions: List<ExerciseDefinition>,
    trailing: (ExerciseDefinition) -> ListRowTrailing?,
    onExerciseClick: (ExerciseDefinition) -> (() -> Unit)?,
    onClose: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    onRemoveFromFilter: (() -> Unit)? = null,
) {
    val uses = remember(muscle, definitions) { MuscleRules.usesOf(muscle, definitions) }
    SheetSurface(modifier.verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Studio.spacing.m)) {
            BodyMap(BodyMapSize.SingleView, primary = listOf(muscle), view = muscle.view, onRaised = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                Text(
                    stringResource(R.string.muscle_sheet_label).uppercase(appLocale()),
                    style = Studio.typography.label,
                    color = Studio.colors.textSecondary,
                )
                Text(
                    stringResource(muscle.nameRes),
                    Modifier.semantics { heading() },
                    style = Studio.typography.titleScreen,
                )
                Text(stringResource(muscle.scientificRes), style = Studio.typography.bodyLine, color = Studio.colors.textSecondary)
            }
        }
        Text(stringResource(muscle.roleRes), style = Studio.typography.paragraph)
        Column {
            SectionHeader(stringResource(R.string.muscle_sheet_exercises), trailingText = formatInteger(uses.size.toDouble()))
            uses.forEachIndexed { index, use ->
                ListRow(
                    title = exerciseName(use.definition),
                    subtitle = stringResource(if (use.role == MuscleRole.Primary) R.string.muscle_use_primary else R.string.muscle_use_secondary),
                    trailing = trailing(use.definition),
                    onClick = onExerciseClick(use.definition),
                    showDivider = index < uses.lastIndex,
                )
            }
        }
        if (onRemoveFromFilter != null) {
            StudioButton(
                stringResource(R.string.muscle_filter_remove),
                onRemoveFromFilter,
                Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
            )
        }
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}
