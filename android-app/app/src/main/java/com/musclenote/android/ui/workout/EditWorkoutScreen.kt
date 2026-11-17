package com.musclenote.android.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.newId
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.AddSetRow
import com.musclenote.android.ui.designsystem.components.BottomActionBar
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.ExerciseBlockHeader
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SetField
import com.musclenote.android.ui.designsystem.components.SetRow
import com.musclenote.android.ui.designsystem.components.SetRowState
import com.musclenote.android.ui.designsystem.components.SetTableHeader
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.statusHelp
import com.musclenote.android.ui.designsystem.templates.FicheLazyTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.systemScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.exercise.spokenDuration
import kotlinx.serialization.Serializable

/** Durée absente d'une ancienne série en reps (§ 15.18.1). */
private const val NO_DURATION = "—"

/**
 * Copie de travail de l'éditeur (§ 2.9) : rien n'est enregistré avant « Enregistrer ». Les
 * exercices à la définition introuvable y restent (masqués, § 10.5), donc conservés.
 */
@Serializable
private data class EditDraft(
    val exercises: List<WorkoutExercise>,
    val bodyWeight: Double,
    val name: String,
    val preset: PresetId?,
) {
    fun mapExercise(exerciseId: String, transform: (WorkoutExercise) -> WorkoutExercise) =
        copy(exercises = exercises.map { if (it.exerciseId == exerciseId) transform(it) else it })

    fun mapSets(exerciseId: String, transform: (List<WorkoutSet>) -> List<WorkoutSet>) =
        mapExercise(exerciseId) { it.copy(sets = transform(it.sets)) }

    fun mapSet(exerciseId: String, setId: String, transform: (WorkoutSet) -> WorkoutSet) =
        mapSets(exerciseId) { sets -> sets.map { if (it.id == setId) transform(it) else it } }

    companion object {
        fun of(workout: Workout) = EditDraft(workout.exercises, workout.bodyWeight, workout.name, workout.preset)
    }
}

/** La copie de travail survit à la recréation de l'activité (JSON dans l'état sauvegardé). */
private val DraftSaver = Saver<EditDraft?, String>(
    save = { draft -> draft?.let { AppJson.storage.encodeToString(EditDraft.serializer(), it) } },
    restore = { AppJson.storage.decodeFromString(EditDraft.serializer(), it) },
)

