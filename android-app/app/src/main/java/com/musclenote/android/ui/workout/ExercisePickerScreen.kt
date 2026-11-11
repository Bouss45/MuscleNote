package com.musclenote.android.ui.workout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ExerciseSearchBar
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.MuscleGroupChips
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.filterExercises
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Sélecteur d'exercices de la séance en cours (SPEC § 2.7) : recherche et filtre par groupe,
 * ajouts multiples (l'écran reste ouvert), création, modification et suppression des exercices
 * perso (§ 3.9 : archivés s'ils sont référencés). Les exercices archivés ne sont pas proposés.
 */
@Composable
fun ExercisePickerScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val current by repository.currentWorkout.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    var search by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }

    var showNewForm by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newGroup by rememberSaveable { mutableStateOf(MuscleGroup.FALLBACK) }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editGroup by rememberSaveable { mutableStateOf(MuscleGroup.FALLBACK) }

    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    val definitions = remember(customExercises) { exerciseDefinitions(customExercises).filterNot { it.archived } }
    val filtered = filterExercises(definitions, search, group)
    val addedIds = current?.exercises?.mapTo(HashSet()) { it.exerciseId }.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderButton(R.drawable.ic_close, colors.foreground, stringResource(R.string.common_back), onClose)
                Text(
                    stringResource(R.string.exercise_picker_title),
                    color = colors.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                HeaderButton(R.drawable.ic_add, colors.primary, stringResource(R.string.exercise_picker_new_exercise)) {
                    showNewForm = !showNewForm
                    editingId = null
                }
            }
            ExerciseSearchBar(
                search = search,
                onSearchChange = { search = it },
                group = group,
                onGroupChange = { group = it },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showNewForm) {
                item(key = "new") {
                    ExerciseForm(
                        title = stringResource(R.string.exercise_picker_new_exercise),
                        name = newName,
                        onNameChange = { newName = it },
                        placeholder = stringResource(R.string.exercise_picker_name_placeholder),
                        group = newGroup,
                        onGroupChange = { newGroup = it },
                        onCancel = null,
                        submitLabel = stringResource(R.string.common_add),
                        onSubmit = {
                            if (newName.isNotBlank()) {
                                haptics.medium()
                                val name = newName
                                val muscleGroup = newGroup
                                container.launchMutation { repository.addCustomExercise(name, muscleGroup) }
                                newName = ""
                                showNewForm = false
                            }
                        },
                    )
                }
            }

            items(filtered, key = { it.id }) { definition ->
                val added = definition.id in addedIds
                val editing = editingId == definition.id && definition.isCustom && !added
                val deleting = deletingId == definition.id && definition.isCustom && !added
                val name = exerciseName(definition)
                Column {
                    if (editing) {
                        ExerciseForm(
                            title = stringResource(R.string.exercise_picker_edit_exercise),
                            name = editName,
                            onNameChange = { editName = it },
                            placeholder = stringResource(R.string.exercise_picker_name_placeholder),
                            group = editGroup,
                            onGroupChange = { editGroup = it },
                            onCancel = { editingId = null },
                            submitLabel = stringResource(R.string.common_save),
                            onSubmit = {
                                if (editName.isNotBlank()) {
                                    haptics.medium()
                                    val id = definition.id
                                    val newExerciseName = editName
                                    val muscleGroup = editGroup
                                    container.launchMutation { repository.updateCustomExercise(id, newExerciseName, muscleGroup) }
                                    editingId = null
                                }
                            },
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        )
                    }
                    ExerciseRow(
                        definition = definition,
                        name = name,
                        added = added,
                        editing = editing,
                        deleting = deleting,
                        onAdd = {
                            haptics.light()
                            container.launchMutation { repository.addExerciseToWorkout(definition.id) }
                        },
                        onEdit = {
                            haptics.light()
                            editingId = definition.id
                            editName = definition.customName.orEmpty()
                            editGroup = definition.muscleGroup
                            deletingId = null
                        },
                        onDelete = {
                            haptics.light()
                            editingId = null
                            deletingId = if (deleting) null else definition.id
                        },
                    )
                    if (deleting) {
                        InlineConfirm(
                            message = stringResource(R.string.exercise_picker_delete_confirm, name),
                            hint = stringResource(R.string.exercise_picker_delete_hint),
                            confirmLabel = stringResource(R.string.common_delete),
                            confirmIcon = R.drawable.ic_delete,
                            onCancel = { deletingId = null },
                            onConfirm = {
                                haptics.warning()
                                val id = definition.id
                                container.launchMutation { repository.deleteCustomExercise(id) }
                                deletingId = null
                                editingId = null
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                .background(colors.destructive.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    colors.destructive.copy(alpha = 0.27f),
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                                )
                                .padding(12.dp),
                        )
                    }
                }
            }

            item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * Ligne d'exercice : nom et badge du groupe. Déjà dans la séance → surlignée, cochée et
 * désactivée ; sinon toucher l'ajoute. Un exercice perso non ajouté a aussi éditer et supprimer.
 */
@Composable
private fun ExerciseRow(
    definition: ExerciseDefinition,
    name: String,
    added: Boolean,
    editing: Boolean,
    deleting: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val top = if (editing) 0.dp else 12.dp
    val bottom = if (deleting) 0.dp else 12.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val borderColor = when {
        added -> colors.primary.copy(alpha = 0.27f)
        editing -> colors.primary.copy(alpha = 0.33f)
        deleting -> colors.destructive.copy(alpha = 0.27f)
        else -> colors.border
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (added) colors.primary.copy(alpha = 0.08f) else colors.card)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = !added, onClick = onAdd)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, color = if (added) colors.primary else colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            MuscleGroupBadge(definition.muscleGroup, small = true)
        }
        if (added) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        } else {
            if (definition.isCustom) {
                SmallActionButton(R.drawable.ic_edit, colors.mutedForeground, onEdit)
                SmallActionButton(R.drawable.ic_delete, colors.destructive, onDelete)
            }
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(20.dp))
        }
    }
}

/** Formulaire « Nouvel exercice » / « Modifier l'exercice » : nom (focus auto) et groupe. */
@Composable
private fun ExerciseForm(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    placeholder: String,
    group: MuscleGroup,
    onGroupChange: (MuscleGroup) -> Unit,
    onCancel: (() -> Unit)?,
    submitLabel: String,
    onSubmit: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val colors = MuscleNoteTheme.colors
    val focusRequester = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.primary.copy(alpha = 0.33f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        val fieldShape = RoundedCornerShape(10.dp)
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(fieldShape)
                        .background(colors.input)
                        .border(1.dp, colors.border, fieldShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (name.isEmpty()) Text(placeholder, color = colors.mutedForeground, fontSize = 15.sp)
                    inner()
                }
            },
        )
        MuscleGroupChips(selected = group, onSelect = { it?.let(onGroupChange) }, chipBackground = colors.input)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val buttonShape = RoundedCornerShape(10.dp)
            if (onCancel != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .border(1.dp, colors.border, buttonShape)
                        .clickable(onClick = onCancel)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .background(colors.primary)
                    .clickable(onClick = onSubmit)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onCancel != null) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
                }
                Text(submitLabel, color = colors.primaryForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
private fun SmallActionButton(@DrawableRes icon: Int, tint: Color, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(30.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun HeaderButton(@DrawableRes icon: Int, tint: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}
