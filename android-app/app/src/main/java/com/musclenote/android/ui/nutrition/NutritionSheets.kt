package com.musclenote.android.ui.nutrition

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclenote.android.R
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.nutrition.EntryMode
import com.musclenote.android.data.nutrition.NutritionEntry
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.nutrition.ProteinRules
import com.musclenote.android.data.nutrition.StockRules
import com.musclenote.android.data.nutrition.StockStatus
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInteger
import com.musclenote.android.data.workout.EntryFormat
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.components.MINUS_SIGN
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
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
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.highlightedHelp
import com.musclenote.android.ui.designsystem.components.statusHelp
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.theme.Studio

private const val NAME_MAX_LENGTH = 40
private const val DOSAGE_MAX_LENGTH = 10

/** Premier rappel d'un complément, en minutes depuis minuit (08:00). */
private const val DEFAULT_TIME = NutritionRules.DEFAULT_REMINDER_HOUR * 60

/** Rappels du formulaire, en minutes depuis minuit, conservés à la recréation de l'activité. */
private val TimesSaver = listSaver<MutableState<List<Int>>, Int>(
    save = { it.value },
    restore = { mutableStateOf(it) },
)

// ─── Calories, eau et protéines (§ 15.10.2, § 15.16.3) ───────────────────────────────────

/** Mesures du suivi du jour (§ 15.16.2) : calories, eau, protéines. */
enum class NutritionMeasure { Calories, Water, Protein }

/**
 * Ce qu'une sheet de mesure montre (§ 15.16.3) : la valeur du jour [current], l'objectif du jour [goal]
 * (`null` : protéines sans objectif), la moyenne sur 7 jours [average] ; pour les protéines, le réglage
 * [proteinGoal], la dernière pesée connue [latestWeight] et le facteur [proteinPerKg] quand l'objectif
 * vient du poids.
 */
data class MeasureDay(
    val measure: NutritionMeasure,
    val current: Int,
    val goal: Int?,
    val average: Int?,
    val proteinGoal: ProteinGoal = ProteinGoal.DEFAULT,
    val latestWeight: Double? = null,
    val proteinPerKg: Double? = null,
)

/** Étape d'une sheet de mesure : le détail, la saisie, l'objectif. */
enum class MeasureStep { Detail, Amount, Goal }

/**
 * Sheet d'une mesure (SPEC § 15.10.2, § 15.16.3) : la saisie au pavé (bouton +), ou le détail (toucher
 * la ligne : aujourd'hui → « Total du jour », objectif, moyenne sur 7 jours, « Ajouter »). [onSave]
 * reçoit le mode et la valeur ; l'objectif des calories et de l'eau passe par [onSaveGoal] (kcal ou ml),
 * celui des protéines par [onSaveProteinGoal].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureSheet(
    measure: NutritionMeasure,
    day: MeasureDay,
    start: MeasureSheetStart,
    onDismiss: () -> Unit,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onSaveGoal: (Int) -> Unit,
    onSaveProteinGoal: (ProteinGoal) -> Unit,
) {
    check(day.measure == measure)
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        MeasureForm(day, start, onSave, onSaveGoal, onSaveProteinGoal, onClose = onDismiss, bottomInset = inset)
    }
}

/**
 * Contenu de [MeasureSheet] : détail, saisie ou objectif. Retour : de l'objectif à l'étape d'où il a été
 * ouvert, de la saisie au détail quand la sheet s'est ouverte sur lui. Une saisie enregistrée ferme la
 * sheet (par [onSave]) ; un objectif enregistré revient à l'étape d'avant.
 */