/**
 * Éditer une séance terminée (SPEC § 2.9, § 15.11.3), gabarit fiche : en haut le type de séance
 * (→ renommer) et le poids de corps (→ pavé numérique) ; puis un bloc par exercice (⋮ → retirer),
 * ses séries — valeurs au pavé, case de validation, sans repos — et « + Série » ; « Ajouter un
 * exercice » ouvre le sélecteur. En bas, « Enregistrer », ou le pavé pendant une saisie. Sortie avec
 * des modifications non enregistrées → « Abandonner les modifications ? » (🔄 D15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(
    workoutId: String,
    onClose: () -> Unit,
    insets: ScreenInsets = systemScreenInsets(),
    initialEntry: EntryTarget? = null,
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val locale = appLocale()
    val dates = rememberDateFormats(container.time.zone())

    val workout = workouts.firstOrNull { it.id == workoutId }
    val initial = remember(workout?.id) { workout?.let { EditDraft.of(it) } }
    var edited by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<EditDraft?>(null) }
    val draft = edited ?: initial

    if (workout == null || draft == null) {
        FicheLazyTemplate(topBar = { FicheTopBar(onClose) }, titleBlock = {}, insets = insets) {
            block("not-found") {
                EmptyState(
                    icon = R.drawable.ds_ic_fitness_center,
                    title = stringResource(R.string.edit_workout_not_found),
                    message = null,
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onClose,
                )
            }
        }
        return
    }

    // Lu au moment de l'appel : deux touches dans la même image partent de la dernière copie.
    fun update(transform: (EditDraft) -> EditDraft) {
        val base = edited ?: initial ?: return
        edited = transform(base)
    }

    val definitions = remember(customExercises) { exerciseDefinitions(customExercises) }
    val byId = remember(definitions) { definitions.associateBy { it.id } }
    // § 10.5 : un exercice dont la définition est introuvable est masqué (mais conservé).
    val visible = draft.exercises.filter { it.exerciseId in byId }
    fun kind(exerciseId: String) = byId[exerciseId]?.kind ?: SetKind.Strength
    fun text(field: SetEntryField, set: WorkoutSet) = SetEntryRules.entryText(field.read(set), field, locale)

    // Saisie au pavé (§ 15.7.4) ; fermée si sa série disparaît.
    val entry = rememberSaveable(saver = SetEntryState.Saver) {
        SetEntryState().also { state ->
            val set = initialEntry?.let { t -> visible.firstOrNull { it.exerciseId == t.exerciseId }?.sets?.firstOrNull { it.id == t.setId } }
            if (initialEntry != null && set != null) state.open(initialEntry, SetEntryRules.entryText(initialEntry.field.read(set), initialEntry.field, locale))
        }
    }
    val target = entry.target
    val targetExercise = target?.let { t -> visible.firstOrNull { it.exerciseId == t.exerciseId } }
    val targetSet = targetExercise?.sets?.firstOrNull { it.id == target.setId }
    LaunchedEffect(target, targetSet == null) { if (target != null && targetSet == null) entry.close() }

    fun open(exerciseId: String, set: WorkoutSet, field: SetEntryField) = entry.open(EntryTarget(exerciseId, set.id, field), text(field, set))

    // Bottom sheets.
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var weightOpen by rememberSaveable { mutableStateOf(false) }
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var removeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSetOf by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val dirty = edited != null && edited != initial
    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose()
    }
    BackHandler(enabled = targetSet != null || dirty) {
        if (targetSet != null) entry.close() else confirmDiscard = true
    }

    val displayName = workoutDisplayName(workout.copy(name = draft.name, preset = draft.preset))
    val date = parseInstantOrNull(workout.date, container.time.zone())?.let { dates.weekdayDayMonthYear(it) }
    val weightValue = if (draft.bodyWeight > 0) "${formatNumber(draft.bodyWeight)} kg" else NO_VALUE
    val listState = rememberLazyListState()

    FicheLazyTemplate(
        topBar = {
            FicheTopBar(
                onNavigate = ::requestClose,
                navigationIcon = R.drawable.ds_ic_close,
                navigationDescription = stringResource(R.string.common_close),
            )
        },
        titleBlock = { FicheTitle(displayName, overline = stringResource(R.string.edit_workout_header_label), subtitle = date) },
        bottomAction = { inset ->
            val exercise = targetExercise
            val set = targetSet
            if (target != null && exercise != null && set != null) {
                val exerciseId = exercise.exerciseId
                val setKind = kind(exerciseId)
                WorkoutKeypad(
                    set = set,
                    setNumber = exercise.sets.indexOf(set) + 1,
                    kind = setKind,
                    entry = entry,
                    field = target.field,
                    locale = locale,
                    bottomInset = inset,
                    onSelectField = { open(exerciseId, set, it) },
                    onDigit = { key ->
                        val next = SetEntryRules.type(entry.text, entry.fresh, key, target.field, locale) ?: return@WorkoutKeypad
                        entry.typed(next)
                        update { it.mapSet(exerciseId, set.id) { s -> SetEntryRules.apply(s, target.field, next) } }
                    },
                    onBackspace = {
                        val next = SetEntryRules.backspace(entry.text, entry.fresh)
                        entry.typed(next)
                        update { it.mapSet(exerciseId, set.id) { s -> SetEntryRules.apply(s, target.field, next) } }
                    },
                    onStep = { direction ->
                        val value = SetEntryRules.step(target.field.read(set), direction, target.field, locale) ?: return@WorkoutKeypad
                        entry.reset(SetEntryRules.entryText(value, target.field, locale))
                        update { it.mapSet(exerciseId, set.id) { s -> target.field.write(s, value) } }
                    },
                    onAction = {
                        val nextField = SetEntryRules.next(target.field, setKind)
                        when {
                            nextField != null -> open(exerciseId, set, nextField)
                            set.completed -> entry.close()
                            else -> {
                                // Validée dans la copie, sans repos (§ 2.9).
                                haptics.medium()
                                update { it.mapSet(exerciseId, set.id) { s -> s.copy(completed = true) } }
                                val following = SetEntryRules.nextOpenSet(exercise.sets, set.id)
                                if (following != null) open(exerciseId, following, SetEntryRules.fields(setKind).first()) else entry.close()
                            }
                        }
                    },
                    onDelete = {
                        deleteSetOf = exerciseId
                        deleteSetId = set.id
                    },
                    onHide = { entry.close() },
                )
            } else {
                BottomActionBar(
                    stringResource(R.string.common_save),
                    onClick = {
                        val saved = draft
                        container.launchMutation {
                            repository.patchWorkout(workoutId, saved.exercises, saved.bodyWeight, saved.name, saved.preset)
                        }
                        haptics.success()
                        onClose()
                    },
                    icon = R.drawable.ds_ic_check,
                    bottomInset = inset,
                )
            }
        },
        insets = insets,
        listState = listState,
    ) {
        block("session") {
            Column {
                ListRow(
                    title = stringResource(R.string.start_workout_title),
                    trailing = ListRowTrailing.Value(displayName),
                    onClick = { renameOpen = true },
                    onClickLabel = stringResource(R.string.edit_workout_rename),
                )
                ListRow(
                    title = stringResource(R.string.common_body_weight),
                    trailing = ListRowTrailing.Value(weightValue),
                    showDivider = false,
                    onClick = { weightOpen = true },
                )
            }
        }

        for (exercise in visible) {
            val exerciseId = exercise.exerciseId
            val definition = byId.getValue(exerciseId)
            val fields = SetEntryRules.fields(definition.kind)
            val (firstField, secondField) = fields[0] to fields[1]
            section(
                key = exerciseId,
                header = {
                    val name = exerciseName(definition)
                    ExerciseBlockHeader(
                        name,
                        onOpenDetails = null,
                        onMore = { optionsFor = exerciseId },
                        moreDescription = stringResource(R.string.workout_exercise_options_for, name),
                    ) {
                        StudioChip(stringResource(definition.muscleGroup.labelRes))
                    }
                    if (exercise.sets.isNotEmpty()) {
                        Spacer(Modifier.height(Studio.spacing.m))
                        SetTableHeader(stringResource(R.string.workout_col_index), null, fieldLabel(firstField), fieldLabel(secondField))
                    }
                },
            ) {
                itemsIndexed(exercise.sets, key = { _, set -> set.id }) { index, set ->
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
                    fun display(field: SetEntryField) = when {
                        active == field -> SetEntryRules.display(entry.text, field)
                        // Ancienne série en reps d'un exercice devenu au temps (§ 15.18.1).
                        field == SetEntryField.Hold && set.durationSeconds == null -> NO_DURATION
                        else -> SetEntryRules.format(field.read(set), field, locale)
                    }
                    val number = index + 1
                    @Composable
                    fun spoken(field: SetEntryField): String {
                        val seconds = set.durationSeconds
                        return if (field == SetEntryField.Hold && active != field && seconds != null) spokenDuration(seconds) else display(field)
                    }
                    SetRow(
                        index = number.toString(),
                        previous = null,
                        weight = display(firstField),
                        reps = display(secondField),
                        state = if (set.completed) SetRowState.Done else SetRowState.Todo,
                        checkDescription = stringResource(if (set.completed) R.string.workout_set_checked else R.string.workout_set_check, number),
                        onCheck = {
                            haptics.medium()
                            update { it.mapSet(exerciseId, set.id) { s -> s.copy(completed = !s.completed) } }
                        },
                        modifier = Modifier.bringIntoViewRequester(requester),
                        editingField = when (active) {
                            firstField -> SetField.Weight
                            secondField -> SetField.Reps
                            else -> null
                        },
                        onFieldClick = { column -> open(exerciseId, set, if (column == SetField.Weight) firstField else secondField) },
                        showDivider = index != exercise.sets.lastIndex,
                        weightDescription = stringResource(R.string.workout_set_value_description, number, fieldLabel(firstField), spoken(firstField)),
                        repsDescription = stringResource(R.string.workout_set_value_description, number, fieldLabel(secondField), spoken(secondField)),
                    )
                }
                item(key = "add-set:$exerciseId") {
                    AddSetRow(stringResource(R.string.workout_add_set_row), description = stringResource(R.string.workout_add_set_to, exerciseName(definition)), onClick = {
                        haptics.light()
                        // Copie de la dernière série, ou 10 × 20 kg / 30 min ; validée (§ 2.9).
                        update { d ->
                            d.mapSets(exerciseId) { sets -> sets + WorkoutRules.newSet(sets.lastOrNull(), definition.kind, newId(), completed = true) }
                        }
                    })
                }
            }
        }

        block("add") {
            if (visible.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ds_ic_fitness_center,
                    title = stringResource(R.string.workout_empty_title),
                    message = stringResource(R.string.edit_workout_empty_message),
                    actionLabel = stringResource(R.string.workout_add_exercise),
                    onAction = { pickerOpen = true },
                )
            } else {
                StudioButton(
                    stringResource(R.string.workout_add_exercise),
                    onClick = { pickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                    icon = R.drawable.ds_ic_add,
                )
            }
        }
    }

    if (pickerOpen) {
        val available = remember(definitions) { definitions.filterNot { it.archived } }
        ExercisePickerSheet(
            title = stringResource(R.string.workout_add_exercise),
            definitions = available,
            addedIds = draft.exercises.mapTo(HashSet()) { it.exerciseId },
            onAdd = { definition ->
                haptics.medium()
                // Série initiale validée : 10 × 20 kg, 30 min / 0 / 0 / 0 en cardio (🔄 D15), 0:30 au temps (§ 15.18.2).
                val firstSet = WorkoutRules.newSet(null, definition.kind, newId(), completed = true)
                update { d ->
                    if (d.exercises.any { it.exerciseId == definition.id }) d
                    else d.copy(exercises = d.exercises + WorkoutExercise(definition.id, sets = listOf(firstSet)))
                }
            },
            onDismiss = { pickerOpen = false },
            closeOnAdd = true,
        )
    }

    if (renameOpen) {
        val myTypes = rememberMyWorkoutTypes(container)
        StudioBottomSheet(onDismissRequest = { renameOpen = false }) { inset ->
            RenameSheetContent(
                preset = draft.preset,
                name = draft.name,
                myTypes = myTypes,
                onRemoveMyType = { container.removeMyWorkoutType(it) },
                onConfirm = { preset, name ->
                    haptics.light()
                    renameOpen = false
                    // 🔄 D45 : un nom perso qui est celui d'un préréglage y est rattaché (§ 15.19.2).
                    update { it.copy(preset = WorkoutRules.presetFor(preset, name), name = name) }
                },
                bottomInset = inset,
            )
        }
    }

    if (weightOpen) {
        StudioBottomSheet(onDismissRequest = { weightOpen = false }) { inset ->
            EditBodyWeightContent(
                initial = draft.bodyWeight,
                help = displayName,
                onSave = { weight ->
                    haptics.success()
                    weightOpen = false
                    update { it.copy(bodyWeight = weight) }
                },
                bottomInset = inset,
            )
        }
    }

    optionsFor?.let { exerciseId ->
        val definition = byId[exerciseId]
        if (definition == null || visible.none { it.exerciseId == exerciseId }) {
            optionsFor = null
        } else {
            StudioBottomSheet(onDismissRequest = { optionsFor = null }) { inset ->
                ExerciseOptionsSheetContent(
                    name = exerciseName(definition),
                    note = "",
                    onNoteChange = null,
                    onRemove = {
                        optionsFor = null
                        removeFor = exerciseId
                    },
                    onClose = { optionsFor = null },
                    bottomInset = inset,
                    subtitle = stringResource(definition.muscleGroup.labelRes),
                )
            }
        }
    }

    removeFor?.let { exerciseId ->
        val definition = byId[exerciseId]
        ConfirmSheet(
            title = stringResource(R.string.exercise_card_remove_confirm, definition?.let { exerciseName(it) }.orEmpty()),
            message = stringResource(R.string.workout_exercise_remove_message),
            confirmLabel = stringResource(R.string.exercise_card_remove),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.medium()
                removeFor = null
                update { d -> d.copy(exercises = d.exercises.filterNot { it.exerciseId == exerciseId }) }
            },
            onDismiss = { removeFor = null },
        )
    }

    val deleteExercise = deleteSetOf
    val deleteSet = deleteSetId
    if (deleteExercise != null && deleteSet != null) {
        val number = visible.firstOrNull { it.exerciseId == deleteExercise }?.sets?.indexOfFirst { it.id == deleteSet }?.plus(1) ?: 0
        ConfirmSheet(
            title = stringResource(R.string.workout_delete_set_title, number),
            message = stringResource(R.string.workout_delete_set_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.medium()
                deleteSetOf = null
                deleteSetId = null
                entry.close()
                update { d -> d.mapSets(deleteExercise) { sets -> sets.filterNot { it.id == deleteSet } } }
            },
            onDismiss = {
                deleteSetOf = null
                deleteSetId = null
            },
        )
    }

    if (confirmDiscard) {
        ConfirmSheet(
            title = stringResource(R.string.edit_workout_discard_title),
            message = stringResource(R.string.edit_workout_discard_message),
            confirmLabel = stringResource(R.string.edit_workout_discard),
            dismissLabel = stringResource(R.string.edit_workout_keep_editing),
            onConfirm = {
                confirmDiscard = false
                haptics.warning()
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** Valeur absente (poids de corps inconnu). */
private const val NO_VALUE = "—"

