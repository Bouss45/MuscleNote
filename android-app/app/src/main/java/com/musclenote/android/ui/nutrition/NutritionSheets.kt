package com.musclenote.android.ui.nutrition

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclenote.android.R
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.nutrition.EntryMode
import com.musclenote.android.data.nutrition.NutritionEntry
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInteger
import com.musclenote.android.data.workout.EntryFormat
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.components.MINUS_SIGN
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.ButtonSize
import com.musclenote.android.ui.designsystem.components.ButtonTone
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.LabeledField
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.components.statusHelp
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.progress.DailyKind

private const val NAME_MAX_LENGTH = 40
private const val DOSAGE_MAX_LENGTH = 10

/** Premier rappel d'un complément, en minutes depuis minuit (08:00). */
private const val DEFAULT_TIME = NutritionRules.DEFAULT_REMINDER_HOUR * 60

/** Rappels du formulaire, en minutes depuis minuit, conservés à la recréation de l'activité. */
private val TimesSaver = listSaver<MutableState<List<Int>>, Int>(
    save = { it.value },
    restore = { mutableStateOf(it) },
)

// ─── Calories et eau (§ 15.10.2) ──────────────────────────────────────────────────────

/**
 * Formulaire Calories ou Eau en bottom sheet (SPEC § 15.10.2) : quantité au pavé, « Ajouter » ou
 * « Total du jour » ([onSave] reçoit le mode et la valeur), et l'objectif quotidien, que la même
 * sheet saisit à son tour ([onSaveGoal], en kcal ou en ml).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyEntrySheet(
    kind: DailyKind,
    current: Int,
    goal: Int,
    onDismiss: () -> Unit,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onSaveGoal: (Int) -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        DailyEntryForm(kind, current, goal, onSave, onSaveGoal, bottomInset = inset)
    }
}

/** Contenu de [DailyEntrySheet] : la saisie, ou l'objectif après « Objectif quotidien » (retour : la saisie). */
@Composable
internal fun DailyEntryForm(
    kind: DailyKind,
    current: Int,
    goal: Int,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onSaveGoal: (Int) -> Unit,
    bottomInset: Dp,
    initialGoal: Boolean = false,
    initialText: String = "",
) {
    var editingGoal by rememberSaveable { mutableStateOf(initialGoal) }
    BackHandler(enabled = editingGoal) { editingGoal = false }
    if (editingGoal) {
        GoalForm(kind, goal, onSave = { onSaveGoal(it); editingGoal = false }, bottomInset = bottomInset)
    } else {
        AmountForm(kind, current, goal, onSave, onEditGoal = { editingGoal = true }, bottomInset = bottomInset, initialText = initialText)
    }
}

/** Quantité du jour : kcal ou ml (§ 15.10.2). */
@Composable
private fun AmountForm(
    kind: DailyKind,
    current: Int,
    goal: Int,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onEditGoal: () -> Unit,
    bottomInset: Dp,
    initialText: String,
) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val calories = kind == DailyKind.Calories
    val entry = if (calories) NutritionEntry.Calories else NutritionEntry.Water
    var mode by rememberSaveable { mutableStateOf(EntryMode.Add) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    var fresh by rememberSaveable { mutableStateOf(initialText.isEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun edit(next: String?, nextFresh: Boolean = false) {
        if (next == null) return
        text = next
        fresh = nextFresh
        invalid = false
    }

    fun switchTo(next: EntryMode) {
        if (next == mode) return
        mode = next
        // « Total du jour » part du total actuel, à corriger ; « Ajouter » part de rien.
        edit(if (next == EntryMode.Total) current.toString() else "", nextFresh = true)
    }

    fun save() {
        val value = NutritionRules.entryValue(mode, text)
        if (value == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(mode, value)
        }
    }

    @Composable
    fun amount(value: Int): String =
        if (calories) stringResource(R.string.common_kcal_value, formatInteger(value.toDouble())) else waterText(value)

    val goalLabel = if (calories) amount(goal) else waterGoalText(goal)
    FormSheetTemplate(
        title = stringResource(if (calories) R.string.progress_tab_calories else R.string.progress_tab_water),
        help = if (invalid) statusHelp(stringResource(R.string.nutrition_entry_invalid))
        else AnnotatedString(stringResource(R.string.nutrition_entry_help, amount(current), goalLabel)),
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = if (calories) "kcal" else "ml") },
        quickEntries = {
            FilterChip(stringResource(R.string.nutrition_mode_add), selected = mode == EntryMode.Add, onClick = { switchTo(EntryMode.Add) })
            FilterChip(stringResource(R.string.nutrition_mode_total), selected = mode == EntryMode.Total, onClick = { switchTo(EntryMode.Total) })
        },
        info = {
            if (mode == EntryMode.Add) {
                InfoRow(
                    label = stringResource(R.string.nutrition_new_total),
                    value = amount(NutritionRules.newTotal(current, EntryMode.Add, parseInteger(text))),
                )
            }
            InfoRow(
                label = stringResource(R.string.progress_recap_daily_goal),
                value = goalLabel,
                onClick = onEditGoal,
                showDivider = false,
            )
        },
    ) {
        EntryKeypad(
            text = text,
            fresh = fresh,
            entry = entry,
            actionLabel = stringResource(if (mode == EntryMode.Add) R.string.common_add else R.string.common_save),
            onEdit = ::edit,
            onStep = { value -> haptics.light(); edit(SetEntryRules.format(value, entry, locale), nextFresh = true) },
            onAction = ::save,
        )
    }
}

