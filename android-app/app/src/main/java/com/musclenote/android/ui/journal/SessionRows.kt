package com.musclenote.android.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.journal.JournalRules
import com.musclenote.android.data.journal.SessionSummary
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.buildLocalDate
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.util.parseIntegerOrNull
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.components.statusHelp
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.workout.ConfirmSheet
import java.time.Instant
import java.time.ZoneId

/*
 * Ligne d'une séance terminée et ses actions (SPEC § 15.8.4), communes au Journal et à la section
 * « Dernière séance » de l'Accueil : toucher → résumé (§ 15.12), appui long → modifier, changer la
 * date, supprimer. Les actions servent aussi au ⋮ du résumé.
 */

/** Durée d'une ligne : « 45 min », « 1 h », « 1 h 05 » (en anglais « 1 h 05 min »). */
@Composable
internal fun durationText(minutes: Int): String {
    val duration = JournalRules.duration(minutes)
    return when {
        duration.hours == 0 -> stringResource(R.string.duration_minutes, duration.minutes)
        duration.minutes == 0 -> stringResource(R.string.duration_hours, duration.hours)
        else -> stringResource(R.string.duration_hours_minutes, duration.hours, duration.minutes)
    }
}

/** Durée courte, pour une rangée de stats : « 1 h 05 » (« 1h 05m »), « 45 min », « 1 h ». */
@Composable
internal fun compactDurationText(minutes: Int): String {
    val duration = JournalRules.duration(minutes)
    return if (duration.hours > 0 && duration.minutes > 0) {
        stringResource(R.string.duration_compact, duration.hours, duration.minutes)
    } else {
        durationText(minutes)
    }
}

/**
 * Ligne d'une séance : nom affiché ; « mer. 11 nov. · 1 h 12 » ; volume (« 6 840 kg ») ou, sans
 * volume, le nombre de séries validées.
 */
@Composable
internal fun SessionRow(
    summary: SessionSummary,
    dates: DateFormats,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val date = summary.start?.let { dates.weekdayDayMonth(it.toInstant()) }
    val duration = summary.durationMinutes?.let { durationText(it) }
    val subtitle = if (date != null && duration != null) stringResource(R.string.session_subtitle, date, duration) else date ?: duration
    val value = if (summary.showsVolume) {
        stringResource(R.string.session_volume_kg, formatInteger(summary.volumeKg.toDouble()))
    } else {
        pluralStringResource(R.plurals.common_sets_count, summary.completedSets, summary.completedSets)
    }
    ListRow(
        title = workoutDisplayName(summary.workout),
        modifier = modifier,
        subtitle = subtitle,
        trailing = ListRowTrailing.Value(value),
        showDivider = showDivider,
        onClick = onOpen,
        onLongClick = onLongPress,
        onLongClickLabel = stringResource(R.string.session_long_press),
    )
}

/** Quelle sheet d'action est ouverte, et pour quelle séance (survit à une recréation). */
@Stable
internal class SessionActionsState {
    /** Sheet des actions (appui long). */
    var actionsFor by mutableStateOf<String?>(null)

    /** Confirmation de suppression. */
    var deleteFor by mutableStateOf<String?>(null)

    /** Formulaire « Modifier la date ». */
    var dateFor by mutableStateOf<String?>(null)

    companion object {
        val Saver = listSaver<SessionActionsState, String?>(
            save = { listOf(it.actionsFor, it.deleteFor, it.dateFor) },
            restore = { saved ->
                SessionActionsState().apply {
                    actionsFor = saved[0]
                    deleteFor = saved[1]
                    dateFor = saved[2]
                }
            },
        )
    }
}

@Composable
internal fun rememberSessionActions(): SessionActionsState = rememberSaveable(saver = SessionActionsState.Saver) { SessionActionsState() }

