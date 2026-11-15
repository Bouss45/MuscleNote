package com.musclenote.android.ui.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.nutrition.CalorieTone
import com.musclenote.android.data.nutrition.EntryMode
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.nutrition.ReminderSummary
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
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.templates.HubTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.progress.DailyKind
import com.musclenote.android.ui.workout.ConfirmSheet

/**
 * Onglet Nutrition (SPEC § 15.10.1, 🔄 D35), gabarit hub : surface « Aujourd'hui » (calories du jour,
 * état par rapport à l'objectif, eau, compléments pris, saisies Calories et Eau au pavé), puis
 * « Compléments » (case « pris aujourd'hui », toucher → sheet des rappels). Le jour affiché est le
 * jour courant de l'app (§ 3.12) : il change à minuit et au retour au premier plan.
 */
@Composable
fun NutritionScreen(modifier: Modifier = Modifier, insets: ScreenInsets = tabScreenInsets()) {
    val container = LocalAppContainer.current
    val nutrition = container.nutrition
    val tracker by nutrition.tracker.collectAsStateWithLifecycle()
    val supplements by nutrition.supplements.collectAsStateWithLifecycle()
    val taken by nutrition.taken.collectAsStateWithLifecycle()
    val todayKey = dayKey(rememberToday())
    val haptics = rememberHaptics()

    var entry by rememberSaveable { mutableStateOf<DailyKind?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    // § 7.1 : demandée à la création avec rappels, à l'activation et à l'ajout d'un rappel ; un
    // refus n'empêche rien (les rappels ne sonneront simplement pas).
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    val day = DailyDay.of(tracker, todayKey)
    HubTemplate(
        modifier = modifier,
        titleBar = { ScreenTitleBar(stringResource(R.string.nutrition_title)) },
        highlight = {
            TodaySurface(
                day = day,
                takenCount = NutritionRules.takenCount(supplements, taken, todayKey),
                supplementCount = supplements.size,
                onCalories = { entry = DailyKind.Calories },
                onWater = { entry = DailyKind.Water },
            )
        },
        insets = insets,
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
    }

    entry?.let { kind ->
        DailyEntrySheet(
            kind = kind,
            current = if (kind == DailyKind.Calories) day.kcal else day.ml,
            goal = if (kind == DailyKind.Calories) day.calorieGoal else day.waterGoal,
            onDismiss = { entry = null },
            onSave = { mode, value ->
                entry = null
                container.launchMutation {
                    when (kind) {
                        DailyKind.Calories -> if (mode == EntryMode.Add) nutrition.addCalories(value) else nutrition.setCaloriesToday(value)
                        DailyKind.Water -> if (mode == EntryMode.Add) nutrition.addWater(value) else nutrition.setWaterToday(value)
                    }
                }
            },
            onSaveGoal = { goal ->
                container.launchMutation {
                    if (kind == DailyKind.Calories) nutrition.setCalorieGoal(goal) else nutrition.setWaterGoal(goal)
                }
            },
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
            onDelete = {
                onClose()
                deletingId = id
            },
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

/** Calories et eau du jour [key], avec les objectifs en vigueur ce jour-là (§ 3.12). */
internal data class DailyDay(val kcal: Int, val calorieGoal: Int, val ml: Int, val waterGoal: Int) {
    companion object {
        fun of(tracker: DailyTracker, key: String) = DailyDay(
            kcal = tracker.caloriesLog[key] ?: 0,
            calorieGoal = DailyTracker.goalFor(tracker.calorieGoalHistory, key) ?: tracker.calorieGoal,
            ml = tracker.waterLog[key] ?: 0,
            waterGoal = DailyTracker.goalFor(tracker.waterGoalHistory, key) ?: tracker.waterGoal,
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

/**
 * Surface « Aujourd'hui » (§ 15.10.1, point 2) : kcal du jour en chiffre héros et l'état en mots —
 * « encore 950 kcal » (gris), « dans l'objectif » (vert), « +320 kcal » (rouge) — selon le ratio
 * non plafonné de D15 ; piste ; eau et compléments pris ; Calories et Eau.
 */
@Composable
internal fun TodaySurface(
    day: DailyDay,
    takenCount: Int,
    supplementCount: Int,
    onCalories: () -> Unit,
    onWater: () -> Unit,
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
    val water = Stat(
        waterText(day.ml),
        if (NutritionRules.waterReached(day.ml, day.waterGoal)) stringResource(R.string.nutrition_water_reached)
        else stringResource(R.string.nutrition_water_of, waterGoalText(day.waterGoal)),
    )
    val stats = buildList {
        add(water)
        if (supplementCount > 0) add(Stat("$takenCount/$supplementCount", stringResource(R.string.nutrition_supplements_taken_label)))
    }
    DailySurface(
        label = stringResource(R.string.nutrition_today),
        value = formatInteger(day.kcal.toDouble()),
        unit = "kcal",
        progress = status.progress,
        stats = stats,
        firstAction = stringResource(R.string.progress_tab_calories),
        onFirstAction = onCalories,
        secondAction = stringResource(R.string.progress_tab_water),
        onSecondAction = onWater,
        modifier = modifier,
        status = text,
        statusTrend = trend,
        statusContext = context,
    )
}

/**
 * Section « Compléments » (§ 15.10.1, point 3) : une ligne par complément (dosage et rappels en
 * sous-titre, case « pris aujourd'hui » à droite, toucher → sheet), « Nouveau complément » ; état vide
 * sans complément ; rappel que les rappels sont des notifications locales.
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
        // Le compte des compléments pris est déjà sur la surface « Aujourd'hui ».
        SectionHeader(stringResource(R.string.nutrition_supplements))
        Spacer(Modifier.height(Studio.spacing.xxs))
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
        supplements.forEachIndexed { index, supplement ->
            ListRow(
                title = supplement.name,
                subtitle = supplementSubtitle(supplement),
                trailing = ListRowTrailing.Check(
                    checked = NutritionRules.isTaken(taken, supplement.id, todayKey),
                    onToggle = { onToggleTaken(supplement.id) },
                    toggleLabel = toggleLabel,
                ),
                showDivider = index < supplements.lastIndex,
                onClick = { onOpen(supplement.id) },
                onClickLabel = openLabel,
            )
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
            Text(stringResource(R.string.nutrition_info_mobile), style = Studio.typography.caption, color = Studio.colors.textSecondary)
        }
    }
}

/** « 5 g par prise · Rappel à 08:00 » ; dosage omis s'il est vide (🔄 D15). */
@Composable
private fun supplementSubtitle(supplement: Supplement): String {
    val dosage = supplement.dosage.takeIf { it.isNotBlank() }?.let { stringResource(R.string.nutrition_per_intake, it, supplement.unit) }
    return listOfNotNull(dosage, reminderSummaryText(supplement)).joinToString(" · ")
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
