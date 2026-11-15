package com.musclenote.android.ui.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.nutrition.CalorieTone
import com.musclenote.android.data.nutrition.EntryMode
import com.musclenote.android.data.nutrition.NutritionAverages
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.nutrition.ProteinRules
import com.musclenote.android.data.nutrition.ReminderSummary
import com.musclenote.android.data.nutrition.StockRules
import com.musclenote.android.data.nutrition.StockStatus
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberNotificationPermissionRequest
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.DailySurface
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.ListGroup
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.templates.HubTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.workout.ConfirmSheet

/** Ce que la sheet d'une mesure montre à l'ouverture : la saisie (bouton +) ou le détail (la ligne). */
enum class MeasureSheetStart { Add, Detail }

/**
 * Onglet Nutrition (SPEC § 15.10.1, § 15.16, 🔄 D35, D42), gabarit hub groupé : groupe « Aujourd'hui »
 * (calories du jour et leur bouton +, puis les lignes Eau et Protéines, chacune avec son bouton +),
 * « Compléments » (case « pris aujourd'hui », jours de stock, toucher → sheet), « Moyenne sur 7 jours ».
 * Le jour affiché est le jour courant de l'app (§ 3.12) : il change à minuit et au retour au premier plan.
 */
@Composable
fun NutritionScreen(modifier: Modifier = Modifier, insets: ScreenInsets = tabScreenInsets()) {
    val container = LocalAppContainer.current
    val nutrition = container.nutrition
    val tracker by nutrition.tracker.collectAsStateWithLifecycle()
    val supplements by nutrition.supplements.collectAsStateWithLifecycle()
    val taken by nutrition.taken.collectAsStateWithLifecycle()
    val weighIns by nutrition.bodyWeights.collectAsStateWithLifecycle()
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val current by container.workouts.currentWorkout.collectAsStateWithLifecycle()
    val today = rememberToday()
    val todayKey = dayKey(today)
    val zone = container.time.zone()
    val haptics = rememberHaptics()

    var sheet by rememberSaveable { mutableStateOf<NutritionMeasure?>(null) }
    var sheetStart by rememberSaveable { mutableStateOf(MeasureSheetStart.Add) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    // § 7.1 : demandée à la création avec rappels, à l'activation et à l'ajout d'un rappel ; un
    // refus n'empêche rien (les rappels ne sonneront simplement pas).
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    // § 15.16.1 : l'objectif de protéines suit la dernière pesée connue (séance en cours comprise).
    val latestWeight = remember(today, workouts, current, weighIns, zone) { ProteinRules.latestWeight(today, workouts, current, weighIns, zone) }
    val averages = remember(tracker, today) { NutritionRules.weekAverages(tracker, today) }
    val day = DailyDay.of(tracker, todayKey, latestWeight)

    fun open(measure: NutritionMeasure, start: MeasureSheetStart) {
        sheetStart = start
        sheet = measure
    }

    // 🔄 D40 : listes groupées (DESIGN.md § 10.1) ; « Aujourd'hui » est le groupe du héros.
    HubTemplate(
        modifier = modifier,
        titleBar = { ScreenTitleBar(stringResource(R.string.nutrition_title)) },
        highlight = {
            TodaySurface(
                day = day,
                onAdd = { measure -> open(measure, MeasureSheetStart.Add) },
                onDetail = { measure -> open(measure, MeasureSheetStart.Detail) },
            )
        },
        insets = insets,
        grouped = true,
    ) {
        SupplementsSection(
            supplements = supplements,
            taken = taken,
            todayKey = todayKey,
            onToggleTaken = { id ->
                haptics.medium()
                container.launchMutation { nutrition.toggleTaken(id) }
            },
            onOpen = { openId = it },
            onCreate = { creating = true },
        )
        if (!averages.isEmpty) AveragesSection(averages)
    }

    sheet?.let { measure ->
        MeasureSheet(
            measure = measure,
            day = day.of(measure, averages),
            start = sheetStart,
            onDismiss = { sheet = null },
            onSave = { mode, value ->
                sheet = null
                container.launchMutation {
                    when (measure) {
                        NutritionMeasure.Calories -> if (mode == EntryMode.Add) nutrition.addCalories(value) else nutrition.setCaloriesToday(value)
                        NutritionMeasure.Water -> if (mode == EntryMode.Add) nutrition.addWater(value) else nutrition.setWaterToday(value)
                        NutritionMeasure.Protein -> if (mode == EntryMode.Add) nutrition.addProtein(value) else nutrition.setProteinToday(value)
                    }
                }
            },
            onSaveGoal = { goal ->
                container.launchMutation {
                    when (measure) {
                        NutritionMeasure.Calories -> nutrition.setCalorieGoal(goal)
                        NutritionMeasure.Water -> nutrition.setWaterGoal(goal)
                        NutritionMeasure.Protein -> Unit
                    }
                }
            },
            onSaveProteinGoal = { goal -> container.launchMutation { nutrition.setProteinGoal(goal) } },
        )
    }

    if (creating) {
        NewSupplementSheet(
            onDismiss = { creating = false },
            onSave = { name, dosage, unit, remindersEnabled, times ->
                creating = false
                if (remindersEnabled) requestNotificationPermission()
                container.launchMutation { nutrition.addSupplement(name, dosage, unit, remindersEnabled, times) }
            },
        )
    }

    SupplementSheetHost(openId, onClose = { openId = null })
}

/**
 * Sheet d'un complément et confirmation de sa suppression (§ 15.10.2), ouvertes depuis Nutrition et
 * depuis Paramètres › Rappels (§ 15.13.3, 🔄 D38) : [openId] = le complément ouvert (rien s'il n'existe
 * pas), [onClose] la ferme. Chaque changement s'applique tout de suite.
 */
@Composable
fun SupplementSheetHost(openId: String?, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val nutrition = container.nutrition
    val supplements by nutrition.supplements.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    supplements.firstOrNull { it.id == openId }?.let { supplement ->
        val id = supplement.id
        SupplementSheet(
            supplement = supplement,
            onDismiss = onClose,
            actions = SupplementActions(
                onRemindersEnabled = { enabled ->
                    haptics.light()
                    if (enabled) requestNotificationPermission()
                    container.launchMutation { nutrition.setRemindersEnabled(id, enabled) }
                },
                onAddReminder = {
                    haptics.light()
                    requestNotificationPermission()
                    container.launchMutation { nutrition.addReminder(id) }
                },
                onUpdateReminder = { reminderId, hour, minute ->
                    container.launchMutation { nutrition.updateReminder(id, reminderId, hour, minute) }
                },
                onRemoveReminder = { reminderId ->
                    haptics.light()
                    container.launchMutation { nutrition.removeReminder(id, reminderId) }
                },
                onSetDosesPerDay = { doses -> container.launchMutation { nutrition.setDosesPerDay(id, doses) } },
                onSetStock = { doses -> container.launchMutation { nutrition.setStock(id, doses) } },
                onRestock = { added -> container.launchMutation { nutrition.restock(id, added) } },
                onClearStock = {
                    haptics.light()
                    container.launchMutation { nutrition.clearStock(id) }
                },
                onSetLowStockDays = { days -> container.launchMutation { nutrition.setLowStockDays(id, days) } },
                onDelete = {
                    onClose()
                    deletingId = id
                },
            ),
        )
    }

    supplements.firstOrNull { it.id == deletingId }?.let { supplement ->
        ConfirmSheet(
            title = stringResource(R.string.nutrition_delete_confirm, supplement.name),
            message = stringResource(R.string.nutrition_delete_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.warning()
                deletingId = null
                container.launchMutation { nutrition.deleteSupplement(supplement.id) }
            },
            onDismiss = { deletingId = null },
        )
    }
}

/**
 * Calories, eau et protéines du jour [key] (§ 3.12, § 15.16.1) : objectifs des calories et de l'eau
 * en vigueur ce jour-là ; objectif de protéines du jour (`null` : pas d'objectif), avec le réglage et
 * le poids d'où il vient.
 */
internal data class DailyDay(
    val kcal: Int,
    val calorieGoal: Int,
    val ml: Int,
    val waterGoal: Int,
    val grams: Int = 0,
    val proteinGoal: ProteinGoal = ProteinGoal.DEFAULT,
    val latestWeight: Double? = null,
) {
    /** Objectif de protéines du jour (§ 15.16.1). */
    val proteinTarget = ProteinRules.target(proteinGoal, latestWeight)

    /** Ce que la sheet de [measure] montre (saisie, objectif, moyenne sur 7 jours de [averages]). */
    fun of(measure: NutritionMeasure, averages: NutritionAverages): MeasureDay = when (measure) {
        NutritionMeasure.Calories -> MeasureDay(measure, kcal, calorieGoal, averages.kcal)
        NutritionMeasure.Water -> MeasureDay(measure, ml, waterGoal, averages.waterMl)
        NutritionMeasure.Protein -> MeasureDay(
            measure, grams, proteinTarget?.grams, averages.proteinGrams,
            proteinGoal = proteinGoal, latestWeight = latestWeight, proteinPerKg = proteinTarget?.perKg,
        )
    }

    companion object {
        fun of(tracker: DailyTracker, key: String, latestWeight: Double? = null) = DailyDay(
            kcal = tracker.caloriesLog[key] ?: 0,
            calorieGoal = DailyTracker.goalFor(tracker.calorieGoalHistory, key) ?: tracker.calorieGoal,
            ml = tracker.waterLog[key] ?: 0,
            waterGoal = DailyTracker.goalFor(tracker.waterGoalHistory, key) ?: tracker.waterGoal,
            grams = tracker.proteinLog[key] ?: 0,
            proteinGoal = tracker.proteinGoal,
            latestWeight = latestWeight,
        )
    }
}

/** Eau affichée : « 750 ml », dès 1 000 ml « 1,5 L » (au dixième, § 2.11). */
@Composable
internal fun waterText(ml: Int): String =
    if (ml < 1000) stringResource(R.string.nutrition_ml_value, formatInteger(ml.toDouble()))
    else stringResource(R.string.common_liters_value, formatOneDecimal(ml / 1000.0))

/** Objectif d'eau : « 2,0 L ». */
@Composable
internal fun waterGoalText(ml: Int): String = stringResource(R.string.common_liters_value, formatOneDecimal(ml / 1000.0))

/** Protéines : « 112 g ». */
@Composable
internal fun gramsText(grams: Int): String = stringResource(R.string.nutrition_grams_value, formatInteger(grams.toDouble()))

/**
 * Groupe « Aujourd'hui » (§ 15.16.2, 🔄 D42, comme sur la maquette) : kcal du jour en chiffre héros et
 * l'état en mots — « encore 950 kcal » (gris), « dans l'objectif » (vert), « +320 kcal » (rouge) — selon
 * le ratio non plafonné de D15, le bouton + des calories et la piste ; puis les lignes Eau et
 * Protéines : objectif en sous-titre, valeur grise puis verte une fois l'objectif atteint, bouton + →
 * [onAdd], ligne → [onDetail].
 */
@Composable
internal fun TodaySurface(
    day: DailyDay,
    onAdd: (NutritionMeasure) -> Unit,
    onDetail: (NutritionMeasure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = NutritionRules.calorieStatus(day.kcal, day.calorieGoal)
    val goal = formatInteger(day.calorieGoal.toDouble())
    val (text, trend, context) = when (status.tone) {
        CalorieTone.Below ->
            if (day.kcal == 0) Triple(stringResource(R.string.nutrition_goal_only, goal), Trend.Neutral, null)
            else Triple(
                stringResource(R.string.nutrition_calories_left, formatInteger(status.remaining.toDouble())),
                Trend.Neutral,
                stringResource(R.string.nutrition_goal_of, goal),
            )
        CalorieTone.OnTarget -> Triple(stringResource(R.string.nutrition_calories_on_target), Trend.Up, stringResource(R.string.nutrition_goal_dot, goal))
        CalorieTone.Over -> Triple(
            stringResource(R.string.nutrition_calories_over, formatInteger(status.excess.toDouble())),
            Trend.Down,
            stringResource(R.string.nutrition_goal_over, goal),
        )
    }
    val detailLabel = stringResource(R.string.nutrition_detail_open)
    DailySurface(
        label = stringResource(R.string.nutrition_today_calories),
        value = formatInteger(day.kcal.toDouble()),
        unit = "kcal",
        progress = status.progress,
        addDescription = stringResource(R.string.nutrition_calories_add),
        onAdd = { onAdd(NutritionMeasure.Calories) },
        modifier = modifier,
        status = text,
        statusTrend = trend,
        statusContext = context,
    ) {
        MeasureRow(
            title = stringResource(R.string.progress_tab_water),
            value = waterText(day.ml),
            goal = waterGoalText(day.waterGoal),
            reached = NutritionRules.waterReached(day.ml, day.waterGoal),
            addDescription = stringResource(R.string.nutrition_water_add),
            onAdd = { onAdd(NutritionMeasure.Water) },
            onDetail = { onDetail(NutritionMeasure.Water) },
            detailLabel = detailLabel,
            showDivider = true,
        )
        val target = day.proteinTarget?.grams
        MeasureRow(
            title = stringResource(R.string.nutrition_protein),
            value = gramsText(day.grams),
            goal = target?.let { gramsText(it) },
            reached = ProteinRules.reached(day.grams, target),
            addDescription = stringResource(R.string.nutrition_protein_add),
            onAdd = { onAdd(NutritionMeasure.Protein) },
            onDetail = { onDetail(NutritionMeasure.Protein) },
            detailLabel = detailLabel,
            showDivider = false,
        )
    }
}

/**
 * Ligne Eau ou Protéines du groupe « Aujourd'hui » : sous-titre « objectif 2,5 L » (« … atteint », ou
 * « Pas d'objectif » sans [goal]) ; valeur grise, verte une fois [reached] ; bouton + plein.
 */
@Composable
private fun MeasureRow(
    title: String,
    value: String,
    goal: String?,
    reached: Boolean,
    addDescription: String,
    onAdd: () -> Unit,
    onDetail: () -> Unit,
    detailLabel: String,
    showDivider: Boolean,
) {
    ListRow(
        title = title,
        subtitle = when {
            goal == null -> stringResource(R.string.nutrition_protein_no_goal)
            reached -> stringResource(R.string.nutrition_goal_reached, goal)
            else -> stringResource(R.string.journal_day_goal, goal)
        },
        trailing = ListRowTrailing.ValueAction(
            value = value,
            icon = R.drawable.ds_ic_add,
            actionDescription = addDescription,
            onAction = onAdd,
            valueTrend = if (reached) Trend.Up else Trend.Neutral,
        ),
        showDivider = showDivider,
        onClick = onDetail,
        onClickLabel = detailLabel,
    )
}

/**
 * Section « Compléments » (§ 15.10.1, point 3 ; 🔄 D42) : « 1 sur 3 pris » à droite de l'étiquette, une
 * ligne par complément (dosage, doses par jour et rappels en sous-titre ; jours de stock puis case
 * « pris aujourd'hui » à droite, toucher → sheet), « Nouveau complément » ; état vide sans complément ;
 * rappel que les rappels sont des notifications locales.
 */
@Composable
internal fun SupplementsSection(
    supplements: List<Supplement>,
    taken: TakenRecord,
    todayKey: String,
    onToggleTaken: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val takenCount = NutritionRules.takenCount(supplements, taken, todayKey)
        SectionHeader(
            stringResource(R.string.nutrition_supplements),
            grouped = true,
            trailingText = if (supplements.isEmpty()) null else stringResource(R.string.nutrition_supplements_taken_count, takenCount, supplements.size),
            trailingDescription = if (supplements.isEmpty()) null
            else pluralStringResource(R.plurals.nutrition_supplements_taken_description, supplements.size, takenCount, supplements.size),
        )
        Spacer(Modifier.height(Studio.spacing.xs))
        if (supplements.isEmpty()) {
            EmptyState(
                icon = R.drawable.ds_ic_pill,
                title = stringResource(R.string.nutrition_empty_title),
                message = stringResource(R.string.nutrition_empty_text),
                actionLabel = stringResource(R.string.nutrition_empty_action),
                onAction = onCreate,
            )
            return@Column
        }
        val toggleLabel = stringResource(R.string.nutrition_taken_toggle)
        val openLabel = stringResource(R.string.nutrition_supplement_options)
        ListGroup {
            supplements.forEachIndexed { index, supplement ->
                val stock = StockRules.status(supplement)
                val days = stock?.let { stockShortText(it) }
                val daysDescription = if (stock != null && days != null) {
                    stringResource(if (stock.low) R.string.nutrition_stock_low_description else R.string.nutrition_stock_description, days)
                } else {
                    null
                }
                ListRow(
                    title = supplement.name,
                    subtitle = supplementSubtitle(supplement),
                    trailing = ListRowTrailing.Check(
                        checked = NutritionRules.isTaken(taken, supplement.id, todayKey),
                        onToggle = { onToggleTaken(supplement.id) },
                        toggleLabel = toggleLabel,
                        value = days,
                        valueTrend = if (stock?.low == true) Trend.Down else null,
                        valueDescription = daysDescription,
                    ),
                    showDivider = index < supplements.lastIndex,
                    onClick = { onOpen(supplement.id) },
                    onClickLabel = openLabel,
                )
            }
        }
        Spacer(Modifier.height(Studio.spacing.m))
        StudioButton(
            stringResource(R.string.nutrition_new_supplement),
            onCreate,
            Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            icon = R.drawable.ds_ic_add,
        )
        if (supplements.any { it.reminderEnabled }) {
            Spacer(Modifier.height(Studio.spacing.s))
            Text(
                stringResource(R.string.nutrition_info_mobile),
                Modifier.padding(horizontal = Studio.spacing.screenGrouped),
                style = Studio.typography.caption,
                color = Studio.colors.textSecondary,
            )
        }
    }
}

/**
 * Groupe « Moyenne sur 7 jours » (§ 15.16.6, 🔄 D42) : kcal, eau et protéines moyennes des 7 jours avant
 * aujourd'hui, chacune sur ses jours notés ; « — » pour une mesure sans jour.
 */
@Composable
internal fun AveragesSection(averages: NutritionAverages) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.nutrition_average_title), grouped = true)
        Spacer(Modifier.height(Studio.spacing.xs))
        ListGroup {
            StatRow(
                modifier = Modifier.padding(Studio.spacing.m),
                stats = listOf(
                    Stat(averages.kcal?.let { formatInteger(it.toDouble()) } ?: NO_VALUE, stringResource(R.string.nutrition_average_kcal)),
                    Stat(averages.waterMl?.let { waterText(it) } ?: NO_VALUE, stringResource(R.string.nutrition_average_water)),
                    Stat(averages.proteinGrams?.let { gramsText(it) } ?: NO_VALUE, stringResource(R.string.nutrition_average_protein)),
                ),
            )
        }
    }
}

