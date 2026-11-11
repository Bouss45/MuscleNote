package com.musclenote.android.ui.workout

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.newId
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ExerciseSearchBar
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.NotFoundScreen
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.filterExercises
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.serialization.Serializable
import java.util.Locale

private const val BODY_WEIGHT_MAX_LENGTH = 6

/**
 * Copie de travail de l'éditeur (§ 2.9) : rien n'est enregistré avant « Enregistrer ». Les
 * exercices à la définition introuvable y restent (masqués, § 10.5), donc conservés.
 */
@Serializable
private data class EditDraft(
    val exercises: List<WorkoutExercise>,
    val bodyWeightText: String,
    val name: String,
    val preset: PresetId?,
) {
    fun mapExercise(exerciseId: String, transform: (WorkoutExercise) -> WorkoutExercise) =
        copy(exercises = exercises.map { if (it.exerciseId == exerciseId) transform(it) else it })

    fun mapSets(exerciseId: String, transform: (List<WorkoutSet>) -> List<WorkoutSet>) =
        mapExercise(exerciseId) { it.copy(sets = transform(it.sets)) }

    companion object {
        fun of(workout: Workout, locale: Locale) = EditDraft(
            exercises = workout.exercises,
            // Prérempli si > 0 ; sinon vide, avec l'ancien poids (ou —) en placeholder.
            bodyWeightText = if (workout.bodyWeight > 0) formatDecimal(workout.bodyWeight, locale) else "",
            name = workout.name,
            preset = workout.preset,
        )
    }
}

/** La copie de travail survit à la recréation de l'activité (JSON dans l'état sauvegardé). */
private val DraftSaver = Saver<EditDraft?, String>(
    save = { draft -> draft?.let { AppJson.storage.encodeToString(EditDraft.serializer(), it) } },
    restore = { AppJson.storage.decodeFromString(EditDraft.serializer(), it) },
)

/**
 * Éditer une séance terminée (SPEC § 2.9) : renommage, poids de corps, exercices et séries (avec
 * case de validation, sans minuteur). Sortie avec des modifications non enregistrées → dialogue
 * « Abandonner les modifications ? » (🔄 D15). [onClose] revient à l'écran précédent.
 */
