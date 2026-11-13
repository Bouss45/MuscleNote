package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val FieldMinHeight = 48.dp
private val FieldPaddingH = 16.dp
private val FieldPaddingV = 14.dp

/**
 * Champ texte — `StudioTextField` (DESIGN.md § 9.2) : texte libre au clavier du système (note
 * d'un exercice). Rayon 12, padding 16 / 14, `body`, plusieurs lignes ; au repos sur
 * `surfaceControl`, en saisie sur `liveDim` avec le curseur `liveText` (le jaune marque la saisie) ;
 * invite en texte secondaire.
 */
@Composable
fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 5,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = Studio.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .background(if (focused) colors.liveDim else colors.surfaceControl, Studio.shapes.control),
        textStyle = Studio.typography.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.liveText),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = maxLines,
        interactionSource = source,
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = FieldPaddingH, vertical = FieldPaddingV)) {
                if (value.isEmpty()) Text(placeholder, style = Studio.typography.body, color = colors.textSecondary)
                inner()
            }
        },
    )
}
