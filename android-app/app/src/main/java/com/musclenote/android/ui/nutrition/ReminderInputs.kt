package com.musclenote.android.ui.nutrition

import android.text.format.DateFormat
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclenote.android.R
import com.musclenote.android.ui.components.dashedBorder
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** `HH:mm`, affichage d'un rappel (§ 2.11). */
fun formatReminderTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * Ligne d'un rappel : horloge et `HH:mm` (toucher → sélecteur d'heure, 🔄 D7), ✕ seulement s'il y
 * a plus d'un rappel ([onRemove] non nul).
 */
@Composable
fun ReminderRow(hour: Int, minute: Int, onPick: () -> Unit, onRemove: (() -> Unit)?) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.input)
            .border(1.dp, colors.border, shape)
            .clickable(onClickLabel = stringResource(R.string.nutrition_reminder_time_title), onClick = onPick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
        Text(
            formatReminderTime(hour, minute),
            color = colors.foreground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        if (onRemove != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_delete), tint = colors.destructive, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** « + Ajouter un rappel », bordure pointillée. */
@Composable
fun AddReminderButton(onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primary.copy(alpha = 0.06f))
            .dashedBorder(1.dp, colors.primary.copy(alpha = 0.33f), 10.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.nutrition_add_reminder), color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * « Heure du rappel » (🔄 D7) : `TimePicker` Material 3 en dialogue, au format 24 h ou 12 h selon
 * le réglage du système ; toute minute de 0 à 59.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (hour: Int, minute: Int) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(24.dp)
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.nutrition_reminder_time_title),
                color = colors.mutedForeground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.muted,
                    clockDialSelectedContentColor = colors.primaryForeground,
                    clockDialUnselectedContentColor = colors.foreground,
                    selectorColor = colors.primary,
                    periodSelectorBorderColor = colors.border,
                    periodSelectorSelectedContainerColor = colors.primary.copy(alpha = 0.2f),
                    periodSelectorUnselectedContainerColor = colors.card,
                    periodSelectorSelectedContentColor = colors.foreground,
                    periodSelectorUnselectedContentColor = colors.mutedForeground,
                    timeSelectorSelectedContainerColor = colors.primary.copy(alpha = 0.2f),
                    timeSelectorUnselectedContainerColor = colors.muted,
                    timeSelectorSelectedContentColor = colors.foreground,
                    timeSelectorUnselectedContentColor = colors.foreground,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = colors.mutedForeground, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                    Text(stringResource(R.string.common_confirm), color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
