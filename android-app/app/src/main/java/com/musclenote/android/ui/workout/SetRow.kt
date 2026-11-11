package com.musclenote.android.ui.workout

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
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
import com.musclenote.android.R
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInteger
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Champ d'une ligne de série : longueur maximale, clavier, et lecture de la saisie dans la série. */
private enum class SetField(val maxLength: Int, val keyboard: KeyboardType, val unit: String) {
    Reps(3, KeyboardType.Number, "reps"),
    Weight(6, KeyboardType.Decimal, "kg"),
    Duration(4, KeyboardType.Decimal, "min"),
    Distance(5, KeyboardType.Decimal, "km"),
    Speed(4, KeyboardType.Decimal, "km/h"),
    Incline(4, KeyboardType.Decimal, "% inc.");

    fun withText(set: WorkoutSet, text: String): WorkoutSet = when (this) {
        Reps -> set.copy(reps = parseInteger(text))
        Weight -> set.copy(weight = parseDecimal(text))
        Duration -> set.copy(duration = parseDecimal(text))
        Distance -> set.copy(distance = parseDecimal(text))
        Speed -> set.copy(speed = parseDecimal(text))
        Incline -> set.copy(incline = parseDecimal(text))
    }

    fun read(set: WorkoutSet): Double? = when (this) {
        Reps -> set.reps.toDouble()
        Weight -> set.weight
        Duration -> set.duration
        Distance -> set.distance
        Speed -> set.speed
        Incline -> set.incline
    }
}

private val STRENGTH_FIELDS = listOf(SetField.Reps, SetField.Weight)
private val CARDIO_FIELDS = listOf(SetField.Duration, SetField.Distance, SetField.Speed, SetField.Incline)

/**
 * Ligne de série (SPEC § 2.6) : numéro, champs force (`reps × kg`) ou cardio (grille 2 × 2),
 * case de validation et poubelle. Chaque frappe est appliquée tout de suite par [onUpdate]
 * (🔄 D20) ; le texte brut reste affiché pendant la saisie (`62,` reste visible).
 */
@Composable
fun SetRow(
    set: WorkoutSet,
    index: Int,
    isCardio: Boolean,
    onToggle: () -> Unit,
    onUpdate: ((WorkoutSet) -> WorkoutSet) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val fields = if (isCardio) CARDIO_FIELDS else STRENGTH_FIELDS
    val requesters = remember(isCardio) { fields.map { FocusRequester() } }

    @Composable
    fun input(position: Int) {
        val field = fields[position]
        // Clé explicite : l'état sauvegardé de chaque champ doit avoir sa propre clé.
        key(field) {
            SetNumberField(
                field = field,
                initial = field.read(set),
                focusRequester = requesters[position],
                next = requesters.getOrNull(position + 1),
                onTextChange = { text -> onUpdate { field.withText(it, text) } },
            )
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (set.completed) colors.primary.copy(alpha = 0.09f) else colors.card)
            .border(1.dp, if (set.completed) colors.primary.copy(alpha = 0.27f) else colors.border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (index + 1).toString(),
            color = colors.mutedForeground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp),
        )

        if (isCardio) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (line in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (column in 0..1) {
                            val position = line * 2 + column
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                input(position)
                                UnitLabel(fields[position].unit)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    input(0)
                    UnitLabel(SetField.Reps.unit)
                }
                Text("×", color = colors.mutedForeground, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    input(1)
                    UnitLabel(SetField.Weight.unit)
                }
            }
        }

        // Case de validation.
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (set.completed) colors.primary else Color.Transparent)
                .border(1.5.dp, if (set.completed) colors.primary else colors.border, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (set.completed) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
            }
        }

        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.common_delete),
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun UnitLabel(text: String) {
    Text(text, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 11.sp, maxLines = 1)
}

/**
 * Champ numérique d'une série (🔄 D20) : 0 affiché vide avec le placeholder `0`, tout le texte
 * sélectionné au focus, « Suivant » vers le champ suivant de la ligne et « OK » sur le dernier.
 */
@Composable
private fun SetNumberField(
    field: SetField,
    initial: Double?,
    focusRequester: FocusRequester,
    next: FocusRequester?,
    onTextChange: (String) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = when {
            initial == null || initial == 0.0 -> ""
            field == SetField.Reps -> initial.toInt().toString()
            else -> formatDecimal(initial, locale)
        }
        mutableStateOf(TextFieldValue(text))
    }
    var focused by remember { mutableStateOf(false) }
    // Le toucher place le curseur après la prise de focus : la sélection est faite juste après.
    LaunchedEffect(focused) {
        if (focused) value = value.copy(selection = TextRange(0, value.text.length))
    }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(8.dp)

    BasicTextField(
        value = value,
        onValueChange = { changed ->
            if (changed.text.length <= field.maxLength) {
                val textChanged = changed.text != value.text
                value = changed
                if (textChanged) onTextChange(changed.text)
            }
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = colors.foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = field.keyboard,
            imeAction = if (next != null) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { next?.requestFocus() },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = Modifier
            .width(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(shape)
                    .background(colors.input)
                    .border(1.dp, colors.border, shape)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.text.isEmpty()) {
                    Text("0", color = colors.mutedForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                inner()
            }
        },
    )
}
