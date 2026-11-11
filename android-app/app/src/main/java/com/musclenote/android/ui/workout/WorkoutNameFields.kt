package com.musclenote.android.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Longueur maximale d'un nom de séance personnalisé (§ 2.3). */
internal const val CUSTOM_NAME_MAX_LENGTH = 30

/**
 * Choix du nom d'une séance, partagé par « Démarrer une séance » (§ 2.3, étape 1) et le
 * renommage dans l'éditeur (§ 2.9) : trois groupes de puces de préréglages, puis le groupe
 * « Personnalisé », boîte qui devient un champ texte (focus automatique) une fois touchée.
 * Choisir une puce désélectionne le nom personnalisé ; c'est à l'appelant de vider le champ.
 */
@Composable
fun WorkoutNameFields(
    preset: PresetId?,
    customSelected: Boolean,
    customName: String,
    onSelectPreset: (PresetId) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    chipBackground: Color = MuscleNoteTheme.colors.card,
) {
    val colors = MuscleNoteTheme.colors
    Column(modifier) {
        for (group in PresetGroup.entries) {
            GroupLabel(stringResource(group.labelRes()))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                for (candidate in PresetId.entries.filter { it.group == group }) {
                    val active = candidate == preset
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .clip(shape)
                            .background(if (active) colors.primary else chipBackground)
                            .border(1.5.dp, if (active) colors.primary else colors.border, shape)
                            .clickable { onSelectPreset(candidate) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(
                            stringResource(candidate.labelRes),
                            color = if (active) colors.primaryForeground else colors.foreground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        GroupLabel(stringResource(R.string.start_workout_custom_label))
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (customSelected) colors.primary.copy(alpha = 0.08f) else chipBackground)
                .border(1.5.dp, if (customSelected) colors.primary else colors.border, shape)
                .clickable(enabled = !customSelected, onClick = onSelectCustom)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_edit),
                contentDescription = null,
                tint = if (customSelected) colors.primary else colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
            if (customSelected) {
                val focusRequester = remember { FocusRequester() }
                BasicTextField(
                    value = customName,
                    onValueChange = { onCustomNameChange(it.take(CUSTOM_NAME_MAX_LENGTH)) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (customName.isEmpty()) {
                                Text(stringResource(R.string.start_workout_custom_placeholder), color = colors.mutedForeground, fontSize = 15.sp)
                            }
                            inner()
                        }
                    },
                )
                LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            } else {
                Text(
                    stringResource(R.string.start_workout_custom_empty),
                    color = colors.mutedForeground,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(appLocale()),
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

private fun PresetGroup.labelRes(): Int = when (this) {
    PresetGroup.Ppl -> R.string.start_workout_group_ppl
    PresetGroup.Split -> R.string.start_workout_group_split
    PresetGroup.Other -> R.string.start_workout_group_other
}
