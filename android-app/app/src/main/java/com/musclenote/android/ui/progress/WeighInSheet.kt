package com.musclenote.android.ui.progress

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.progress.DayValue
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.MINUS_SIGN
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.theme.Studio
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Pas des touches − / + de la pesée (§ 15.9.3). */
private const val WEIGH_IN_STEP = 0.1

/**
 * « Nouvelle pesée » (SPEC § 15.9.2) : formulaire en bottom sheet au pavé numérique. Le poids part
 * du dernier poids connu ([lastWeight], champ neuf) ; la date d'aujourd'hui, « Hier », ou une autre
 * par le sélecteur de date du système. « Ajouter » enregistre si `0 < poids ≤ 500` (§ 3.11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeighInSheet(
    today: LocalDate,
    lastWeight: DayValue?,
    dates: DateFormats,
    onDismiss: () -> Unit,
    onSave: (day: LocalDate, weight: Double) -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        WeighInForm(today, lastWeight, dates, onSave, bottomInset = inset)
    }
}

/** Contenu du formulaire « Nouvelle pesée », avec son état. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeighInForm(
    today: LocalDate,
    lastWeight: DayValue?,
    dates: DateFormats,
    onSave: (day: LocalDate, weight: Double) -> Unit,
    bottomInset: Dp,
    initiallyInvalid: Boolean = false,
) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val field = SetEntryField.Weight
    var text by rememberSaveable { mutableStateOf(lastWeight?.let { SetEntryRules.format(it.value, field, locale) }.orEmpty()) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var epochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var invalid by rememberSaveable { mutableStateOf(initiallyInvalid) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.ofEpochDay(epochDay)
    val yesterday = today.minusDays(1)

    fun edit(next: String?, nextFresh: Boolean = false) {
        if (next == null) return
        text = next
        fresh = nextFresh
        invalid = false
    }

    fun step(direction: Int) {
        SetEntryRules.step(parseDecimal(text), direction, field, locale, step = WEIGH_IN_STEP)?.let {
            haptics.light()
            edit(SetEntryRules.format(it, field, locale), nextFresh = true)
        }
    }

    fun add() {
        val weight = parseDecimal(text)
        if (BodyWeightRules.isValidWeight(weight)) {
            haptics.success()
            onSave(day, weight)
        } else {
            invalid = true
            haptics.warning()
        }
    }

    val separator = SetEntryRules.decimalSeparator(locale)
    val stepLabel = "0${separator}1"
    val error = stringResource(R.string.body_weight_invalid_entry)
    val errorColor = Studio.colors.textPrimary
    FormSheetTemplate(
        title = stringResource(R.string.body_weight_new_entry),
        help = when {
            invalid -> buildAnnotatedString { withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) { append(error) } }
            lastWeight != null -> AnnotatedString(
                stringResource(R.string.weigh_in_help_last, formatNumber(lastWeight.value), dates.shortDay(lastWeight.day, today)),
            )
            else -> AnnotatedString(stringResource(R.string.weigh_in_help_none))
        },
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = "kg") },
        quickEntries = {
            FilterChip(stringResource(R.string.weigh_in_today), selected = day == today, onClick = { epochDay = today.toEpochDay() })
            FilterChip(stringResource(R.string.weigh_in_yesterday), selected = day == yesterday, onClick = { epochDay = yesterday.toEpochDay() })
        },
        info = {
            InfoRow(
                label = stringResource(R.string.body_weight_date_label),
                value = dates.weekdayDay(day, today),
                onClick = { picking = true },
                showDivider = false,
            )
        },
    ) {
        NumericKeypad(
            onDigit = { key -> edit(SetEntryRules.type(text, fresh, key, field, locale)) },
            onDelete = { edit(SetEntryRules.backspace(text, fresh)) },
            stepDownLabel = "$MINUS_SIGN$stepLabel",
            onStepDown = { step(-1) },
            stepUpLabel = "+$stepLabel",
            onStepUp = { step(+1) },
            actionLabel = stringResource(R.string.weigh_in_add),
            onAction = ::add,
            actionIcon = R.drawable.ds_ic_check,
            decimalSeparator = separator,
        )
    }

    if (picking) {
        // Sélecteur du système, en UTC : il manipule des jours, pas des instants.
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                StudioButton(
                    stringResource(R.string.workout_keypad_ok),
                    onClick = {
                        state.selectedDateMillis?.let { epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                        picking = false
                    },
                    variant = ButtonVariant.Text,
                )
            },
            dismissButton = {
                StudioButton(stringResource(R.string.common_cancel), onClick = { picking = false }, variant = ButtonVariant.Text)
            },
        ) {
            DatePicker(state)
        }
    }
}
