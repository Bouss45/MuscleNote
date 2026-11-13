package com.musclenote.android.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.musclenote.android.R
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.LoadSuggestion
import com.musclenote.android.data.workout.ReferenceSession
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.designsystem.components.AddSetRow
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.ExerciseBlockHeader
import com.musclenote.android.ui.designsystem.components.SetField
import com.musclenote.android.ui.designsystem.components.SetRow
import com.musclenote.android.ui.designsystem.components.SetRowState
import com.musclenote.android.ui.designsystem.components.SetTableHeader
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.theme.Studio
import java.util.Locale

/** Signe moins des pas et variations (U+2212), pas le tiret. */
internal const val MINUS = "−"

/** Valeur absente de la colonne « Préc. ». */
private const val NO_PREVIOUS = "—"

/**
 * Bloc d'un exercice de la séance en cours (§ 15.7.1) : en-tête (titre — poignée de
 * glisser-déposer via [headerModifier] —, fiche, options), puces « dernière fois » et suggestion,
 * note, colonnes, lignes de série avec le repos après [restAnchor], « + Série ».
 */
@Composable
internal fun LiveExerciseBlock(
    name: String,
    exercise: WorkoutExercise,
    isCardio: Boolean,
    reference: ReferenceSession?,
    referenceDate: String?,
    suggestion: LoadSuggestion?,
    entry: SetEntryState,
    locale: Locale,
    restAnchor: String?,
    rest: @Composable () -> Unit,
    onOpenDetails: () -> Unit,
    onMore: () -> Unit,
    onApplySuggestion: () -> Unit,
    onFieldClick: (WorkoutSet, SetEntryField) -> Unit,
    onPreviousClick: (WorkoutSet, WorkoutSet) -> Unit,
    onCheck: (WorkoutSet) -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    val fields = SetEntryRules.fields(isCardio)
    val (firstField, secondField) = fields[0] to fields[1]
    val target = entry.target?.takeIf { it.exerciseId == exercise.exerciseId }
    Column(modifier.fillMaxWidth()) {
        ExerciseBlockHeader(name, onOpenDetails, onMore, modifier = headerModifier) {
            if (referenceDate != null) {
                val description = stringResource(R.string.workout_last_time, referenceDate)
                StudioChip(
                    referenceDate,
                    icon = R.drawable.ds_ic_history,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
            if (suggestion != null) {
                val text = suggestionText(suggestion, locale)
                StudioChip(
                    text,
                    tone = if (suggestion.increase) ChipTone.Up else ChipTone.Neutral,
                    onClick = onApplySuggestion,
                    onClickLabel = stringResource(R.string.workout_suggest_apply, text),
                )
            }
        }
        val note = exercise.note
        if (!note.isNullOrBlank()) {
            Text(
                note,
                modifier = Modifier
                    .padding(top = Studio.spacing.xs)
                    .clickable(onClick = onMore),
                style = Studio.typography.caption,
                color = Studio.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Studio.spacing.m))
        if (exercise.sets.isNotEmpty()) {
            SetTableHeader(
                stringResource(R.string.workout_col_index),
                stringResource(R.string.workout_col_previous),
                fieldLabel(firstField),
                fieldLabel(secondField),
            )
        }
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                val previous = reference?.previous(index)
                val editingHere = target?.setId == set.id
                val active = target?.field.takeIf { editingHere }
                val requester = remember { BringIntoViewRequester() }
                if (editingHere) {
                    LaunchedEffect(target) {
                        // Après la mise en page : le pavé vient de réduire la liste.
                        withFrameNanos {}
                        withFrameNanos {}
                        requester.bringIntoView()
                    }
                }
                fun display(field: SetEntryField) =
                    if (active == field) entry.text else SetEntryRules.format(field.read(set), field, locale)
                val previousLabel = previous?.let { previousText(it, isCardio, locale) } ?: NO_PREVIOUS
                val number = index + 1
                val isLast = index == exercise.sets.lastIndex
                SetRow(
                    index = number.toString(),
                    previous = previousLabel,
                    weight = display(firstField),
                    reps = display(secondField),
                    state = if (set.completed) SetRowState.Done else SetRowState.Todo,
                    checkDescription = stringResource(if (set.completed) R.string.workout_set_checked else R.string.workout_set_check, number),
                    onCheck = { onCheck(set) },
                    modifier = Modifier.bringIntoViewRequester(requester),
                    editingField = when (active) {
                        firstField -> SetField.Weight
                        secondField -> SetField.Reps
                        else -> null
                    },
                    onFieldClick = { column -> onFieldClick(set, if (column == SetField.Weight) firstField else secondField) },
                    onPreviousClick = previous?.let { { onPreviousClick(set, it) } },
                    previousClickLabel = stringResource(R.string.workout_previous_copy, previousLabel),
                    showDivider = !isLast || restAnchor == set.id,
                )
                if (restAnchor == set.id) rest()
            }
        }
        AddSetRow(stringResource(R.string.workout_add_set_row), onAddSet)
    }
}

/** Colonne « Préc. » (§ 15.7.2) : « 80 × 8 », « 12 reps », « 30 · 5,2 », « 30 min ». */
@Composable
private fun previousText(set: WorkoutSet, isCardio: Boolean, locale: Locale): String {
    fun number(field: SetEntryField) = SetEntryRules.format(field.read(set), field, locale)
    return when {
        isCardio && (set.distance ?: 0.0) > 0.0 ->
            stringResource(R.string.workout_previous_cardio, number(SetEntryField.Duration), number(SetEntryField.Distance))
        isCardio -> stringResource(R.string.workout_previous_minutes, number(SetEntryField.Duration))
        set.weight > 0.0 -> stringResource(R.string.workout_previous_strength, number(SetEntryField.Weight), set.reps)
        else -> stringResource(R.string.workout_previous_reps, set.reps)
    }
}

/** Puce de suggestion (§ 15.7.3) : « +2,5 kg », « +1 rep », « Même charge », « Mêmes reps ». */
@Composable
private fun suggestionText(suggestion: LoadSuggestion, locale: Locale): String = when {
    suggestion.increase && suggestion.bodyWeight -> stringResource(R.string.workout_suggest_plus_rep)
    suggestion.increase -> stringResource(
        R.string.workout_suggest_plus_kg,
        SetEntryRules.format(LoadSuggestion.WEIGHT_STEP, SetEntryField.Weight, locale),
    )
    suggestion.bodyWeight -> stringResource(R.string.workout_suggest_same_reps)
    else -> stringResource(R.string.workout_suggest_same_weight)
}
