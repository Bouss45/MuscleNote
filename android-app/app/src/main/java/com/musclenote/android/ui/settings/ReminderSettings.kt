package com.musclenote.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.nutrition.WeighInDays
import com.musclenote.android.data.nutrition.WeighInReminderRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberNotificationPermissionRequest
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.LabeledField
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.nutrition.ReminderTimeDialog
import com.musclenote.android.ui.nutrition.formatReminderTime
import com.musclenote.android.ui.nutrition.reminderSummaryText
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Ligne « Rappel de pesée » de Paramètres › Rappels (SPEC § 15.13.3, 🔄 D38) : « 07:30 » ou
 * « Désactivé », les jours en sous-titre (ou « Pour penser à te peser ») ; [onOpen] ouvre la sheet.
 */
@Composable
internal fun weighInReminderRow(onOpen: () -> Unit): SettingsRow {
    val preferences by LocalAppContainer.current.preferences.preferences.collectAsStateWithLifecycle()
    val reminder = preferences.weighInReminder
    return SettingsRow(
        key = "weigh-in-reminder",
        title = stringResource(R.string.weigh_in_reminder_title),
        subtitle = if (reminder.enabled) weighInDaysText(reminder) else stringResource(R.string.weigh_in_reminder_off_subtitle),
        value = if (reminder.enabled) formatReminderTime(reminder.hour, reminder.minute) else stringResource(R.string.settings_off_m),
        onClick = onOpen,
    )
}

/**
 * Une ligne par complément (les rappels de Nutrition, § 15.13.3) : nom, résumé de ses rappels ;
 * [onOpen] ouvre sa sheet de Nutrition.
 */
@Composable
internal fun supplementReminderRows(onOpen: (supplementId: String) -> Unit): List<SettingsRow> {
    val supplements by LocalAppContainer.current.nutrition.supplements.collectAsStateWithLifecycle()
    return supplements.map { supplement ->
        SettingsRow(
            key = "supplement:${supplement.id}",
            title = supplement.name,
            subtitle = reminderSummaryText(supplement),
            chevron = true,
            onClick = { onOpen(supplement.id) },
        )
    }
}

/** « Tous les jours », « Aucun jour » ou « lun., mer., ven. » (libellés courts de la langue). */
@Composable
internal fun weighInDaysText(reminder: WeighInReminder): String = when (val days = WeighInReminderRules.daysSummary(reminder)) {
    WeighInDays.EveryDay -> stringResource(R.string.weigh_in_reminder_every_day)
    WeighInDays.NoDay -> stringResource(R.string.weigh_in_reminder_no_day)
    is WeighInDays.Some -> {
        val locale = appLocale()
        days.days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

/**
 * Sheet « Rappel de pesée » (§ 15.13.3), ouverte depuis Paramètres › Rappels et depuis la surface
 * « Poids de corps » de Progrès. Chaque changement s'applique tout de suite ; l'alarme suit (§ 15.13.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeighInReminderSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        WeighInReminderSheetContent(
            reminder = preferences.weighInReminder,
            onToggle = { enabled ->
                haptics.light()
                // § 7.1 : demandée à l'activation ; un refus n'empêche rien (le rappel ne sonnera pas).
                if (enabled) requestNotificationPermission()
                container.launchMutation { container.preferences.updateWeighInReminder { it.copy(enabled = enabled) } }
            },
            onTime = { hour, minute ->
                container.launchMutation { container.preferences.updateWeighInReminder { it.copy(hour = hour, minute = minute) } }
            },
            onToggleDay = { day ->
                haptics.light()
                container.launchMutation { container.preferences.updateWeighInReminder { WeighInReminderRules.toggleDay(it, day) } }
            },
            onClose = onDismiss,
            bottomInset = inset,
        )
    }
}

/**
 * Contenu de [WeighInReminderSheet] : ligne à cocher, heure (→ `TimePicker`), jours en puces à choix
 * multiple (lundi → dimanche, lus en entier par TalkBack), « Fermer ».
 */
@Composable
internal fun WeighInReminderSheetContent(
    reminder: WeighInReminder,
    onToggle: (Boolean) -> Unit,
    onTime: (hour: Int, minute: Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val locale = appLocale()
    val selected = WeighInReminderRules.daysOfWeek(reminder)
    SheetSurface(Modifier.verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(
            stringResource(R.string.weigh_in_reminder_title),
            AnnotatedString(stringResource(R.string.weigh_in_reminder_help)),
            messageStyle = Studio.typography.body,
        )
        Column(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.weigh_in_reminder_toggle),
                trailing = ListRowTrailing.Check(reminder.enabled),
                showDivider = false,
                onClick = { onToggle(!reminder.enabled) },
            )
            InfoRow(
                label = stringResource(R.string.weigh_in_reminder_time),
                value = formatReminderTime(reminder.hour, reminder.minute),
                onClick = { picking = true },
            )
        }
        LabeledField(stringResource(R.string.weigh_in_reminder_days)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
            ) {
                for (day in DayOfWeek.entries) {
                    FilterChip(
                        day.getDisplayName(TextStyle.SHORT, locale),
                        selected = day in selected,
                        onClick = { onToggleDay(day) },
                        multiSelect = true,
                        description = day.getDisplayName(TextStyle.FULL, locale),
                    )
                }
            }
        }
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }

    if (picking) {
        ReminderTimeDialog(
            hour = reminder.hour,
            minute = reminder.minute,
            onDismiss = { picking = false },
            onConfirm = { hour, minute ->
                picking = false
                onTime(hour, minute)
            },
            onRemove = null,
        )
    }
}