/** Valeur absente d'une stat (« — »). */
internal const val NO_VALUE = "—"

/** Jours de stock d'une ligne (« 12 jours ») ; en doses (« 24 doses ») sans doses par jour valables. */
@Composable
internal fun stockShortText(stock: StockStatus): String {
    val days = stock.daysLeft ?: return pluralStringResource(R.plurals.journal_day_doses, stock.doses, stock.doses)
    return pluralStringResource(R.plurals.nutrition_stock_days, days, days)
}

/** « 5 g par prise · 2 doses par jour · Rappel à 08:00 » ; dosage omis s'il est vide (🔄 D15). */
@Composable
private fun supplementSubtitle(supplement: Supplement): String {
    val dosage = supplement.dosage.takeIf { it.isNotBlank() }?.let { stringResource(R.string.nutrition_per_intake, it, supplement.unit) }
    val doses = supplement.dosesPerDay.takeIf { it >= 2 }?.let { pluralStringResource(R.plurals.nutrition_doses_per_day_subtitle, it, it) }
    return listOfNotNull(dosage, doses, reminderSummaryText(supplement)).joinToString(" · ")
}

/** Résumé des rappels (§ 2.11) : « Pas de rappel », « Rappels activés », « Rappel à 08:00 », « 3 rappels ». */
@Composable
internal fun reminderSummaryText(supplement: Supplement): String = when (val summary = NutritionRules.reminderSummary(supplement)) {
    ReminderSummary.Off -> stringResource(R.string.nutrition_no_reminder)
    ReminderSummary.On -> stringResource(R.string.nutrition_reminders_on)
    is ReminderSummary.At -> stringResource(R.string.nutrition_reminder_at, formatReminderTime(summary.hour, summary.minute))
    is ReminderSummary.Count -> stringResource(R.string.nutrition_reminders_count, summary.count)
}

/** `HH:mm`, affichage d'un rappel (§ 2.11). */
fun formatReminderTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
