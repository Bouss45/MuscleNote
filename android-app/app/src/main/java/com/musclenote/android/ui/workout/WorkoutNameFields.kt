package com.musclenote.android.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.LabeledField
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.theme.Studio

/** Longueur maximale d'un nom de séance personnalisé (§ 2.3). */
internal const val CUSTOM_NAME_MAX_LENGTH = 30

/**
 * Choix du type d'une séance, partagé par « Démarrer une séance » (§ 15.11.1) et le renommage dans
 * l'éditeur (§ 15.11.3) : les préréglages en puces de filtre, par groupe (PPL, Split, Autre), puis
 * un nom personnalisé au clavier (30 caractères). C'est l'appelant qui garde l'exclusion : toucher
 * une puce vide le nom, écrire un nom désélectionne la puce.
 */
@Composable
internal fun WorkoutTypeFields(
    preset: PresetId?,
    customName: String,
    onSelectPreset: (PresetId) -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Studio.spacing.m)) {
        for (group in PresetGroup.entries) {
            LabeledField(stringResource(group.labelRes())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                ) {
                    for (candidate in PresetId.entries.filter { it.group == group }) {
                        FilterChip(
                            stringResource(candidate.labelRes),
                            selected = candidate == preset,
                            onClick = { onSelectPreset(candidate) },
                        )
                    }
                }
            }
        }
        LabeledField(stringResource(R.string.start_workout_custom_label)) {
            StudioTextField(
                value = customName,
                onValueChange = { onCustomNameChange(it.take(CUSTOM_NAME_MAX_LENGTH)) },
                placeholder = stringResource(R.string.start_workout_custom_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        }
    }
}

private fun PresetGroup.labelRes(): Int = when (this) {
    PresetGroup.Ppl -> R.string.start_workout_group_ppl
    PresetGroup.UpperLower -> R.string.start_workout_group_upper_lower
    PresetGroup.Split -> R.string.start_workout_group_split
    PresetGroup.Other -> R.string.start_workout_group_other
}