@Composable
fun EditWorkoutScreen(workoutId: String, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val locale = appLocale()

    val workout = workouts.firstOrNull { it.id == workoutId }
    val initial = remember(workout?.id) { workout?.let { EditDraft.of(it, locale) } }
    var edited by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<EditDraft?>(null) }
    val draft = edited ?: initial
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    if (workout == null || draft == null) {
        NotFoundScreen(stringResource(R.string.edit_workout_not_found), onClose)
        return
    }

    // Lu au moment de l'appel : deux frappes dans la même image partent de la dernière copie.
    fun update(transform: (EditDraft) -> EditDraft) {
        val base = edited ?: initial ?: return
        edited = transform(base)
    }

    val dirty = edited != null && edited != initial
    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose()
    }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    val definitions = remember(customExercises) { exerciseDefinitions(customExercises) }
    val byId = remember(definitions) { definitions.associateBy { it.id } }
    val visible = draft.exercises.filter { it.exerciseId in byId }
    val colors = MuscleNoteTheme.colors

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .imePadding(),
        ) {
            EditHeader(
                name = workoutDisplayName(workout.copy(name = draft.name, preset = draft.preset)),
                onBack = { requestClose() },
                onRename = { showRename = true },
                onSave = {
                    val saved = draft
                    val bodyWeight = parseDecimal(saved.bodyWeightText)
                    container.launchMutation {
                        repository.patchWorkout(workoutId, saved.exercises, bodyWeight, saved.name, saved.preset)
                    }
                    haptics.success()
                    onClose()
                },
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "body-weight") {
                    BodyWeightCard(
                        text = draft.bodyWeightText,
                        placeholder = if (workout.bodyWeight > 0) formatNumber(workout.bodyWeight) else "—",
                        onTextChange = { text -> update { it.copy(bodyWeightText = text) } },
                    )
                }

                items(visible, key = { it.exerciseId }) { workoutExercise ->
                    val exerciseId = workoutExercise.exerciseId
                    val definition = byId.getValue(exerciseId)
                    EditExerciseCard(
                        definition = definition,
                        workoutExercise = workoutExercise,
                        onRemove = {
                            haptics.medium()
                            update { d -> d.copy(exercises = d.exercises.filterNot { it.exerciseId == exerciseId }) }
                        },
                        onToggleSet = { setId ->
                            haptics.medium()
                            update { d -> d.mapSets(exerciseId) { sets -> sets.map { if (it.id == setId) it.copy(completed = !it.completed) else it } } }
                        },
                        onUpdateSet = { setId, transform ->
                            update { d -> d.mapSets(exerciseId) { sets -> sets.map { if (it.id == setId) transform(it) else it } } }
                        },
                        onRemoveSet = { setId ->
                            haptics.light()
                            update { d -> d.mapSets(exerciseId) { sets -> sets.filterNot { it.id == setId } } }
                        },
                        onAddSet = {
                            haptics.light()
                            // Copie de la dernière série, ou 10 × 20 kg / 30 min ; validée (§ 2.9).
                            update { d ->
                                d.mapSets(exerciseId) { sets -> sets + WorkoutRules.newSet(sets.lastOrNull(), definition.isCardio, newId(), completed = true) }
                            }
                        },
                    )
                }

                item(key = "add") { AddExerciseButton(onClick = { showPicker = true }) }

                if (visible.isEmpty()) item(key = "empty") { EmptyExercises() }

                item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
            }
        }

        // Par-dessus l'éditeur, qui reste composé (défilement et saisies conservés).
        if (showPicker) {
            val usedIds = draft.exercises.mapTo(HashSet()) { it.exerciseId }
            EditExercisePicker(
                definitions = definitions.filter { !it.archived && it.id !in usedIds },
                onPick = { definition ->
                    haptics.medium()
                    showPicker = false
                    // Série initiale validée : 10 × 20 kg, ou 30 min / 0 / 0 / 0 en cardio (🔄 D15).
                    val firstSet = WorkoutRules.newSet(null, definition.isCardio, newId(), completed = true)
                    update { d ->
                        if (d.exercises.any { it.exerciseId == definition.id }) d
                        else d.copy(exercises = d.exercises + WorkoutExercise(definition.id, sets = listOf(firstSet)))
                    }
                },
                onClose = { showPicker = false },
            )
        }
    }

    if (showRename) {
        RenameDialog(
            preset = draft.preset,
            name = draft.name,
            onDismiss = { showRename = false },
            onConfirm = { preset, name ->
                haptics.light()
                showRename = false
                update { it.copy(preset = preset, name = name) }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = colors.card,
            title = {
                Text(stringResource(R.string.edit_workout_discard_title), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        haptics.warning()
                        onClose()
                    },
                ) {
                    Text(stringResource(R.string.edit_workout_discard), color = colors.destructive, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.edit_workout_keep_editing), color = colors.foreground, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

/** En-tête : ←, « Modifier la séance », nom affiché (toucher = renommer), Enregistrer. */
@Composable
private fun EditHeader(name: String, onBack: () -> Unit, onRename: () -> Unit, onSave: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.foreground,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.edit_workout_header_label), color = colors.mutedForeground, fontSize = 11.sp)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = stringResource(R.string.edit_workout_rename), onClick = onRename)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        name,
                        color = colors.foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary)
                    .clickable(onClick = onSave)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.common_save), color = colors.primaryForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