@Composable
internal fun MeasureForm(
    day: MeasureDay,
    start: MeasureSheetStart,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onSaveGoal: (Int) -> Unit,
    onSaveProteinGoal: (ProteinGoal) -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp,
    initialStep: MeasureStep? = null,
    initialText: String = "",
) {
    var step by rememberSaveable { mutableStateOf(initialStep ?: if (start == MeasureSheetStart.Detail) MeasureStep.Detail else MeasureStep.Amount) }
    var amountMode by rememberSaveable { mutableStateOf(EntryMode.Add) }
    var goalReturn by rememberSaveable { mutableStateOf(if (start == MeasureSheetStart.Detail) MeasureStep.Detail else MeasureStep.Amount) }
    BackHandler(enabled = step == MeasureStep.Goal || (step == MeasureStep.Amount && start == MeasureSheetStart.Detail)) {
        step = if (step == MeasureStep.Goal) goalReturn else MeasureStep.Detail
    }
    when (step) {
        MeasureStep.Detail -> MeasureDetail(
            day,
            onTotal = { amountMode = EntryMode.Total; step = MeasureStep.Amount },
            onGoal = { goalReturn = MeasureStep.Detail; step = MeasureStep.Goal },
            onAdd = { amountMode = EntryMode.Add; step = MeasureStep.Amount },
            onClose = onClose,
            bottomInset = bottomInset,
        )
        MeasureStep.Amount -> AmountForm(
            day,
            initialMode = amountMode,
            onSave = onSave,
            onEditGoal = { goalReturn = MeasureStep.Amount; step = MeasureStep.Goal },
            bottomInset = bottomInset,
            initialText = initialText,
        )
        MeasureStep.Goal ->
            if (day.measure == NutritionMeasure.Protein) {
                ProteinGoalForm(day, onSave = { onSaveProteinGoal(it); step = goalReturn }, bottomInset = bottomInset)
            } else {
                GoalForm(day, onSave = { onSaveGoal(it); step = goalReturn }, bottomInset = bottomInset)
            }
    }
}

@Composable
private fun measureTitle(measure: NutritionMeasure): String = stringResource(
    when (measure) {
        NutritionMeasure.Calories -> R.string.progress_tab_calories
        NutritionMeasure.Water -> R.string.progress_tab_water
        NutritionMeasure.Protein -> R.string.nutrition_protein
    },
)

/** Quantité d'une mesure : « 1 850 kcal », « 750 ml » / « 1,5 L », « 112 g ». */
@Composable
private fun measureAmount(measure: NutritionMeasure, value: Int): String = when (measure) {
    NutritionMeasure.Calories -> stringResource(R.string.common_kcal_value, formatInteger(value.toDouble()))
    NutritionMeasure.Water -> waterText(value)
    NutritionMeasure.Protein -> gramsText(value)
}

/** Objectif du jour d'une mesure (« 2 800 kcal », « 2,5 L », « 150 g ») ; « À définir » sans objectif. */
@Composable
private fun measureGoal(day: MeasureDay): String {
    val goal = day.goal ?: return stringResource(R.string.nutrition_goal_undefined)
    return if (day.measure == NutritionMeasure.Water) waterGoalText(goal) else measureAmount(day.measure, goal)
}

/** « Aujourd'hui : 1,5 L sur 2,5 L. », sans objectif « Aujourd'hui : 112 g. ». */
@Composable
private fun measureHelp(day: MeasureDay): String =
    if (day.goal == null) stringResource(R.string.nutrition_entry_help_no_goal, measureAmount(day.measure, day.current))
    else stringResource(R.string.nutrition_entry_help, measureAmount(day.measure, day.current), measureGoal(day))

/**
 * Détail de l'eau ou des protéines (§ 15.16.3) : lignes d'information « Aujourd'hui » (→ « Total du
 * jour »), « Objectif quotidien » (→ objectif ; « 125 g · 1,6 g/kg » quand il vient du poids) et
 * « Moyenne sur 7 jours » ; « Ajouter » et « Fermer ».
 */
