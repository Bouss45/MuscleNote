package com.musclenote.android.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Carte d'un complément (§ 2.11) : rond « pris aujourd'hui », nom, dosage par prise (masqué s'il
 * est vide, 🔄 D15), suppression avec confirmation en ligne ; ligne des rappels (résumé qui déplie
 * la liste modifiable, interrupteur) ; badge « Pris / Non pris aujourd'hui ».
 */
@Composable
fun SupplementCard(
    supplement: Supplement,
    taken: Boolean,
    expanded: Boolean,
    deleting: Boolean,
    onToggleTaken: () -> Unit,
    onToggleExpanded: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onRemindersEnabled: (Boolean) -> Unit,
    onPickReminder: (Reminder) -> Unit,
    onRemoveReminder: (Reminder) -> Unit,
    onAddReminder: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (taken) colors.primary.copy(alpha = 0.07f) else colors.card)
            .border(1.dp, if (taken) colors.primary.copy(alpha = 0.33f) else colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleTaken),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (taken) colors.primary else Color.Transparent)
                        .border(2.dp, if (taken) colors.primary else colors.mutedForeground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (taken) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(supplement.name, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (supplement.dosage.isNotBlank()) {
                    Text(
                        stringResource(R.string.nutrition_per_intake, supplement.dosage, supplement.unit),
                        color = colors.mutedForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            val buttonShape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(buttonShape)
                    .border(1.dp, colors.border, buttonShape)
                    .clickable(onClick = onDeleteRequest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.common_delete), tint = colors.destructive, modifier = Modifier.size(14.dp))
            }
        }

        if (deleting) {
            InlineConfirm(
                message = stringResource(R.string.nutrition_delete_confirm, supplement.name),
                confirmLabel = stringResource(R.string.common_delete),
                confirmIcon = R.drawable.ic_delete,
                onCancel = onDeleteCancel,
                onConfirm = onDeleteConfirm,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.destructive.copy(alpha = 0.08f))
                    .border(1.dp, colors.destructive.copy(alpha = 0.27f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }

        HorizontalDivider(thickness = 1.dp, color = colors.border)
        RemindersSection(supplement, expanded, onToggleExpanded, onRemindersEnabled, onPickReminder, onRemoveReminder, onAddReminder)

        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (taken) colors.primary.copy(alpha = 0.13f) else colors.muted.copy(alpha = 0.27f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                stringResource(if (taken) R.string.nutrition_taken_yes else R.string.nutrition_taken_no),
                color = if (taken) colors.primary else colors.mutedForeground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Résumé des rappels (« Pas de rappel », « Rappels activés », « Rappel à 08:00 », « n rappels »)
 * qui déplie la liste modifiable, appliquée tout de suite ; interrupteur des rappels.
 */
@Composable
private fun RemindersSection(
    supplement: Supplement,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRemindersEnabled: (Boolean) -> Unit,
    onPickReminder: (Reminder) -> Unit,
    onRemoveReminder: (Reminder) -> Unit,
    onAddReminder: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val enabled = supplement.reminderEnabled
    val reminders = supplement.reminders
    val tint = if (enabled) colors.primary else colors.mutedForeground
    val summary = when {
        !enabled -> stringResource(R.string.nutrition_no_reminder)
        reminders.isEmpty() -> stringResource(R.string.nutrition_reminders_on)
        reminders.size == 1 -> stringResource(R.string.nutrition_reminder_at, formatReminderTime(reminders[0].hour, reminders[0].minute))
        else -> stringResource(R.string.nutrition_reminders_count, reminders.size)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(if (enabled) R.drawable.ic_notifications else R.drawable.ic_notifications_off),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(15.dp),
                )
                Text(summary, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (reminders.isNotEmpty() || expanded) {
                    Icon(
                        painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = colors.mutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            AppSwitch(checked = enabled, onCheckedChange = onRemindersEnabled, modifier = Modifier.scale(0.8f))
        }
        if (expanded) {
            for (reminder in reminders) {
                ReminderRow(
                    hour = reminder.hour,
                    minute = reminder.minute,
                    onPick = { onPickReminder(reminder) },
                    onRemove = if (reminders.size > 1) ({ onRemoveReminder(reminder) }) else null,
                )
            }
            AddReminderButton(onClick = onAddReminder)
        }
    }
}
