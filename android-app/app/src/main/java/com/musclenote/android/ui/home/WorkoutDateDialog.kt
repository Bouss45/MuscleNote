package com.musclenote.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.buildLocalDate
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.util.parseIntegerOrNull
import com.musclenote.android.ui.components.DateField
import com.musclenote.android.ui.components.DateSeparator
import com.musclenote.android.ui.components.FormDialog
import com.musclenote.android.ui.components.FormDialogButtons
import com.musclenote.android.ui.components.FormDialogError
import com.musclenote.android.ui.components.FormDialogTitle
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
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

    FormDialog(onDismiss = onDismiss) {
        FormDialogTitle(stringResource(R.string.home_edit_date), workoutDisplayName(workout))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DateField(day, { day = it }, stringResource(R.string.date_day), maxLength = 2, label = stringResource(R.string.home_date_day))
            DateSeparator("/")
            DateField(month, { month = it }, stringResource(R.string.date_month), maxLength = 2, label = stringResource(R.string.home_date_month))
            DateSeparator("/")
            DateField(year, { year = it }, stringResource(R.string.date_year), maxLength = 4, label = stringResource(R.string.home_date_year), weight = 2f)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DateField(hour, { hour = it }, stringResource(R.string.date_hh), maxLength = 2, label = stringResource(R.string.home_date_hour))
            DateSeparator(":")
            DateField(
                minute,
                { minute = it },
                stringResource(R.string.date_mm),
                maxLength = 2,
                label = stringResource(R.string.home_date_minute),
                imeAction = ImeAction.Done,
            )
        }
        if (invalid) FormDialogError(stringResource(R.string.date_invalid))
        FormDialogButtons(onCancel = onDismiss, onSave = { save() })
    }
}