@Composable
private fun MeasureDetail(
    day: MeasureDay,
    onTotal: () -> Unit,
    onGoal: () -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp,
) {
    val perKg = day.proteinPerKg
    val goal = day.goal
    val goalText = if (goal != null && perKg != null) {
        stringResource(R.string.nutrition_protein_goal_per_kg_value, gramsText(goal), formatNumber(perKg))
    } else {
        measureGoal(day)
    }
    SheetSurface(Modifier.verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(measureTitle(day.measure), message = AnnotatedString(measureHelp(day)), messageStyle = Studio.typography.body)
        Column(Modifier.fillMaxWidth()) {
            InfoRow(stringResource(R.string.nutrition_today), measureAmount(day.measure, day.current), onClick = onTotal)
            InfoRow(stringResource(R.string.progress_recap_daily_goal), goalText, onClick = onGoal)
            InfoRow(
                stringResource(R.string.nutrition_average_title),
                day.average?.let { measureAmount(day.measure, it) } ?: NO_VALUE,
                showDivider = false,
            )
        }
        StudioButton(stringResource(R.string.common_add), onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ds_ic_add)
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/** Quantité du jour : kcal, ml ou g (§ 15.10.2, § 15.16.3), en « Ajouter » ou « Total du jour ». */
@Composable
private fun AmountForm(
    day: MeasureDay,
    initialMode: EntryMode,
    onSave: (mode: EntryMode, value: Int) -> Unit,
    onEditGoal: () -> Unit,
    bottomInset: Dp,
    initialText: String,
) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val measure = day.measure
    val current = day.current
    val entry = when (measure) {
        NutritionMeasure.Calories -> NutritionEntry.Calories
        NutritionMeasure.Water -> NutritionEntry.Water
        NutritionMeasure.Protein -> NutritionEntry.Protein
    }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var text by rememberSaveable { mutableStateOf(if (initialMode == EntryMode.Total) current.toString() else initialText) }
    var fresh by rememberSaveable { mutableStateOf(initialMode == EntryMode.Total || initialText.isEmpty()) }
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

    FormSheetTemplate(
        title = measureTitle(measure),
        help = if (invalid) statusHelp(stringResource(R.string.nutrition_entry_invalid)) else AnnotatedString(measureHelp(day)),
        bottomInset = bottomInset,
        field = {
            FormHeroField(
                text,
                unit = when (measure) {
                    NutritionMeasure.Calories -> "kcal"
                    NutritionMeasure.Water -> "ml"
                    NutritionMeasure.Protein -> "g"
                },
            )
        },
        quickEntries = {
            FilterChip(stringResource(R.string.nutrition_mode_add), selected = mode == EntryMode.Add, onClick = { switchTo(EntryMode.Add) })
            FilterChip(stringResource(R.string.nutrition_mode_total), selected = mode == EntryMode.Total, onClick = { switchTo(EntryMode.Total) })
        },
        info = {
            if (mode == EntryMode.Add) {
                InfoRow(
                    label = stringResource(R.string.nutrition_new_total),
                    value = measureAmount(measure, NutritionRules.newTotal(current, EntryMode.Add, parseInteger(text))),
                )
            }
            InfoRow(
                label = stringResource(R.string.progress_recap_daily_goal),
                value = measureGoal(day),
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

/** Objectif quotidien des calories ou de l'eau, à partir d'aujourd'hui (§ 3.12) : kcal, ou litres stockés en ml. */
@Composable
private fun GoalForm(day: MeasureDay, onSave: (Int) -> Unit, bottomInset: Dp) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val calories = day.measure == NutritionMeasure.Calories
    val goal = day.goal ?: 0
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

/**
 * Objectif de protéines (§ 15.16.3) : puces « Par kg » / « Valeur fixe » quand un poids est connu
 * (sinon la valeur fixe seule) ; facteur de 1,2 à 2,2 (pas ±0,1 bornés) et l'objectif qu'il donne, ou
 * grammes > 0 (pas ±10). Changer de puce garde les deux saisies ; seul « Enregistrer » change le mode.
 */
@Composable
private fun ProteinGoalForm(day: MeasureDay, onSave: (ProteinGoal) -> Unit, bottomInset: Dp) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val goal = day.proteinGoal
    val weight = day.latestWeight
    val available = ProteinRules.perKgAvailable(weight)
    var mode by rememberSaveable { mutableStateOf(ProteinRules.initialMode(goal, weight)) }
    var perKgText by rememberSaveable { mutableStateOf(SetEntryRules.format(goal.perKg, NutritionEntry.ProteinPerKg, locale)) }
    var fixedText by rememberSaveable { mutableStateOf(ProteinRules.fixedPrefill(goal, weight)?.toString().orEmpty()) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val perKg = mode == ProteinGoalMode.PerKg && available
    val text = if (perKg) perKgText else fixedText
    val entry = if (perKg) NutritionEntry.ProteinPerKg else NutritionEntry.ProteinFixed

    fun edit(next: String?, nextFresh: Boolean = false) {
        if (next == null) return
        if (perKg) perKgText = next else fixedText = next
        fresh = nextFresh
        invalid = false
    }

    fun switchTo(next: ProteinGoalMode) {
        if (next == mode) return
        mode = next
        fresh = true
        invalid = false
    }

    fun save() {
        val next = if (perKg) ProteinRules.perKgValue(text)?.let { ProteinRules.withPerKg(goal, it) }
        else ProteinRules.fixedValue(text)?.let { ProteinRules.withFixed(goal, it) }
        if (next == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(next)
        }
    }

    val help = when {
        invalid -> statusHelp(stringResource(if (perKg) R.string.nutrition_protein_per_kg_invalid else R.string.nutrition_goal_invalid))
        perKg -> AnnotatedString(stringResource(R.string.nutrition_protein_per_kg_help, formatNumber(weight!!)))
        available -> AnnotatedString(stringResource(R.string.nutrition_protein_fixed_help))
        else -> AnnotatedString(stringResource(R.string.nutrition_protein_no_weight_help))
    }
    FormSheetTemplate(
        title = stringResource(R.string.nutrition_protein_goal_title),
        help = help,
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = if (perKg) "g/kg" else "g") },
        quickEntries = if (available) {
            {
                FilterChip(stringResource(R.string.nutrition_protein_mode_per_kg), selected = perKg, onClick = { switchTo(ProteinGoalMode.PerKg) })
                FilterChip(stringResource(R.string.nutrition_protein_mode_fixed), selected = !perKg, onClick = { switchTo(ProteinGoalMode.Fixed) })
            }
        } else {
            null
        },
        info = if (perKg) {
            {
                InfoRow(
                    label = stringResource(R.string.nutrition_protein_goal_label),
                    value = ProteinRules.perKgValue(text)?.let { gramsText(ProteinRules.goalFromWeight(weight!!, it)) } ?: NO_VALUE,
                    showDivider = false,
                )
            }
        } else {
            null
        },
    ) {
        EntryKeypad(
            text = text,
            fresh = fresh,
            entry = entry,
            actionLabel = stringResource(R.string.common_save),
            onEdit = ::edit,
            onStep = { value -> haptics.light(); edit(SetEntryRules.format(value, entry, locale), nextFresh = true) },
            onAction = ::save,
            stepper = if (perKg) ({ value, direction -> ProteinRules.stepPerKg(value, direction) }) else null,
        )
    }
}

/**
 * Pavé d'une quantité (§ 15.10.5) : touches, pas de [entry] (« −50 » / « +50 »), touche principale.
 * [stepper] remplace le pas du champ (facteur des protéines, borné à 1,2 – 2,2).
 */
@Composable
private fun EntryKeypad(
    text: String,
    fresh: Boolean,
    entry: EntryFormat,
    actionLabel: String,
    onEdit: (next: String?, nextFresh: Boolean) -> Unit,
    onStep: (Double) -> Unit,
    onAction: () -> Unit,
    stepper: ((value: Double, direction: Int) -> Double?)? = null,
) {
    val locale = appLocale()
    val separator = SetEntryRules.decimalSeparator(locale)
    val stepLabel = formatDecimal(entry.step, locale)
    val step = stepper ?: { value, direction -> SetEntryRules.step(value, direction, entry, locale) }
    NumericKeypad(
        onDigit = { key -> onEdit(SetEntryRules.type(text, fresh, key, entry, locale), false) },
        onDelete = { onEdit(SetEntryRules.backspace(text, fresh), false) },
        stepDownLabel = "$MINUS_SIGN$stepLabel",
        onStepDown = { step(parseDecimal(text), -1)?.let(onStep) },
        stepUpLabel = "+$stepLabel",
        onStepUp = { step(parseDecimal(text), +1)?.let(onStep) },
        actionLabel = actionLabel,
        onAction = onAction,
        actionIcon = R.drawable.ds_ic_check,
        decimalSeparator = separator,
    )
}

/**
 * Formulaire d'un nombre entier au pavé (stock, doses rachetées, seuil, doses par jour, § 15.16.5) :
 * champ héros prérempli neuf, [info] sous le champ (reçoit la saisie), « ✓ [actionLabel] » : [validate]
 * la lit, `null` → [invalidMessage] et vibration d'erreur, sinon [onSave].
 */
@Composable
private fun CountForm(
    title: String,
    help: AnnotatedString,
    unit: String,
    initialText: String,
    entry: NutritionEntry,
    validate: (String) -> Int?,
    invalidMessage: String,
    actionLabel: String,
    onSave: (Int) -> Unit,
    bottomInset: Dp,
    info: (@Composable ColumnScope.(text: String) -> Unit)? = null,
) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun edit(next: String?, nextFresh: Boolean = false) {
        if (next == null) return
        text = next
        fresh = nextFresh
        invalid = false
    }

    fun save() {
        val value = validate(text)
        if (value == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(value)
        }
    }

    FormSheetTemplate(
        title = title,
        help = if (invalid) statusHelp(invalidMessage) else help,
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = unit) },
        info = info?.let { content -> { content(text) } },
    ) {
        EntryKeypad(
            text = text,
            fresh = fresh,
            entry = entry,
            actionLabel = actionLabel,
            onEdit = ::edit,
            onStep = { value -> haptics.light(); edit(SetEntryRules.format(value, entry, locale), nextFresh = true) },
            onAction = ::save,
        )
    }
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

/** Actions de la sheet d'un complément (§ 15.10.2, § 15.16.4) : chacune s'applique tout de suite. */
class SupplementActions(
    val onRemindersEnabled: (Boolean) -> Unit,
    val onAddReminder: () -> Unit,
    val onUpdateReminder: (reminderId: String, hour: Int, minute: Int) -> Unit,
    val onRemoveReminder: (reminderId: String) -> Unit,
    val onSetDosesPerDay: (Int) -> Unit,
    val onSetStock: (Int) -> Unit,
    val onRestock: (Int) -> Unit,
    val onClearStock: () -> Unit,
    val onSetLowStockDays: (Int) -> Unit,
    val onDelete: () -> Unit,
) {
    companion object {
        /** Aucune action (aperçus). */
        val None = SupplementActions({}, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {})
    }
}

/** Étape de la sheet d'un complément : la sheet elle-même, ou l'un des formulaires du stock (§ 15.16.5). */
enum class SupplementStep { Main, DosesPerDay, Stock, Restock, LowStock }

/**
 * Sheet d'un complément (§ 15.10.2, § 15.16.4) : rappels (activés ou non, chacun modifiable, ajout),
 * doses par jour et stock (saisie, « Racheté », seuil du stock bas), « Supprimer le complément »
 * (confirmé par l'écran) et « Fermer ». Chaque changement s'applique tout de suite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementSheet(supplement: Supplement, onDismiss: () -> Unit, actions: SupplementActions) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        SupplementSheetContent(supplement, actions, onClose = onDismiss, bottomInset = inset)
    }
}

/**
 * Contenu de [SupplementSheet] : la sheet, ou un formulaire du stock (retour : l'étape d'où il a été
 * ouvert ; enregistré : idem). [initialStep] : aperçus.
 */
@Composable
internal fun SupplementSheetContent(
    supplement: Supplement,
    actions: SupplementActions,
    onClose: () -> Unit,
    bottomInset: Dp,
    initialStep: SupplementStep = SupplementStep.Main,
) {
    var step by rememberSaveable { mutableStateOf(initialStep) }
    var lowReturn by rememberSaveable { mutableStateOf(if (initialStep == SupplementStep.LowStock) SupplementStep.Main else SupplementStep.Main) }
    BackHandler(enabled = step != SupplementStep.Main) {
        step = if (step == SupplementStep.LowStock) lowReturn else SupplementStep.Main
    }
    val status = StockRules.status(supplement)
    val unitDoses = stringResource(R.string.nutrition_doses_unit)
    when (step) {
        SupplementStep.Main -> SupplementMain(
            supplement,
            status,
            actions,
            onStep = { next ->
                if (next == SupplementStep.LowStock) lowReturn = SupplementStep.Main
                step = next
            },
            onClose = onClose,
            bottomInset = bottomInset,
        )
        SupplementStep.DosesPerDay -> CountForm(
            title = stringResource(R.string.nutrition_doses_per_day),
            help = AnnotatedString(stringResource(R.string.nutrition_doses_per_day_help)),
            unit = unitDoses,
            initialText = supplement.dosesPerDay.toString(),
            entry = NutritionEntry.SmallCount,
            validate = StockRules::dosesPerDayValue,
            invalidMessage = stringResource(R.string.nutrition_doses_per_day_invalid),
            actionLabel = stringResource(R.string.common_save),
            onSave = { actions.onSetDosesPerDay(it); step = SupplementStep.Main },
            bottomInset = bottomInset,
        )
        SupplementStep.Stock -> CountForm(
            title = stringResource(R.string.nutrition_stock),
            help = AnnotatedString(stringResource(R.string.nutrition_stock_help)),
            unit = unitDoses,
            initialText = status?.doses?.toString().orEmpty(),
            entry = NutritionEntry.Stock,
            validate = StockRules::stockValue,
            invalidMessage = stringResource(R.string.nutrition_entry_invalid),
            actionLabel = stringResource(R.string.common_save),
            onSave = { actions.onSetStock(it); step = SupplementStep.Main },
            bottomInset = bottomInset,
        ) { text ->
            val days = if (text.isEmpty()) null else StockRules.daysLeft(parseInteger(text), supplement.dosesPerDay)
            InfoRow(
                label = stringResource(R.string.nutrition_stock_days_left),
                value = days?.let { daysText(it) } ?: NO_VALUE,
                showDivider = false,
            )
            if (status != null) {
                StudioButton(
                    stringResource(R.string.nutrition_stock_stop),
                    onClick = { actions.onClearStock(); step = SupplementStep.Main },
                    variant = ButtonVariant.Text,
                    size = ButtonSize.Medium,
                    tone = ButtonTone.Subtle,
                )
            }
        }
        SupplementStep.Restock -> {
            val remaining = status?.doses ?: 0
            CountForm(
                title = stringResource(R.string.nutrition_restock_title),
                help = restockHelp(supplement, status),
                unit = stringResource(R.string.nutrition_restock_unit),
                initialText = "",
                entry = NutritionEntry.Stock,
                validate = StockRules::restockValue,
                invalidMessage = stringResource(R.string.nutrition_entry_invalid),
                actionLabel = stringResource(R.string.common_add),
                onSave = { actions.onRestock(it); step = SupplementStep.Main },
                bottomInset = bottomInset,
            ) { text ->
                val total = remaining + parseInteger(text).coerceAtLeast(0)
                InfoRow(
                    label = stringResource(R.string.nutrition_new_stock),
                    value = stockText(total, StockRules.daysLeft(total, supplement.dosesPerDay)),
                )
                InfoRow(
                    label = stringResource(R.string.nutrition_stock_low),
                    value = lowStockText(supplement.lowStockDays),
                    onClick = { lowReturn = SupplementStep.Restock; step = SupplementStep.LowStock },
                    showDivider = false,
                )
            }
        }
        SupplementStep.LowStock -> CountForm(
            title = stringResource(R.string.nutrition_stock_low),
            help = AnnotatedString(stringResource(R.string.nutrition_stock_low_help)),
            unit = stringResource(R.string.nutrition_days_unit),
            initialText = supplement.lowStockDays.toString(),
            entry = NutritionEntry.SmallCount,
            validate = StockRules::lowStockDaysValue,
            invalidMessage = stringResource(R.string.nutrition_stock_low_invalid),
            actionLabel = stringResource(R.string.common_save),
            onSave = { actions.onSetLowStockDays(it); step = lowReturn },
            bottomInset = bottomInset,
        )
    }
}

/** La sheet du complément elle-même : rappels, doses par jour, stock, suppression. */
@Composable
private fun SupplementMain(
    supplement: Supplement,
    status: StockStatus?,
    actions: SupplementActions,
    onStep: (SupplementStep) -> Unit,
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
            onEnabled = actions.onRemindersEnabled,
            onPick = { index -> pickingId = reminders.getOrNull(index)?.id },
            onAdd = actions.onAddReminder,
        )
        // Doses par jour et stock (§ 15.16.4, 🔄 D42).
        Column(Modifier.fillMaxWidth()) {
            InfoRow(
                label = stringResource(R.string.nutrition_doses_per_day),
                value = formatInteger(supplement.dosesPerDay.toDouble()),
                onClick = { onStep(SupplementStep.DosesPerDay) },
            )
            InfoRow(
                label = stringResource(R.string.nutrition_stock),
                value = status?.let { stockText(it.doses, it.daysLeft) } ?: stringResource(R.string.nutrition_stock_untracked),
                valueColor = if (status?.low == true) Studio.colors.down else Color.Unspecified,
                onClick = { onStep(SupplementStep.Stock) },
                showDivider = status != null,
            )
            if (status != null) {
                InfoRow(
                    label = stringResource(R.string.nutrition_stock_low),
                    value = lowStockText(supplement.lowStockDays),
                    onClick = { onStep(SupplementStep.LowStock) },
                    showDivider = false,
                )
                StudioButton(
                    stringResource(R.string.nutrition_restocked),
                    { onStep(SupplementStep.Restock) },
                    Modifier.padding(top = Studio.spacing.xxs),
                    variant = ButtonVariant.Text,
                    size = ButtonSize.Medium,
                    icon = R.drawable.ds_ic_add,
                )
            }
        }
        StudioButton(
            stringResource(R.string.nutrition_delete_supplement),
            actions.onDelete,
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
                actions.onUpdateReminder(reminder.id, hour, minute)
            },
            onRemove = if (reminders.size > 1) ({
                pickingId = null
                actions.onRemoveReminder(reminder.id)
            }) else null,
        )
    }
}