/**
 * « Renommer la séance » (🔄 D15) : les mêmes puces et nom perso qu'au démarrage. Un préréglage fixe
 * `preset` et `name` = son libellé dans la langue courante ; un nom perso fixe `name` et efface
 * `preset`. 🔄 D45 (§ 15.19) : « Mes types » ([myTypes]) en tête ; l'appui long sur une de ses puces
 * fait passer la sheet à la confirmation, puis [onRemoveMyType]. [initialRemovingType] ouvre cette
 * confirmation (aperçus).
 */
@Composable
internal fun RenameSheetContent(
    preset: PresetId?,
    name: String,
    myTypes: List<String>,
    onRemoveMyType: (String) -> Unit,
    onConfirm: (preset: PresetId?, name: String) -> Unit,
    bottomInset: Dp,
    initialRemovingType: String? = null,
) {
    var chosenPreset by rememberSaveable { mutableStateOf(preset) }
    var customName by rememberSaveable { mutableStateOf(if (preset == null) name.take(CUSTOM_NAME_MAX_LENGTH) else "") }
    var removingType by rememberSaveable { mutableStateOf(initialRemovingType) }
    val presetLabel = chosenPreset?.let { stringResource(it.labelRes) }
    val finalName = presetLabel ?: customName.trim()
    BackHandler(enabled = removingType != null) { removingType = null }
    val removing = removingType
    if (removing != null) {
        RemoveMyTypeConfirm(
            name = removing,
            onConfirm = {
                onRemoveMyType(removing)
                removingType = null
            },
            onDismiss = { removingType = null },
            bottomInset = bottomInset,
        )
        return
    }
    SheetSurface(Modifier.imePadding().verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(stringResource(R.string.edit_workout_rename), null)
        WorkoutTypeFields(
            preset = chosenPreset,
            customName = customName,
            myTypes = myTypes,
            onSelectPreset = {
                chosenPreset = it
                customName = ""
            },
            onSelectMyType = {
                customName = it
                chosenPreset = null
            },
            onRemoveMyType = { removingType = it },
            onCustomNameChange = {
                customName = it
                if (it.isNotBlank()) chosenPreset = null
            },
        )
        StudioButton(
            stringResource(R.string.common_save),
            onClick = { onConfirm(chosenPreset, finalName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = finalName.isNotEmpty(),
        )
    }
}

/** Poids de corps de la séance au pavé (§ 15.11.3) : « Enregistrer » si le poids est supérieur à 0. */
@Composable
internal fun EditBodyWeightContent(initial: Double, help: String, onSave: (Double) -> Unit, bottomInset: Dp) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf(if (initial > 0) SetEntryRules.format(initial, SetEntryField.Weight, locale) else "") }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    BodyWeightForm(
        title = stringResource(R.string.common_body_weight),
        help = if (invalid) statusHelp(stringResource(R.string.start_workout_weight_invalid)) else AnnotatedString(help),
        text = text,
        fresh = fresh,
        onEdit = { next, nextFresh ->
            text = next
            fresh = nextFresh
            invalid = false
        },
        actionLabel = stringResource(R.string.common_save),
        onAction = {
            val weight = parseDecimal(text)
            if (weight > 0) {
                onSave(weight)
            } else {
                invalid = true
                haptics.warning()
            }
        },
        bottomInset = bottomInset,
    )
}
