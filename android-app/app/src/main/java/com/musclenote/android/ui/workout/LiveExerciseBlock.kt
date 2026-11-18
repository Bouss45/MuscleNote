package com.musclenote.android.ui.workout

import androidx.annotation.StringRes
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
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.HoldDuration
import com.musclenote.android.data.workout.LoadSuggestion
import com.musclenote.android.data.workout.ReferenceSession
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.data.workout.SetNumbering
import com.musclenote.android.ui.designsystem.components.AddSetRow
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.ExerciseBlockHeader
import com.musclenote.android.ui.designsystem.components.SetField
import com.musclenote.android.ui.designsystem.components.SetRow
import com.musclenote.android.ui.designsystem.components.SetRowState
import com.musclenote.android.ui.designsystem.components.SetRowTimer
import com.musclenote.android.ui.designsystem.components.SetTableHeader
import com.musclenote.android.ui.designsystem.components.SetTimerButton
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.SupersetLink
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.exercise.spokenDuration
import java.util.Locale

/** Signe moins des pas et variations (U+2212), pas le tiret. */
internal const val MINUS = "−"

/** Valeur absente de la colonne « Préc. ». */
private const val NO_PREVIOUS = "—"

/**
 * Bloc d'un exercice de la séance en cours (§ 15.7.1) : en-tête (titre — poignée de
 * glisser-déposer via [headerModifier] —, fiche, options), puces « dernière fois » et suggestion,
 * note, colonnes, lignes de série avec le repos après [restAnchor], « + Série ». Exercice au temps
 * (§ 15.18.2) : colonnes Durée · Kg et le chrono de chaque série ; [timer] = le chrono en cours de la
 * séance (une seule série), [holdSeconds] son temps écoulé, [onTimerClick] Démarrer / Arrêter.
 * Séries de chauffe (§ 15.21.2) : « C » dans la colonne « # », séries de travail numérotées sans elles,
 * « Préc. » par rangs séparés. Exercice lié au suivant (§ 15.20.2) : [linkToNext] = la description
 * TalkBack du trait de supersérie posé sous « + Série ».
 */
@Composable
internal fun LiveExerciseBlock(
    name: String,
    exercise: WorkoutExercise,
    kind: SetKind,
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
    timer: SetTimerState? = null,
    holdSeconds: Int = 0,
    onTimerClick: (WorkoutSet) -> Unit = {},
    linkToNext: String? = null,
) {
    val fields = SetEntryRules.fields(kind)
    val (firstField, secondField) = fields[0] to fields[1]
    val target = entry.target?.takeIf { it.exerciseId == exercise.exerciseId }
    Column(modifier.fillMaxWidth()) {
        ExerciseBlockHeader(
            name,
            onOpenDetails,
            onMore,
            modifier = headerModifier,
            detailsDescription = stringResource(R.string.workout_exercise_details_for, name),
            moreDescription = stringResource(R.string.workout_exercise_options_for, name),
        ) {
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
        val numbers = SetNumbering.of(exercise.sets)
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                val setNumber = numbers[index]
                val previous = reference?.previous(setNumber)
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
                val running = timer?.setId == set.id
                fun display(field: SetEntryField) = when {
                    active == field -> SetEntryRules.display(entry.text, field)
                    field == SetEntryField.Hold && running -> HoldDuration.format(holdSeconds)
                    // Ancienne série en reps d'un exercice devenu au temps (§ 15.18.1).
                    field == SetEntryField.Hold && set.durationSeconds == null -> NO_PREVIOUS
                    else -> SetEntryRules.format(field.read(set), field, locale)
                }
                val number = setNumber.rank
                val warmup = setNumber.warmup
                @Composable
                fun spoken(field: SetEntryField): String = when {
                    field != SetEntryField.Hold || (active == field) -> display(field)
                    running -> spokenDuration(holdSeconds)
                    set.durationSeconds == null -> display(field)
                    else -> spokenDuration(set.durationSeconds)
                }
                val previousLabel = previous?.let { previousText(it, kind, locale) } ?: NO_PREVIOUS
                val isLast = index == exercise.sets.lastIndex
                SetRow(
                    index = if (warmup) stringResource(R.string.workout_warmup_short) else number.toString(),
                    previous = previousLabel,
                    weight = display(firstField),
                    reps = display(secondField),
                    state = if (set.completed) SetRowState.Done else SetRowState.Todo,
                    checkDescription = stringResource(checkLabel(warmup, set.completed), number),
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
                    weightDescription = stringResource(valueLabel(warmup), number, fieldLabel(firstField), spoken(firstField)),
                    repsDescription = stringResource(valueLabel(warmup), number, fieldLabel(secondField), spoken(secondField)),
                    timer = if (kind == SetKind.Timed) {
                        SetRowTimer(
                            button = when {
                                running -> SetTimerButton.Stop
                                set.completed -> SetTimerButton.None
                                timer != null -> SetTimerButton.Unavailable
                                else -> SetTimerButton.Start
                            },
                            description = stringResource(timerLabel(warmup, running), number),
                            onClick = { onTimerClick(set) },
                        )
                    } else {
                        null
                    },
                )
                if (restAnchor == set.id) rest()
            }
        }
        AddSetRow(
            stringResource(R.string.workout_add_set_row),
            onAddSet,
            description = stringResource(R.string.workout_add_set_to, name),
        )
        if (linkToNext != null) {
            // Le trait descend jusqu'au bloc suivant, à travers l'écart de la liste.
            SupersetLink(stringResource(R.string.superset_label), linkToNext, extendBelow = Studio.spacing.l)
        }
    }
}

