package com.musclenote.android.ui.workout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.dashedBorder
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Carte d'un exercice de la séance en cours (SPEC § 2.5) : en-tête repliable, note, séries,
 * « + Ajouter une série ». Les actions passent par les rappels ; la carte ne garde que son
 * état d'affichage (repliée, note visible, confirmation de retrait).
 */
@Composable
fun ExerciseCard(
    definition: ExerciseDefinition,
    workoutExercise: WorkoutExercise,
    onToggleSet: (setId: String) -> Unit,
    onUpdateSet: (setId: String, transform: (WorkoutSet) -> WorkoutSet) -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onAddSet: () -> Unit,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    val name = exerciseName(definition)
    val sets = workoutExercise.sets
    val completedCount = sets.count { it.completed }

    var collapsed by rememberSaveable { mutableStateOf(false) }
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    var noteText by rememberSaveable { mutableStateOf(workoutExercise.note.orEmpty()) }
    var showNote by rememberSaveable { mutableStateOf(noteText.isNotBlank()) }
    var focusNote by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape),
    ) {
        // En-tête : toucher = replier / déplier.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MuscleGroupBadge(definition.muscleGroup, small = true)
                    if (sets.isNotEmpty()) {
                        val allDone = completedCount == sets.size
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (allDone) colors.success.copy(alpha = 0.13f) else colors.muted)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "$completedCount/${sets.size}",
                                color = if (allDone) colors.success else colors.mutedForeground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            HeaderIcon(
                icon = R.drawable.ic_edit_note,
                tint = if (noteText.isNotBlank()) colors.primary else colors.mutedForeground,
                onClick = {
                    showNote = true
                    collapsed = false
                    focusNote = noteText.isEmpty()
                },
            )
            // Historique de l'exercice : branché à l'étape suivante.
            HeaderIcon(icon = R.drawable.ic_trending_up, tint = colors.primary, onClick = null)
            HeaderIcon(
                icon = R.drawable.ic_close,
                tint = colors.mutedForeground,
                contentDescription = stringResource(R.string.exercise_card_remove),
                onClick = { confirmingRemove = !confirmingRemove },
            )
        }

        if (confirmingRemove) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            InlineConfirm(
                message = stringResource(R.string.exercise_card_remove_confirm, name),
                confirmLabel = stringResource(R.string.exercise_card_remove),
                onCancel = { confirmingRemove = false },
                onConfirm = {
                    haptics.medium()
                    confirmingRemove = false
                    onRemove()
                },
                modifier = Modifier
                    .background(colors.destructive.copy(alpha = 0.06f))
                    .padding(14.dp),
            )
        }

        if (!collapsed) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showNote) {
                    NoteField(
                        text = noteText,
                        requestFocus = focusNote,
                        onFocusRequested = { focusNote = false },
                        onTextChange = {
                            noteText = it
                            onNoteChange(it)
                        },
                        onHide = { showNote = false },
                    )
                } else if (noteText.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showNote = true }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_edit_note), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(13.dp))
                        Text(
                            noteText,
                            color = colors.mutedForeground,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                sets.forEachIndexed { index, set ->
                    key(set.id) {
                        SetRow(
                            set = set,
                            index = index,
                            isCardio = definition.isCardio,
                            onToggle = {
                                haptics.medium()
                                onToggleSet(set.id)
                            },
                            onUpdate = { transform -> onUpdateSet(set.id, transform) },
                            onRemove = { onRemoveSet(set.id) },
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.06f))
                        .dashedBorder(1.dp, colors.primary.copy(alpha = 0.33f), 10.dp)
                        .clickable {
                            haptics.light()
                            onAddSet()
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.exercise_card_add_set), color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Icône d'en-tête de carte ; sans [onClick], elle est seulement affichée. */
@Composable
private fun HeaderIcon(@DrawableRes icon: Int, tint: Color, onClick: (() -> Unit)?, contentDescription: String? = null) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Note de l'exercice (§ 2.5) : enregistrée à chaque frappe ; se masque quand elle perd le focus
 * en étant vide, ou par ✕ tant qu'elle est vide.
 */
@Composable
private fun NoteField(
    text: String,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onTextChange: (String) -> Unit,
    onHide: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.input)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_edit_note),
            contentDescription = null,
            tint = colors.mutedForeground,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 80.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (hadFocus && !it.isFocused && text.isBlank()) onHide()
                    hadFocus = it.isFocused
                },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.exercise_card_note_placeholder), color = colors.mutedForeground, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        if (text.isBlank()) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onHide),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
            }
        }
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
}
