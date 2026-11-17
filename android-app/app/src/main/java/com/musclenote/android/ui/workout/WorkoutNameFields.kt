package com.musclenote.android.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.model.PresetGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.workout.WorkoutTypeRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.designsystem.components.ConfirmSheetContent
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.LabeledField
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.theme.Studio

/** Longueur maximale d'un nom de séance personnalisé (§ 2.3). */
internal const val CUSTOM_NAME_MAX_LENGTH = 30

/**
 * Choix du type d'une séance, partagé par « Démarrer une séance » (§ 15.11.1) et le renommage dans
 * l'éditeur (§ 15.11.3) : en tête « Mes types » (🔄 D45, § 15.19.3 — les noms perso déjà utilisés,
 * masquée sans nom), puis les préréglages en puces de filtre, par groupe (PPL, Haut / Bas, Split,
 * Autre), puis un nom personnalisé au clavier (30 caractères). C'est l'appelant qui garde l'exclusion :
 * toucher une puce de préréglage vide le nom ; toucher une puce de « Mes types » ([onSelectMyType]) ou
 * écrire un nom désélectionne le préréglage. Une puce de « Mes types » est sélectionnée tant que le nom
 * du champ est le sien (sans casse ni espaces en trop) ; son appui long appelle [onRemoveMyType].
 */
@Composable
internal fun WorkoutTypeFields(
    preset: PresetId?,
    customName: String,
    myTypes: List<String>,
    onSelectPreset: (PresetId) -> Unit,
    onSelectMyType: (String) -> Unit,
    onRemoveMyType: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Studio.spacing.m)) {
        if (myTypes.isNotEmpty()) {
            val removeLabel = stringResource(R.string.start_workout_my_type_remove_action)
            LabeledField(stringResource(R.string.start_workout_my_types)) {
                ChipFlow {
                    for (name in myTypes) {
                        FilterChip(
                            name,
                            selected = preset == null && WorkoutTypeRules.isSameType(name, customName),
                            onClick = { onSelectMyType(name.take(CUSTOM_NAME_MAX_LENGTH)) },
                            onLongClick = { onRemoveMyType(name) },
                            onLongClickLabel = removeLabel,
                        )
                    }
                }
            }
        }
        for (group in PresetGroup.entries) {
            LabeledField(stringResource(group.labelRes())) {
                ChipFlow {
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

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
    ) { content() }
}

/**
 * Noms de « Mes types » (§ 15.19.3), calculés à partir des séances terminées et des types retirés
 * des préférences ; suivent leurs changements.
 */
@Composable
internal fun rememberMyWorkoutTypes(container: AppContainer = LocalAppContainer.current): List<String> {
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    return remember(workouts, preferences.removedWorkoutTypes, zone) {
        WorkoutTypeRules.myTypes(workouts, preferences.removedWorkoutTypes, zone)
    }
}

/** Retire [name] de « Mes types » maintenant (§ 15.19.4) ; les séances ne changent pas. */
internal fun AppContainer.removeMyWorkoutType(name: String) {
    launchMutation { preferences.removeWorkoutType(name, time.now()) }
}

/**
 * Confirmation de l'appui long sur une puce de « Mes types » (§ 15.19.4), à la place du contenu de la
 * sheet : « Retirer « nom » ? », [Retirer] [Annuler].
 */
@Composable
internal fun RemoveMyTypeConfirm(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit, bottomInset: Dp) {
    ConfirmSheetContent(
        title = stringResource(R.string.start_workout_my_type_remove_title, name),
        message = stringResource(R.string.start_workout_my_type_remove_message),
        confirmLabel = stringResource(R.string.start_workout_my_type_remove),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        bottomInset = bottomInset,
    )
}

private fun PresetGroup.labelRes(): Int = when (this) {
    PresetGroup.Ppl -> R.string.start_workout_group_ppl
    PresetGroup.UpperLower -> R.string.start_workout_group_upper_lower
    PresetGroup.Split -> R.string.start_workout_group_split
    PresetGroup.Other -> R.string.start_workout_group_other
}