/**
 * Les sheets d'action d'une séance de [workouts] : actions (modifier → [onEdit], date,
 * suppression), confirmation de suppression, formulaire de date. Une séance disparue ferme sa sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionActionSheets(state: SessionActionsState, workouts: List<Workout>, onEdit: (workoutId: String) -> Unit) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val haptics = rememberHaptics()
    val zone = container.time.zone()
    val dates = rememberDateFormats(zone)
    fun find(id: String?) = id?.let { wanted -> workouts.firstOrNull { it.id == wanted } }

    find(state.actionsFor)?.let { workout ->
        val start = parseInstantOrNull(workout.date, zone)
        StudioBottomSheet(onDismissRequest = { state.actionsFor = null }) { inset ->
            SessionActionsContent(
                name = workoutDisplayName(workout),
                dateLine = start?.let { stringResource(R.string.session_subtitle, dates.weekdayDayMonth(it), dates.time(it)) },
                onEdit = {
                    state.actionsFor = null
                    onEdit(workout.id)
                },
                onEditDate = {
                    state.actionsFor = null
                    state.dateFor = workout.id
                },
                onDelete = {
                    state.actionsFor = null
                    state.deleteFor = workout.id
                },
                onClose = { state.actionsFor = null },
                bottomInset = inset,
            )
        }
    }

    find(state.deleteFor)?.let { workout ->
        val date = parseInstantOrNull(workout.date, zone)?.let { dates.weekdayDayMonth(it) }.orEmpty()
        ConfirmSheet(
            title = stringResource(R.string.session_delete_title),
            message = stringResource(R.string.session_delete_message, workoutDisplayName(workout), date),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.warning()
                state.deleteFor = null
                container.launchMutation { repository.deleteWorkout(workout.id) }
            },
            onDismiss = { state.deleteFor = null },
        )
    }

    find(state.dateFor)?.let { workout ->
        WorkoutDateSheet(
            workout = workout,
            zone = zone,
            onDismiss = { state.dateFor = null },
            onSave = { instant ->
                state.dateFor = null
                container.launchMutation { repository.updateWorkoutDate(workout.id, instant) }
            },
        )
    }
}

/** Contenu de la sheet d'actions : nom, « mer. 11 nov. · 18:30 », modifier, date, supprimer, fermer. */
@Composable
internal fun SessionActionsContent(
    name: String,
    dateLine: String?,
    onEdit: () -> Unit,
    onEditDate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp,
) {
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(name, dateLine?.let { AnnotatedString(it) })
        StudioButton(stringResource(R.string.session_actions_edit), onEdit, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        StudioButton(stringResource(R.string.home_edit_date), onEditDate, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        StudioButton(stringResource(R.string.session_actions_delete), onDelete, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        StudioButton(stringResource(R.string.ds_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/**
 * « Modifier la date » (§ 15.8.4, règles du § 2.2) : Jour / Mois / Année puis Heure / Min,
 * préremplis avec la date locale de la séance. Heure ou minute vide → 12 h / 00. Une date
 * impossible (§ 10.3) remplace la phrase d'aide par « Date ou heure invalide », avec une vibration
 * d'erreur, et laisse la sheet ouverte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDateSheet(workout: Workout, zone: ZoneId, onDismiss: () -> Unit, onSave: (Instant) -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        WorkoutDateForm(workout, zone, onDismiss, onSave, bottomInset = inset)
    }
}

/** Contenu du formulaire « Modifier la date » (gabarit formulaire), avec son état. */
@Composable
internal fun WorkoutDateForm(
    workout: Workout,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Instant) -> Unit,
    bottomInset: Dp,
    initiallyInvalid: Boolean = false,
) {
    val haptics = rememberHaptics()
    val start = remember(workout.id) { parseInstantOrNull(workout.date, zone)?.atZone(zone) }
    var day by rememberSaveable(workout.id) { mutableStateOf(start?.dayOfMonth?.toString().orEmpty()) }
    var month by rememberSaveable(workout.id) { mutableStateOf(start?.monthValue?.toString().orEmpty()) }
    var year by rememberSaveable(workout.id) { mutableStateOf(start?.year?.toString().orEmpty()) }
    var hour by rememberSaveable(workout.id) { mutableStateOf(start?.hour?.toString()?.padStart(2, '0').orEmpty()) }
    var minute by rememberSaveable(workout.id) { mutableStateOf(start?.minute?.toString()?.padStart(2, '0').orEmpty()) }
    var invalid by rememberSaveable(workout.id) { mutableStateOf(initiallyInvalid) }

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

    val name = workoutDisplayName(workout)
    val error = stringResource(R.string.date_invalid)
    FormSheetTemplate(
        title = stringResource(R.string.home_edit_date),
        help = if (invalid) {
            statusHelp(error)
        } else {
            AnnotatedString(name)
        },
        modifier = Modifier.imePadding(),
        bottomInset = bottomInset,
        field = {
            Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
                    DateField(day, { day = it }, R.string.home_date_day, R.string.date_day, maxLength = 2)
                    DateField(month, { month = it }, R.string.home_date_month, R.string.date_month, maxLength = 2)
                    DateField(year, { year = it }, R.string.home_date_year, R.string.date_year, maxLength = 4, weight = 2f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
                    DateField(hour, { hour = it }, R.string.home_date_hour, R.string.date_hh, maxLength = 2)
                    DateField(minute, { minute = it }, R.string.home_date_minute, R.string.date_mm, maxLength = 2, onDone = ::save)
                    Spacer(Modifier.weight(2f))
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            StudioButton(stringResource(R.string.common_save), ::save, Modifier.fillMaxWidth())
            StudioButton(stringResource(R.string.common_cancel), onDismiss, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
        }
    }
}

/** Un champ numérique de la date : libellé au-dessus, chiffres seulement, [maxLength] au plus. */
@Composable
private fun RowScope.DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    placeholder: Int,
    maxLength: Int,
    weight: Float = 1f,
    onDone: (() -> Unit)? = null,
) {
    Column(Modifier.weight(weight), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
        Text(stringResource(label), style = Studio.typography.caption, color = Studio.colors.textSecondary)
        StudioTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(maxLength)) },
            placeholder = stringResource(placeholder),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        )
    }
}