/** Case d'une série (§ 2.6) : « Valider la série 2 », « Chauffe 1 validée »… (§ 15.21.2). */
@StringRes
internal fun checkLabel(warmup: Boolean, completed: Boolean): Int = when {
    warmup && completed -> R.string.workout_warmup_checked
    warmup -> R.string.workout_warmup_check
    completed -> R.string.workout_set_checked
    else -> R.string.workout_set_check
}

/** Valeur d'une série lue par TalkBack : « Série 2, Kg : 80 », « Chauffe 1, Kg : 40 ». */
@StringRes
internal fun valueLabel(warmup: Boolean): Int =
    if (warmup) R.string.workout_warmup_value_description else R.string.workout_set_value_description

/** Bouton du chrono d'une série au temps (§ 15.18.4). */
@StringRes
private fun timerLabel(warmup: Boolean, running: Boolean): Int = when {
    warmup && running -> R.string.workout_warmup_timer_stop
    warmup -> R.string.workout_warmup_timer_start
    running -> R.string.workout_timer_stop
    else -> R.string.workout_timer_start
}

/**
 * Colonne « Préc. » (§ 15.7.2) : « 80 × 8 », « 12 reps », « 30 · 5,2 », « 30 min » ; au temps
 * (§ 15.18.2) « 1:00 », « 1:00 · 10 » avec du lest, « — » sans durée.
 */
@Composable
private fun previousText(set: WorkoutSet, kind: SetKind, locale: Locale): String {
    fun number(field: SetEntryField) = SetEntryRules.format(field.read(set), field, locale)
    val isCardio = kind == SetKind.Cardio
    return when {
        kind == SetKind.Timed && set.durationSeconds == null -> NO_PREVIOUS
        kind == SetKind.Timed && set.weight > 0.0 ->
            stringResource(R.string.workout_previous_hold_weight, number(SetEntryField.Hold), number(SetEntryField.Weight))
        kind == SetKind.Timed -> number(SetEntryField.Hold)
        isCardio && (set.distance ?: 0.0) > 0.0 ->
            stringResource(R.string.workout_previous_cardio, number(SetEntryField.Duration), number(SetEntryField.Distance))
        isCardio -> stringResource(R.string.workout_previous_minutes, number(SetEntryField.Duration))
        set.weight > 0.0 -> stringResource(R.string.workout_previous_strength, number(SetEntryField.Weight), set.reps)
        else -> stringResource(R.string.workout_previous_reps, set.reps)
    }
}

/**
 * Puce de suggestion (§ 15.7.3) : « +2,5 kg », « +1 rep », « Même charge », « Mêmes reps » ; au temps
 * « +5 s », « Même durée » (§ 15.18.2).
 */
@Composable
private fun suggestionText(suggestion: LoadSuggestion, locale: Locale): String = when {
    suggestion.hold && suggestion.increase -> stringResource(R.string.workout_suggest_plus_seconds, HoldDuration.SUGGESTION_SECONDS)
    suggestion.hold -> stringResource(R.string.workout_suggest_same_duration)
    suggestion.increase && suggestion.bodyWeight -> stringResource(R.string.workout_suggest_plus_rep)
    suggestion.increase -> stringResource(
        R.string.workout_suggest_plus_kg,
        SetEntryRules.format(LoadSuggestion.WEIGHT_STEP, SetEntryField.Weight, locale),
    )
    suggestion.bodyWeight -> stringResource(R.string.workout_suggest_same_reps)
    else -> stringResource(R.string.workout_suggest_same_weight)
}
