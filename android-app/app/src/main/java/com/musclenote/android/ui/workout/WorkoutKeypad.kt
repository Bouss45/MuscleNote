package com.musclenote.android.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.KeypadHeader
import com.musclenote.android.ui.designsystem.components.KeypadPanel
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import java.util.Locale

/** Champ en saisie au pavé : une valeur d'une série de la séance en cours (§ 15.7.4). */
@Immutable
data class EntryTarget(val exerciseId: String, val setId: String, val field: SetEntryField)

/**
 * Saisie au pavé numérique (§ 15.7.4) : le champ ouvert, son texte tel que tapé (`62,` reste
 * visible) et s'il est « neuf » (la prochaine touche remplace la valeur). La série elle-même est
 * modifiée à chaque touche par l'écran (🔄 D20) ; cet état ne garde que l'affichage.
 */
@Stable
class SetEntryState(target: EntryTarget? = null, text: String = "", fresh: Boolean = true) {
    var target by mutableStateOf(target)
        private set
    var text by mutableStateOf(text)
        private set
    var fresh by mutableStateOf(fresh)
        private set

    /** Ouvre [target], neuf, avec sa valeur [value] affichée. */
    fun open(target: EntryTarget, value: String) {
        this.target = target
        text = value
        fresh = true
    }

    fun close() {
        target = null
    }

    /** Texte après une touche acceptée. */
    fun typed(next: String) {
        text = next
        fresh = false
    }

    /** Valeur changée d'un coup (pas, recopie de « Préc. », suggestion) : le champ redevient neuf. */
    fun reset(value: String) {
        text = value
        fresh = true
    }

    companion object {
        val Saver = listSaver<SetEntryState, Any?>(
            save = { listOf(it.target?.exerciseId, it.target?.setId, it.target?.field?.name, it.text, it.fresh) },
            restore = { saved ->
                val exerciseId = saved[0] as String?
                val setId = saved[1] as String?
                val field = (saved[2] as String?)?.let { name -> SetEntryField.entries.firstOrNull { it.name == name } }
                val target = if (exerciseId != null && setId != null && field != null) EntryTarget(exerciseId, setId, field) else null
                SetEntryState(target, saved[3] as String, saved[4] as Boolean)
            },
        )
    }
}

/** Libellé court d'un champ : colonne, segment du pavé, unité du champ héros. */
@Composable
internal fun fieldLabel(field: SetEntryField): String = stringResource(
    when (field) {
        SetEntryField.Weight -> R.string.workout_col_kg
        SetEntryField.Reps -> R.string.workout_col_reps
        SetEntryField.Duration -> R.string.workout_col_min
        SetEntryField.Distance -> R.string.workout_col_km
        SetEntryField.Speed -> R.string.workout_keypad_speed
        SetEntryField.Incline -> R.string.workout_keypad_incline
    },
)

/**
 * Pavé de la séance en cours (§ 15.7.4), en bas de l'écran : segmenté des champs de la série
 * (« Kg · série 3 » / « Reps »), supprimer, fermer ; vitesse et inclinaison (absentes des colonnes)
 * dans un champ héros ; touches, pas et touche principale (« Suivant », « Valider la série », « OK »).
 */
@Composable
internal fun WorkoutKeypad(
    set: WorkoutSet,
    setNumber: Int,
    isCardio: Boolean,
    entry: SetEntryState,
    field: SetEntryField,
    locale: Locale,
    bottomInset: Dp,
    onSelectField: (SetEntryField) -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onStep: (direction: Int) -> Unit,
    onAction: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
) {
    val fields = SetEntryRules.fields(isCardio)
    val labels = fields.mapIndexed { index, f ->
        if (index == 0) stringResource(R.string.workout_keypad_first, fieldLabel(f), setNumber) else fieldLabel(f)
    }
    val step = SetEntryRules.format(field.step, field, locale)
    val last = SetEntryRules.next(field, isCardio) == null
    KeypadPanel(bottomInset = bottomInset) {
        KeypadHeader(
            options = labels,
            selectedIndex = fields.indexOf(field),
            onSelect = { onSelectField(fields[it]) },
            onHide = onHide,
            onDelete = onDelete,
            deleteDescription = stringResource(R.string.workout_delete_set),
        )
        if (field == SetEntryField.Speed || field == SetEntryField.Incline) {
            FormHeroField(entry.text, fieldLabel(field))
        }
        NumericKeypad(
            onDigit = onDigit,
            onDelete = onBackspace,
            stepDownLabel = "$MINUS$step",
            onStepDown = { onStep(-1) },
            stepUpLabel = "+$step",
            onStepUp = { onStep(+1) },
            actionLabel = stringResource(
                when {
                    !last -> R.string.workout_keypad_next
                    set.completed -> R.string.workout_keypad_ok
                    else -> R.string.workout_notif_complete_set
                },
            ),
            onAction = onAction,
            actionIcon = if (last && !set.completed) R.drawable.ds_ic_check else null,
            decimalSeparator = SetEntryRules.decimalSeparator(locale),
        )
    }
}
