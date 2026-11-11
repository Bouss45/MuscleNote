package com.musclenote.android.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme

private const val NAME_MAX_LENGTH = 40
private const val DOSAGE_MAX_LENGTH = 10

/** Rappels du formulaire, en minutes depuis minuit, conservés à la recréation de l'activité. */
private val TimesSaver = listSaver<MutableState<List<Int>>, Int>(
    save = { it.value },
    restore = { mutableStateOf(it) },
)

private const val DEFAULT_TIME = NutritionRules.DEFAULT_REMINDER_HOUR * 60

/**
 * Formulaire « Nouveau complément » (§ 2.11) : nom (focus automatique), dosage par prise (texte
 * libre au clavier décimal, gardé tel que saisi), unité, interrupteur « Rappels » (activé, 08:00
 * au départ ; réactivé sans rappel → 08:00) et rappels choisis au `TimePicker` (🔄 D7).
 * « Enregistrer » actif dès qu'un nom est saisi. [onSave] reçoit les rappels `(heure, minute)`.
 */
@Composable
fun SupplementForm(onSave: (name: String, dosage: String, unit: String, remindersEnabled: Boolean, times: List<Pair<Int, Int>>) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    var name by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(NutritionRules.UNITS.first()) }
    var remindersOn by rememberSaveable { mutableStateOf(true) }
    var times by rememberSaveable(saver = TimesSaver) { mutableStateOf(listOf(DEFAULT_TIME)) }
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val canSave = name.isNotBlank()

    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.5.dp, colors.primary.copy(alpha = 0.33f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nutrition_new_supplement), color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        FieldLabel(stringResource(R.string.nutrition_name))
        val focusRequester = remember { FocusRequester() }
        FormField(
            value = name,
            onValueChange = { name = it.take(NAME_MAX_LENGTH) },
            placeholder = stringResource(R.string.nutrition_name_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.focusRequester(focusRequester),
        )
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

        FieldLabel(stringResource(R.string.nutrition_dosage_per_intake))
        FormField(
            value = dosage,
            onValueChange = { dosage = it.take(DOSAGE_MAX_LENGTH) },
            placeholder = stringResource(R.string.nutrition_dosage_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in NutritionRules.UNITS) {
                val active = option == unit
                val chipShape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .clip(chipShape)
                        .background(if (active) colors.primary else colors.card)
                        .border(1.dp, if (active) colors.primary else colors.border, chipShape)
                        .clickable { unit = option }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(option, color = if (active) colors.primaryForeground else colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.nutrition_reminders), color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            AppSwitch(
                checked = remindersOn,
                onCheckedChange = { on ->
                    remindersOn = on
                    if (on && times.isEmpty()) times = listOf(DEFAULT_TIME)
                },
            )
        }
        if (remindersOn) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEachIndexed { index, time ->
                    ReminderRow(
                        hour = time / 60,
                        minute = time % 60,
                        onPick = { picking = index },
                        onRemove = if (times.size > 1) ({ times = times.filterIndexed { i, _ -> i != index } }) else null,
                    )
                }
                AddReminderButton(
                    onClick = {
                        haptics.light()
                        val last = times.lastOrNull()?.let { it / 60 to it % 60 }
                        val (hour, minute) = NutritionRules.nextReminderTime(last)
                        times = times + (hour * 60 + minute)
                    },
                )
            }
        }

        val buttonShape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(buttonShape)
                .background(if (canSave) colors.primary else colors.muted)
                .clickable(enabled = canSave) {
                    haptics.medium()
                    onSave(name, dosage, unit, remindersOn, times.map { it / 60 to it % 60 })
                }
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = if (canSave) colors.primaryForeground else colors.mutedForeground
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.common_save), color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    val pickingIndex = picking
    val pickingTime = pickingIndex?.let { times.getOrNull(it) }
    if (pickingIndex != null && pickingTime != null) {
        ReminderTimePickerDialog(
            hour = pickingTime / 60,
            minute = pickingTime % 60,
            onDismiss = { picking = null },
            onConfirm = { hour, minute ->
                times = times.mapIndexed { i, t -> if (i == pickingIndex) hour * 60 + minute else t }
                picking = null
            },
        )
    }
}

/** Interrupteur aux couleurs de l'app. */
@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.mutedForeground,
            uncheckedTrackColor = colors.muted,
            uncheckedBorderColor = colors.border,
        ),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 15.sp),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.input)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.mutedForeground, fontSize = 15.sp)
                inner()
            }
        },
    )
}