/** Objectif quotidien, à partir d'aujourd'hui (§ 3.12) : kcal, ou litres stockés en ml. */
@Composable
private fun GoalForm(kind: DailyKind, goal: Int, onSave: (Int) -> Unit, bottomInset: Dp) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val calories = kind == DailyKind.Calories
    val entry = if (calories) NutritionEntry.CalorieGoal else NutritionEntry.WaterGoal
    var text by rememberSaveable { mutableStateOf(if (calories) goal.toString() else NutritionRules.waterGoalText(goal, locale)) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun edit(next: String?, nextFresh: Boolean = false) {
        if (next == null) return
        text = next
        fresh = nextFresh
        invalid = false
    }

    fun save() {
        val value = if (calories) NutritionRules.calorieGoalValue(text) else NutritionRules.waterGoalValue(text)
        if (value == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(value)
        }
    }

    FormSheetTemplate(
        title = stringResource(if (calories) R.string.nutrition_calorie_goal_title else R.string.nutrition_water_goal_title),
        help = if (invalid) statusHelp(stringResource(R.string.nutrition_goal_invalid))
        else AnnotatedString(stringResource(R.string.nutrition_goal_help)),
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = if (calories) "kcal" else "L") },
    ) {
        EntryKeypad(
            text = text,
            fresh = fresh,
            entry = entry,
            actionLabel = stringResource(R.string.common_save),
            onEdit = ::edit,
            onStep = { value -> haptics.light(); edit(SetEntryRules.format(value, entry, locale), nextFresh = true) },
            onAction = ::save,
        )
    }
}

/** Pavé d'une quantité (§ 15.10.5) : touches, pas de [entry] (« −50 » / « +50 »), touche principale. */
@Composable
private fun EntryKeypad(
    text: String,
    fresh: Boolean,
    entry: EntryFormat,
    actionLabel: String,
    onEdit: (next: String?, nextFresh: Boolean) -> Unit,
    onStep: (Double) -> Unit,
    onAction: () -> Unit,
) {
    val locale = appLocale()
    val separator = SetEntryRules.decimalSeparator(locale)
    val stepLabel = formatDecimal(entry.step, locale)
    NumericKeypad(
        onDigit = { key -> onEdit(SetEntryRules.type(text, fresh, key, entry, locale), false) },
        onDelete = { onEdit(SetEntryRules.backspace(text, fresh), false) },
        stepDownLabel = "$MINUS_SIGN$stepLabel",
        onStepDown = { SetEntryRules.step(parseDecimal(text), -1, entry, locale)?.let(onStep) },
        stepUpLabel = "+$stepLabel",
        onStepUp = { SetEntryRules.step(parseDecimal(text), +1, entry, locale)?.let(onStep) },
        actionLabel = actionLabel,
        onAction = onAction,
        actionIcon = R.drawable.ds_ic_check,
        decimalSeparator = separator,
    )
}

// ─── Compléments (§ 15.10.2) ──────────────────────────────────────────────────────────

