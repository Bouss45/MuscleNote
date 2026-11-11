package com.musclenote.android.ui.nutrition

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.notifications.AppNotifications
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Onglet Nutrition (SPEC § 2.11) : suivi du jour (calories et eau, objectifs datés) et compléments
 * (formulaire, « pris aujourd'hui », rappels). Le jour affiché est le jour courant de l'app
 * (§ 3.12) : il change à minuit et au retour au premier plan.
 */
@Composable
fun NutritionScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val nutrition = container.nutrition
    val tracker by nutrition.tracker.collectAsStateWithLifecycle()
    val supplements by nutrition.supplements.collectAsStateWithLifecycle()
    val taken by nutrition.taken.collectAsStateWithLifecycle()
    val todayKey = dayKey(rememberToday())
    val haptics = rememberHaptics()
    val context = LocalContext.current

    var showForm by rememberSaveable { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Rappel d'une carte en cours de modification : complément, rappel.
    var pickingSupplementId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingReminderId by rememberSaveable { mutableStateOf<String?>(null) }

    // § 7.1 : demandée à la création avec rappels, à l'activation et à l'ajout d'un rappel ; un
    // refus n'empêche rien (les rappels ne sonneront simplement pas).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotificationPermission() {
        if (AppNotifications.needsPermissionRequest(context)) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nutrition_title),
            action = {
                HeaderButton(
                    formOpen = showForm,
                    onClick = {
                        haptics.light()
                        showForm = !showForm
                        deletingId = null
                        expandedId = null
                    },
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.nutrition_section_today))
            CaloriesCard(
                kcal = tracker.caloriesLog[todayKey] ?: 0,
                goal = DailyTracker.goalFor(tracker.calorieGoalHistory, todayKey) ?: tracker.calorieGoal,
                onAdd = { amount -> container.launchMutation { nutrition.addCalories(amount) } },
                onSet = { value -> container.launchMutation { nutrition.setCaloriesToday(value) } },
                onReset = { container.launchMutation { nutrition.setCaloriesToday(0) } },
                onGoalChange = { kcal -> container.launchMutation { nutrition.setCalorieGoal(kcal) } },
            )
            WaterCard(
                ml = tracker.waterLog[todayKey] ?: 0,
                goalMl = DailyTracker.goalFor(tracker.waterGoalHistory, todayKey) ?: tracker.waterGoal,
                onAdd = { ml -> container.launchMutation { nutrition.addWater(ml) } },
                onReset = { container.launchMutation { nutrition.resetWaterToday() } },
                onGoalChange = { ml -> container.launchMutation { nutrition.setWaterGoal(ml) } },
            )

            SectionLabel(
                stringResource(R.string.nutrition_supplements_section) + if (supplements.isNotEmpty()) " (${supplements.size})" else "",
                Modifier.padding(top = 4.dp),
            )

            if (showForm) {
                SupplementForm(
                    onSave = { name, dosage, unit, remindersEnabled, times ->
                        if (remindersEnabled) requestNotificationPermission()
                        container.launchMutation { nutrition.addSupplement(name, dosage, unit, remindersEnabled, times) }
                        showForm = false
                    },
                )
            } else if (supplements.isEmpty()) {
                EmptySupplements()
            }

            for (supplement in supplements) {
                key(supplement.id) {
                    val id = supplement.id
                    SupplementCard(
                        supplement = supplement,
                        taken = NutritionRules.isTaken(taken, id, todayKey),
                        expanded = expandedId == id,
                        deleting = deletingId == id,
                        onToggleTaken = {
                            haptics.medium()
                            container.launchMutation { nutrition.toggleTaken(id) }
                        },
                        onToggleExpanded = { expandedId = if (expandedId == id) null else id },
                        onDeleteRequest = {
                            expandedId = null
                            deletingId = if (deletingId == id) null else id
                        },
                        onDeleteCancel = { deletingId = null },
                        onDeleteConfirm = {
                            haptics.warning()
                            deletingId = null
                            container.launchMutation { nutrition.deleteSupplement(id) }
                        },
                        onRemindersEnabled = { enabled ->
                            haptics.light()
                            if (enabled) requestNotificationPermission()
                            container.launchMutation { nutrition.setRemindersEnabled(id, enabled) }
                        },
                        onPickReminder = { reminder ->
                            pickingSupplementId = id
                            pickingReminderId = reminder.id
                        },
                        onRemoveReminder = { reminder ->
                            haptics.light()
                            container.launchMutation { nutrition.removeReminder(id, reminder.id) }
                        },
                        onAddReminder = {
                            haptics.light()
                            requestNotificationPermission()
                            container.launchMutation { nutrition.addReminder(id) }
                        },
                    )
                }
            }

            if (supplements.any { it.reminderEnabled }) InfoBox(stringResource(R.string.nutrition_info_mobile))
        }
    }

    val picked = supplements.firstOrNull { it.id == pickingSupplementId }?.reminders?.firstOrNull { it.id == pickingReminderId }
    if (picked != null) {
        val supplementId = pickingSupplementId.orEmpty()
        ReminderTimePickerDialog(
            hour = picked.hour,
            minute = picked.minute,
            onDismiss = {
                pickingSupplementId = null
                pickingReminderId = null
            },
            onConfirm = { hour, minute ->
                pickingSupplementId = null
                pickingReminderId = null
                container.launchMutation { nutrition.updateReminder(supplementId, picked.id, hour, minute) }
            },
        )
    }
}

/** « + Complément », ou « ✕ Annuler » quand le formulaire est ouvert. */
@Composable
private fun HeaderButton(formOpen: Boolean, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painterResource(if (formOpen) R.drawable.ic_close else R.drawable.ic_add),
            contentDescription = null,
            tint = colors.primaryForeground,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(if (formOpen) R.string.common_cancel else R.string.nutrition_supplement),
            color = colors.primaryForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 💊 « Aucun complément » + aide (formulaire fermé). */
@Composable
private fun EmptySupplements() {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .size(60.dp)
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text("💊", fontSize = 28.sp)
        }
        Text(stringResource(R.string.nutrition_empty_title), color = colors.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.nutrition_empty_text),
            color = colors.mutedForeground,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Encart d'information (rappels = notifications locales). */
@Composable
private fun InfoBox(text: String) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
        Text(text, color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** Libellé de section en capitales (« SUIVI DU JOUR », « COMPLÉMENTS »). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(bottom = 2.dp),
    )
}