/** Poids de corps : champ décimal de 6 caractères, tout sélectionné au focus. */
@Composable
private fun BodyWeightCard(text: String, placeholder: String, onTextChange: (String) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) value = value.copy(selection = TextRange(0, value.text.length))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.common_body_weight),
            color = colors.mutedForeground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        val fieldShape = RoundedCornerShape(8.dp)
        Row(
            Modifier
                .clip(fieldShape)
                .background(colors.input)
                .border(1.dp, colors.border, fieldShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { changed ->
                    if (changed.text.length <= BODY_WEIGHT_MAX_LENGTH) {
                        val textChanged = changed.text != value.text
                        value = changed
                        if (textChanged) onTextChange(changed.text)
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = colors.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .widthIn(min = 48.dp, max = 72.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (value.text.isEmpty()) {
                            Text(placeholder, color = colors.mutedForeground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        inner()
                    }
                },
            )
            Text("kg", color = colors.mutedForeground, fontSize = 12.sp)
        }
    }
}

/** Carte d'un exercice de la copie : nom, badge, ✕ (retrait immédiat), séries, « + Ajouter une série ». */
@Composable
private fun EditExerciseCard(
    definition: ExerciseDefinition,
    workoutExercise: WorkoutExercise,
    onRemove: () -> Unit,
    onToggleSet: (setId: String) -> Unit,
    onUpdateSet: (setId: String, transform: (WorkoutSet) -> WorkoutSet) -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onAddSet: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(exerciseName(definition), color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                MuscleGroupBadge(definition.muscleGroup, small = true)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.exercise_card_remove), tint = colors.mutedForeground, modifier = Modifier.size(18.dp))
            }
        }
        workoutExercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                SetRow(
                    set = set,
                    index = index,
                    isCardio = definition.isCardio,
                    onToggle = { onToggleSet(set.id) },
                    onUpdate = { transform -> onUpdateSet(set.id, transform) },
                    onRemove = { onRemoveSet(set.id) },
                )
            }
        }
        AddSetButton(onClick = onAddSet)
    }
}

@Composable
private fun EmptyExercises() {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_fitness_center), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.edit_workout_empty_exercises), color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Sélecteur plein écran de l'éditeur (§ 2.9) : recherche et puces de groupes comme au § 2.7, sur
 * les exercices non archivés absents de la séance ; toucher l'ajoute et ferme l'écran.
 */
@Composable
private fun EditExercisePicker(
    definitions: List<ExerciseDefinition>,
    onPick: (ExerciseDefinition) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    var search by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    val filtered = filterExercises(definitions, search, group)
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Absorbe les touchers : rien ne doit atteindre l'éditeur en dessous.
            .clickable(interactionSource = null, indication = null, onClick = {})
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_back), tint = colors.foreground, modifier = Modifier.size(22.dp))
                }
                Text(stringResource(R.string.workout_add_exercise), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            ExerciseSearchBar(
                search = search,
                onSearchChange = { search = it },
                group = group,
                onGroupChange = { group = it },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { definition ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(definition) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exerciseName(definition), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        MuscleGroupBadge(definition.muscleGroup, small = true)
                    }
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(thickness = 1.dp, color = colors.border)
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.edit_workout_no_exercise_found),
                        color = colors.mutedForeground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                    )
                }
            }
            item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * « Renommer la séance » (🔄 D15) : mêmes puces et champ perso qu'au § 2.3. Un préréglage fixe
 * `preset` et `name` = son libellé dans la langue courante ; un nom perso fixe `name` et efface
 * `preset`.
 */
@Composable
private fun RenameDialog(
    preset: PresetId?,
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (preset: PresetId?, name: String) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    var chosenPreset by rememberSaveable { mutableStateOf(preset) }
    var customSelected by rememberSaveable { mutableStateOf(preset == null && name.isNotBlank()) }
    var customName by rememberSaveable { mutableStateOf(if (preset == null) name.take(CUSTOM_NAME_MAX_LENGTH) else "") }
    val presetLabel = chosenPreset?.let { stringResource(it.labelRes) }
    val canConfirm = presetLabel != null || (customSelected && customName.isNotBlank())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.edit_workout_rename), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            WorkoutNameFields(
                preset = chosenPreset,
                customSelected = customSelected,
                customName = customName,
                onSelectPreset = {
                    chosenPreset = it
                    customSelected = false
                    customName = ""
                },
                onSelectCustom = {
                    chosenPreset = null
                    customSelected = true
                },
                onCustomNameChange = { customName = it },
                chipBackground = colors.input,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val buttonShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .border(1.dp, colors.border, buttonShape)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .background(if (canConfirm) colors.primary else colors.muted)
                        .clickable(enabled = canConfirm) {
                            if (presetLabel != null) onConfirm(chosenPreset, presetLabel) else onConfirm(null, customName.trim())
                        }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val content = if (canConfirm) colors.primaryForeground else colors.mutedForeground
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.common_save), color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