/** « 12 jours ». */
@Composable
private fun daysText(days: Int): String = pluralStringResource(R.plurals.nutrition_stock_days, days, days)

/** « 24 doses · 12 jours », ou « 24 doses » sans jours (doses par jour invalides). */
@Composable
private fun stockText(doses: Int, daysLeft: Int?): String {
    val dosesText = pluralStringResource(R.plurals.journal_day_doses, doses, doses)
    return if (daysLeft == null) dosesText else stringResource(R.string.nutrition_stock_value, dosesText, daysText(daysLeft))
}

/** Seuil du stock bas : « à 7 jours ». */
@Composable
private fun lowStockText(days: Int): String = stringResource(R.string.nutrition_stock_low_value, daysText(days))

/** « Créatine · il reste 8 doses, soit 4 jours. », les jours en `down` quand le stock est bas. */
@Composable
private fun restockHelp(supplement: Supplement, status: StockStatus?): AnnotatedString {
    val doses = status?.doses ?: 0
    val dosesText = pluralStringResource(R.plurals.journal_day_doses, doses, doses)
    val daysLeft = status?.daysLeft ?: return AnnotatedString(stringResource(R.string.nutrition_restock_help_doses, supplement.name, dosesText))
    val days = daysText(daysLeft)
    val message = stringResource(R.string.nutrition_restock_help, supplement.name, dosesText, days)
    return if (status.low) highlightedHelp(message, days, Trend.Down) else AnnotatedString(message)
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