/**
 * « Nouveau complément » en bottom sheet (§ 15.10.2) : nom (focus automatique), dosage par prise
 * (texte libre au clavier décimal, gardé tel que saisi), unité, rappels (08:00 au départ) choisis au
 * `TimePicker` (🔄 D7). [onSave] reçoit les rappels `(heure, minute)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSupplementSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, dosage: String, unit: String, remindersEnabled: Boolean, times: List<Pair<Int, Int>>) -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        NewSupplementForm(onSave, bottomInset = inset)
    }
}

/** Contenu de [NewSupplementSheet], avec son état. */
@Composable
internal fun NewSupplementForm(
    onSave: (name: String, dosage: String, unit: String, remindersEnabled: Boolean, times: List<Pair<Int, Int>>) -> Unit,
    bottomInset: Dp,
    initialName: String = "",
    autoFocus: Boolean = true,
) {
    val haptics = rememberHaptics()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dosage by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(NutritionRules.UNITS.first()) }
    var remindersOn by rememberSaveable { mutableStateOf(true) }
    var times by rememberSaveable(saver = TimesSaver) { mutableStateOf(listOf(DEFAULT_TIME)) }
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    SheetSurface(
        Modifier.imePadding().verticalScroll(rememberScrollState()),
        bottomInset = bottomInset,
    ) {
        SheetTitle(stringResource(R.string.nutrition_new_supplement), message = null)
        LabeledField(stringResource(R.string.nutrition_name)) {
            StudioTextField(
                value = name,
                onValueChange = { name = it.take(NAME_MAX_LENGTH) },
                placeholder = stringResource(R.string.nutrition_name_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        LabeledField(stringResource(R.string.nutrition_dosage_per_intake)) {
            StudioTextField(
                value = dosage,
                onValueChange = { dosage = it.take(DOSAGE_MAX_LENGTH) },
                placeholder = stringResource(R.string.nutrition_dosage_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
                for (option in NutritionRules.UNITS) FilterChip(option, selected = option == unit, onClick = { unit = option })
            }
        }
        RemindersEditor(
            enabled = remindersOn,
            times = times.map { it / 60 to it % 60 },
            showTimes = remindersOn,
            onEnabled = { on ->
                haptics.light()
                remindersOn = on
                if (on && times.isEmpty()) times = listOf(DEFAULT_TIME)
            },
            onPick = { picking = it },
            onAdd = {
                haptics.light()
                val (hour, minute) = NutritionRules.nextReminderTime(times.lastOrNull()?.let { it / 60 to it % 60 })
                times = times + (hour * 60 + minute)
            },
        )
        StudioButton(
            stringResource(R.string.common_save),
            onClick = {
                haptics.medium()
                onSave(name, dosage, unit, remindersOn, times.map { it / 60 to it % 60 })
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
    }
    if (autoFocus) LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    val index = picking
    val time = index?.let { times.getOrNull(it) }
    if (index != null && time != null) {
        ReminderTimeDialog(
            hour = time / 60,
            minute = time % 60,
            onDismiss = { picking = null },
            onConfirm = { hour, minute ->
                times = times.mapIndexed { i, t -> if (i == index) hour * 60 + minute else t }
                picking = null
            },
            onRemove = if (times.size > 1) ({
                times = times.filterIndexed { i, _ -> i != index }
                picking = null
            }) else null,
        )
    }
}

/**
 * Sheet d'un complément (§ 15.10.2) : rappels (activés ou non, chacun modifiable, ajout), « Supprimer
 * le complément » (confirmé par l'écran) et « Fermer ». Chaque changement s'applique tout de suite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementSheet(
    supplement: Supplement,
    onDismiss: () -> Unit,
    onRemindersEnabled: (Boolean) -> Unit,
    onAddReminder: () -> Unit,
    onUpdateReminder: (reminderId: String, hour: Int, minute: Int) -> Unit,
    onRemoveReminder: (reminderId: String) -> Unit,
    onDelete: () -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        SupplementSheetContent(supplement, onRemindersEnabled, onAddReminder, onUpdateReminder, onRemoveReminder, onDelete, onClose = onDismiss, bottomInset = inset)
    }
}

/** Contenu de [SupplementSheet]. */
@Composable
internal fun SupplementSheetContent(
    supplement: Supplement,
    onRemindersEnabled: (Boolean) -> Unit,
    onAddReminder: () -> Unit,
    onUpdateReminder: (reminderId: String, hour: Int, minute: Int) -> Unit,
    onRemoveReminder: (reminderId: String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp,
) {
    var pickingId by rememberSaveable { mutableStateOf<String?>(null) }
    val reminders = supplement.reminders
    SheetSurface(Modifier.verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(
            supplement.name,
            message = supplement.dosage.takeIf { it.isNotBlank() }?.let {
                AnnotatedString(stringResource(R.string.nutrition_per_intake, it, supplement.unit))
            },
            messageStyle = Studio.typography.body,
        )
        RemindersEditor(
            enabled = supplement.reminderEnabled,
            times = reminders.map { it.hour to it.minute },
            showTimes = true,
            onEnabled = onRemindersEnabled,
            onPick = { index -> pickingId = reminders.getOrNull(index)?.id },
            onAdd = onAddReminder,
        )
        StudioButton(
            stringResource(R.string.nutrition_delete_supplement),
            onDelete,
            Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            icon = R.drawable.ds_ic_delete,
        )
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }

    reminders.firstOrNull { it.id == pickingId }?.let { reminder ->
        ReminderTimeDialog(
            hour = reminder.hour,
            minute = reminder.minute,
            onDismiss = { pickingId = null },
            onConfirm = { hour, minute ->
                pickingId = null
                onUpdateReminder(reminder.id, hour, minute)
            },
            onRemove = if (reminders.size > 1) ({
                pickingId = null
                onRemoveReminder(reminder.id)
            }) else null,
        )
    }
}

/**
 * Rappels d'un complément : ligne à cocher « Rappels », une ligne d'information par rappel
 * (« Rappel » « 08:00 », toucher → choix de l'heure) si [showTimes], puis « Ajouter un rappel ».
 */
@Composable
private fun RemindersEditor(
    enabled: Boolean,
    times: List<Pair<Int, Int>>,
    showTimes: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPick: (index: Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ListRow(
            title = stringResource(R.string.nutrition_reminders),
            trailing = ListRowTrailing.Check(enabled),
            showDivider = false,
            onClick = { onEnabled(!enabled) },
        )
        if (showTimes) {
            times.forEachIndexed { index, (hour, minute) ->
                InfoRow(
                    label = stringResource(R.string.nutrition_reminder),
                    value = formatReminderTime(hour, minute),
                    onClick = { onPick(index) },
                )
            }
            StudioButton(
                stringResource(R.string.nutrition_add_reminder),
                onAdd,
                Modifier.padding(top = Studio.spacing.xxs),
                variant = ButtonVariant.Text,
                size = ButtonSize.Medium,
                icon = R.drawable.ds_ic_add,
            )
        }
    }
}

/** Libellé (`caption`, texte secondaire) au-dessus d'un champ, comme les champs de « Modifier la date ». */
/**
 * « Heure du rappel » (🔄 D7) : `TimePicker` Material 3 en dialogue, 24 h ou 12 h selon le système,
 * toute minute, aux couleurs du design system (heure ou minute en saisie sur `liveDim`).
 * « Supprimer » ([onRemove]) seulement s'il y a plus d'un rappel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    val colors = Studio.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = Studio.spacing.l)
                .background(colors.surfaceRaised, Studio.shapes.surface)
                .padding(start = Studio.spacing.screen, end = Studio.spacing.screen, top = Studio.spacing.screen, bottom = Studio.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Studio.spacing.m),
        ) {
            Text(stringResource(R.string.nutrition_reminder_time_title), Modifier.fillMaxWidth(), style = Studio.typography.titleSection)
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    containerColor = colors.surfaceRaised,
                    clockDialColor = colors.surfaceControl,
                    clockDialSelectedContentColor = colors.onInverse,
                    clockDialUnselectedContentColor = colors.textPrimary,
                    selectorColor = colors.inverse,
                    periodSelectorBorderColor = colors.surfacePressed,
                    periodSelectorSelectedContainerColor = colors.surfacePressed,
                    periodSelectorUnselectedContainerColor = colors.surfaceRaised,
                    periodSelectorSelectedContentColor = colors.textPrimary,
                    periodSelectorUnselectedContentColor = colors.textSecondary,
                    timeSelectorSelectedContainerColor = colors.liveDim,
                    timeSelectorUnselectedContainerColor = colors.surfaceControl,
                    timeSelectorSelectedContentColor = colors.textPrimary,
                    timeSelectorUnselectedContentColor = colors.textPrimary,
                ),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) {
                    StudioButton(stringResource(R.string.common_delete), onRemove, variant = ButtonVariant.Text, size = ButtonSize.Medium, tone = ButtonTone.Subtle)
                }
                Spacer(Modifier.weight(1f))
                StudioButton(stringResource(R.string.common_cancel), onDismiss, variant = ButtonVariant.Text, size = ButtonSize.Medium, tone = ButtonTone.Subtle)
                StudioButton(
                    stringResource(R.string.common_ok),
                    onClick = { onConfirm(state.hour, state.minute) },
                    variant = ButtonVariant.Text,
                    size = ButtonSize.Medium,
                )
            }
        }
    }
}
