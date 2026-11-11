package com.musclenote.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclenote.android.R
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.buildLocalDate
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.util.parseIntegerOrNull
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Modale « Modifier la date » d'une séance terminée (§ 2.2) : Jour / Mois / Année puis
 * Heure : Min, préremplis avec la date locale de la séance. Heure ou minute vide → 12 h / 00.
 * Une date impossible (§ 10.3) affiche « Date ou heure invalide » avec une vibration d'erreur et
 * laisse la modale ouverte. Toucher le voile ou « Annuler » ferme sans enregistrer.
 */
@Composable
fun WorkoutDateDialog(workout: Workout, zone: ZoneId, onDismiss: () -> Unit, onSave: (Instant) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    val start = remember(workout.id) { parseInstantOrNull(workout.date, zone)?.atZone(zone) }
    var day by rememberSaveable(workout.id) { mutableStateOf(start?.dayOfMonth?.toString().orEmpty()) }
    var month by rememberSaveable(workout.id) { mutableStateOf(start?.monthValue?.toString().orEmpty()) }
    var year by rememberSaveable(workout.id) { mutableStateOf(start?.year?.toString().orEmpty()) }
    var hour by rememberSaveable(workout.id) { mutableStateOf(start?.hour?.toString()?.padStart(2, '0').orEmpty()) }
    var minute by rememberSaveable(workout.id) { mutableStateOf(start?.minute?.toString()?.padStart(2, '0').orEmpty()) }
    var invalid by rememberSaveable(workout.id) { mutableStateOf(false) }

    fun save() {
        val date = buildLocalDate(
            year = parseIntegerOrNull(year),
            month = parseIntegerOrNull(month),
            day = parseIntegerOrNull(day),
            hour = if (hour.isBlank()) 12 else parseIntegerOrNull(hour),
            minute = if (minute.isBlank()) 0 else parseIntegerOrNull(minute),
        )
        if (date == null) {
            invalid = true
            haptics.warning()
        } else {
            haptics.success()
            onSave(date.atZone(zone).toInstant())
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_edit_date), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(workoutDisplayName(workout), color = colors.mutedForeground, fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateField(stringResource(R.string.home_date_day), day, { day = it }, stringResource(R.string.date_day), maxLength = 2)
                Separator("/")
                DateField(stringResource(R.string.home_date_month), month, { month = it }, stringResource(R.string.date_month), maxLength = 2)
                Separator("/")
                DateField(stringResource(R.string.home_date_year), year, { year = it }, stringResource(R.string.date_year), maxLength = 4, weight = 2f)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateField(stringResource(R.string.home_date_hour), hour, { hour = it }, stringResource(R.string.date_hh), maxLength = 2)
                Separator(":")
                DateField(
                    stringResource(R.string.home_date_minute),
                    minute,
                    { minute = it },
                    stringResource(R.string.date_mm),
                    maxLength = 2,
                    imeAction = ImeAction.Done,
                )
            }

            if (invalid) {
                Text(stringResource(R.string.date_invalid), color = colors.destructive, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val buttonShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .border(1.dp, colors.border, buttonShape)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .background(colors.primary)
                        .clickable { save() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.common_save), color = colors.primaryForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Champ numérique libellé, tout sélectionné au focus ; « Suivant » passe au champ suivant. */
@Composable
private fun RowScope.DateField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    weight: Float = 1f,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MuscleNoteTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) value = value.copy(selection = TextRange(0, value.text.length))
    }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.weight(weight), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.mutedForeground, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = { changed ->
                if (changed.text.length <= maxLength) {
                    val textChanged = changed.text != value.text
                    value = changed
                    if (textChanged) onTextChange(changed.text)
                }
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = colors.foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(colors.background)
                        .border(1.dp, colors.border, shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.text.isEmpty()) {
                        Text(placeholder, color = colors.mutedForeground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Separator(text: String) {
    Text(
        text,
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}
