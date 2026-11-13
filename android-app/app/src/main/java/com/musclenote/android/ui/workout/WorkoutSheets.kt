package com.musclenote.android.ui.workout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ConfirmSheetContent
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioTextField

/** Un exercice de l'échauffement (§ 2.4). */
internal class WarmupItem(@StringRes val label: Int, @StringRes val detail: Int)

/** Les 5 exercices de l'échauffement (§ 2.4), cochés à l'écran sans être enregistrés. */
internal val WARMUP_ITEMS = listOf(
    WarmupItem(R.string.workout_warmup_cardio_label, R.string.workout_warmup_cardio_detail),
    WarmupItem(R.string.workout_warmup_shoulders_label, R.string.workout_warmup_shoulders_detail),
    WarmupItem(R.string.workout_warmup_hips_label, R.string.workout_warmup_hips_detail),
    WarmupItem(R.string.workout_warmup_stretch_label, R.string.workout_warmup_stretch_detail),
    WarmupItem(R.string.workout_warmup_squat_label, R.string.workout_warmup_squat_detail),
)

/** Exercices cochés de l'échauffement, en masque de bits. */
internal fun Int.isWarmupDone(index: Int): Boolean = this and (1 shl index) != 0

internal fun Int.warmupCount(): Int = Integer.bitCount(this)

/** Ligne « Échauffement » en tête de la séance (§ 15.7.1) : « 2/5 exercices » ou « Fait ! ». */
@Composable
internal fun WarmupRow(done: Int, onClick: () -> Unit) {
    val count = done.warmupCount()
    ListRow(
        title = stringResource(R.string.workout_warmup_title),
        subtitle = if (count == WARMUP_ITEMS.size) {
            stringResource(R.string.workout_warmup_done)
        } else {
            stringResource(R.string.workout_warmup_count, count, WARMUP_ITEMS.size)
        },
        trailing = ListRowTrailing.Chevron,
        showDivider = false,
        onClick = onClick,
    )
}

/** Bottom sheet de l'échauffement : les 5 exercices à cocher. */
@Composable
internal fun WarmupSheetContent(done: Int, onToggle: (Int) -> Unit, onClose: () -> Unit, bottomInset: Dp) {
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(
            stringResource(R.string.workout_warmup_title),
            AnnotatedString(stringResource(R.string.workout_warmup_count, done.warmupCount(), WARMUP_ITEMS.size)),
        )
        Column {
            WARMUP_ITEMS.forEachIndexed { index, item ->
                ListRow(
                    title = stringResource(item.label),
                    subtitle = stringResource(item.detail),
                    trailing = ListRowTrailing.Check(done.isWarmupDone(index)),
                    showDivider = false,
                    onClick = { onToggle(index) },
                )
            }
        }
        StudioButton(stringResource(R.string.ds_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/**
 * Options d'un exercice (menu ⋮, § 15.7.1) : note enregistrée à chaque frappe, « Retirer de la
 * séance » (confirmation à part).
 */
@Composable
internal fun ExerciseOptionsSheetContent(
    name: String,
    note: String,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp,
) {
    var text by rememberSaveable { mutableStateOf(note) }
    SheetSurface(Modifier.imePadding(), bottomInset = bottomInset) {
        SheetTitle(name, null)
        StudioTextField(
            value = text,
            onValueChange = {
                text = it
                onNoteChange(it)
            },
            placeholder = stringResource(R.string.exercise_card_note_placeholder),
        )
        StudioButton(stringResource(R.string.workout_exercise_remove), onRemove, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        StudioButton(stringResource(R.string.ds_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/** Confirmation en bottom sheet (DESIGN.md § 13) : bouton principal inverse, jamais rouge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        ConfirmSheetContent(title, message, confirmLabel, onConfirm, dismissLabel, onDismiss, bottomInset = inset)
    }
}
