package com.musclenote.android.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.filterExercises
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ConfirmSheetContent
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.LabeledField
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioIconButton
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.theme.Studio

/** Part de l'écran prise par le sélecteur : le voile reste visible au-dessus. */
private const val PICKER_HEIGHT_FRACTION = 0.92f

/** Étapes du sélecteur (§ 15.11.2) : la liste, un formulaire d'exercice perso, ses actions. */
private enum class PickerStage { List, New, Edit, Actions, Delete }

/** Création, modification et suppression des exercices perso (§ 3.9), depuis le sélecteur. */
internal class CustomExerciseActions(
    val create: (name: String, group: MuscleGroup) -> Unit,
    val update: (id: String, name: String, group: MuscleGroup) -> Unit,
    val delete: (id: String) -> Unit,
)

/**
 * Sélecteur d'exercices (SPEC § 15.11.2) : un formulaire en bottom sheet — liste d'options avec
 * recherche (nom affiché, sans casse ni accents) et puces de groupe ; chaque exercice est une ligne à
 * cocher, cochée et inactive s'il est déjà dans la séance ([addedIds]). Toucher une ligne l'ajoute
 * ([onAdd]) ; [closeOnAdd] ferme alors la sheet (éditeur), sinon elle reste ouverte (ajouts
 * multiples). Avec [custom], « + » crée un exercice perso et l'appui long sur un exercice perso non
 * ajouté propose de le modifier ou de le supprimer. [definitions] : sans les exercices archivés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    title: String,
    definitions: List<ExerciseDefinition>,
    addedIds: Set<String>,
    onAdd: (ExerciseDefinition) -> Unit,
    onDismiss: () -> Unit,
    closeOnAdd: Boolean = false,
    custom: CustomExerciseActions? = null,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        ExercisePickerContent(
            title = title,
            definitions = definitions,
            addedIds = addedIds,
            onAdd = { definition ->
                onAdd(definition)
                if (closeOnAdd) onDismiss()
            },
            custom = custom,
            bottomInset = inset,
        )
    }
}

/** Contenu de [ExercisePickerSheet], avec son état. */
@Composable
internal fun ExercisePickerContent(
    title: String,
    definitions: List<ExerciseDefinition>,
    addedIds: Set<String>,
    onAdd: (ExerciseDefinition) -> Unit,
    custom: CustomExerciseActions?,
    bottomInset: Dp,
    initialSearch: String = "",
) {
    val haptics = rememberHaptics()
    var search by rememberSaveable { mutableStateOf(initialSearch) }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var stage by rememberSaveable { mutableStateOf(PickerStage.List) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var formName by rememberSaveable { mutableStateOf("") }
    var formGroup by rememberSaveable { mutableStateOf(MuscleGroup.FALLBACK) }

    val target = targetId?.let { id -> definitions.firstOrNull { it.id == id } }
    // L'exercice visé a disparu (supprimé, ajouté ailleurs) : retour à la liste.
    LaunchedEffect(stage, target == null) {
        if (stage != PickerStage.List && stage != PickerStage.New && target == null) stage = PickerStage.List
    }
    BackHandler(enabled = stage != PickerStage.List) { stage = PickerStage.List }

    when (stage) {
        PickerStage.List -> PickerList(
            title = title,
            definitions = definitions,
            addedIds = addedIds,
            search = search,
            onSearchChange = { search = it },
            group = group,
            onGroupChange = { group = it },
            onAdd = { definition ->
                haptics.light()
                onAdd(definition)
            },
            onNew = custom?.let {
                {
                    formName = ""
                    formGroup = group ?: MuscleGroup.FALLBACK
                    stage = PickerStage.New
                }
            },
            onActions = custom?.let {
                { definition ->
                    haptics.light()
                    targetId = definition.id
                    stage = PickerStage.Actions
                }
            },
            bottomInset = bottomInset,
        )

        PickerStage.New, PickerStage.Edit -> {
            val editing = stage == PickerStage.Edit
            CustomExerciseForm(
                title = stringResource(if (editing) R.string.exercise_picker_edit_exercise else R.string.exercise_picker_new_exercise),
                name = formName,
                onNameChange = { formName = it },
                group = formGroup,
                onGroupChange = {
                    haptics.light()
                    formGroup = it
                },
                submitLabel = stringResource(if (editing) R.string.common_save else R.string.common_add),
                onSubmit = {
                    val actions = custom ?: return@CustomExerciseForm
                    haptics.medium()
                    val id = targetId
                    if (editing && id != null) actions.update(id, formName, formGroup) else actions.create(formName, formGroup)
                    stage = PickerStage.List
                },
                onCancel = { stage = PickerStage.List },
                bottomInset = bottomInset,
            )
        }

        PickerStage.Actions -> if (target != null) {
            SheetSurface(bottomInset = bottomInset) {
                SheetTitle(exerciseName(target), AnnotatedString(stringResource(target.muscleGroup.labelRes)), messageStyle = Studio.typography.body)
                StudioButton(
                    stringResource(R.string.exercise_picker_edit_exercise),
                    onClick = {
                        formName = target.customName.orEmpty()
                        formGroup = target.muscleGroup
                        stage = PickerStage.Edit
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                )
                StudioButton(
                    stringResource(R.string.exercise_picker_delete_exercise),
                    onClick = { stage = PickerStage.Delete },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                )
                StudioButton(stringResource(R.string.common_close), { stage = PickerStage.List }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
            }
        }

        PickerStage.Delete -> if (target != null) {
            ConfirmSheetContent(
                title = stringResource(R.string.exercise_picker_delete_confirm, exerciseName(target)),
                message = stringResource(R.string.exercise_picker_delete_hint),
                confirmLabel = stringResource(R.string.common_delete),
                onConfirm = {
                    haptics.warning()
                    custom?.delete(target.id)
                    targetId = null
                    stage = PickerStage.List
                },
                dismissLabel = stringResource(R.string.common_cancel),
                onDismiss = { stage = PickerStage.Actions },
                bottomInset = bottomInset,
            )
        }
    }
}

/** La liste : titre (et « + »), recherche, puces de groupe, lignes à cocher. */
@Composable
private fun PickerList(
    title: String,
    definitions: List<ExerciseDefinition>,
    addedIds: Set<String>,
    search: String,
    onSearchChange: (String) -> Unit,
    group: MuscleGroup?,
    onGroupChange: (MuscleGroup?) -> Unit,
    onAdd: (ExerciseDefinition) -> Unit,
    onNew: (() -> Unit)?,
    onActions: ((ExerciseDefinition) -> Unit)?,
    bottomInset: Dp,
) {
    val filtered = filterExercises(definitions, search, group)
    SheetSurface(
        Modifier.fillMaxHeight(PICKER_HEIGHT_FRACTION).imePadding(),
        spacing = Studio.spacing.s,
        bottomPadding = Studio.spacing.xxs,
        bottomInset = bottomInset,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = Studio.typography.titleSection)
            // Le bouton icône déborde de 12 : son icône tombe sur la marge (DESIGN.md § 9.2).
            if (onNew != null) {
                StudioIconButton(
                    R.drawable.ds_ic_add,
                    stringResource(R.string.exercise_picker_new_exercise),
                    onNew,
                    Modifier.offset(x = Studio.spacing.s),
                )
            }
        }
        StudioTextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.exercise_picker_search_placeholder),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
        ) {
            FilterChip(stringResource(R.string.common_all), selected = group == null, onClick = { onGroupChange(null) })
            for (candidate in MuscleGroup.entries) {
                // Toucher la puce active la désélectionne (§ 2.7).
                FilterChip(
                    stringResource(candidate.labelRes),
                    selected = group == candidate,
                    onClick = { onGroupChange(if (group == candidate) null else candidate) },
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(filtered, key = { it.id }) { definition ->
                val added = definition.id in addedIds
                ListRow(
                    title = exerciseName(definition),
                    subtitle = stringResource(definition.muscleGroup.labelRes),
                    trailing = ListRowTrailing.Check(added),
                    onClick = if (added) null else ({ onAdd(definition) }),
                    onLongClick = if (onActions != null && definition.isCustom && !added) ({ onActions(definition) }) else null,
                    onLongClickLabel = stringResource(R.string.exercise_picker_actions),
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.edit_workout_no_exercise_found),
                        Modifier.fillMaxWidth().padding(vertical = Studio.spacing.section),
                        style = Studio.typography.body,
                        color = Studio.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** « Nouvel exercice » / « Modifier l'exercice » : nom (focus automatique) et groupe musculaire. */
@Composable
private fun CustomExerciseForm(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    group: MuscleGroup,
    onGroupChange: (MuscleGroup) -> Unit,
    submitLabel: String,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    bottomInset: Dp,
) {
    val focusRequester = remember { FocusRequester() }
    SheetSurface(Modifier.imePadding().verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(title, null)
        LabeledField(stringResource(R.string.exercise_picker_name_label)) {
            StudioTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.exercise_picker_name_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        LabeledField(stringResource(R.string.exercise_picker_group_label)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
            ) {
                for (candidate in MuscleGroup.entries) {
                    FilterChip(stringResource(candidate.labelRes), selected = candidate == group, onClick = { onGroupChange(candidate) })
                }
            }
        }
        StudioButton(submitLabel, onSubmit, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        StudioButton(stringResource(R.string.common_cancel), onCancel, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}
