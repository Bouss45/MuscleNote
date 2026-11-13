package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val KeyHeight = 52.dp
private val KeyGap = 6.dp
private val PanelPadding = 12.dp
private val PanelGap = 10.dp
private val ActionIconSize = 22.dp
private val ActionIconGap = 6.dp

/**
 * Pavé numérique — `NumericKeypad` (DESIGN.md § 9.2) : 4 colonnes de touches de 52. Chiffres,
 * virgule et 0 sur `surfaceControl` ; pas − / + et effacer sur `surfacePressed` ; la touche
 * principale (deux colonnes, inverse) reprend l'action de l'écran.
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    stepDownLabel: String,
    onStepDown: () -> Unit,
    stepUpLabel: String,
    onStepUp: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes actionIcon: Int? = null,
    decimalSeparator: Char = ',',
    deleteDescription: String = stringResource(R.string.ds_keypad_delete),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        KeyRow {
            DigitKey('1', onDigit); DigitKey('2', onDigit); DigitKey('3', onDigit)
            StepKey(stepDownLabel, onStepDown)
        }
        KeyRow {
            DigitKey('4', onDigit); DigitKey('5', onDigit); DigitKey('6', onDigit)
            StepKey(stepUpLabel, onStepUp)
        }
        KeyRow {
            DigitKey('7', onDigit); DigitKey('8', onDigit); DigitKey('9', onDigit)
            Key(Studio.colors.surfacePressed, onDelete, Modifier.weight(1f), deleteDescription) {
                StudioIcon(R.drawable.ds_ic_backspace, contentDescription = null, tint = Studio.colors.textPrimary)
            }
        }
        KeyRow {
            // Deux moitiés : la touche principale couvre deux colonnes et l'écart qui les sépare.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                DigitKey(decimalSeparator, onDigit); DigitKey('0', onDigit)
            }
            Key(Studio.colors.inverse, onAction, Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ActionIconGap)) {
                    if (actionIcon != null) {
                        StudioIcon(actionIcon, contentDescription = null, size = ActionIconSize, tint = Studio.colors.onInverse)
                    }
                    Text(actionLabel, style = Studio.typography.action, color = Studio.colors.onInverse, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), content = content)
}

@Composable
private fun RowScope.DigitKey(char: Char, onDigit: (Char) -> Unit) {
    Key(Studio.colors.surfaceControl, { onDigit(char) }, Modifier.weight(1f), char.toString()) {
        Text(char.toString(), style = Studio.typography.numberKey, color = Studio.colors.textPrimary)
    }
}

@Composable
private fun RowScope.StepKey(label: String, onClick: () -> Unit) {
    Key(Studio.colors.surfacePressed, onClick, Modifier.weight(1f)) {
        Text(label, style = Studio.typography.action, color = Studio.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun Key(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val pressedColor = if (color == Studio.colors.surfaceControl) Studio.colors.surfacePressed else color.copy(alpha = 0.8f)
    val background by animateColorAsState(if (pressed) pressedColor else color, Studio.motion.fast.spec(), label = "key")
    Box(
        modifier
            .height(KeyHeight)
            .background(background, Studio.shapes.control)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * En-tête du pavé : segmenté compact (« Kg · série 3 » / « Reps ») et bouton « fermer le pavé ».
 */
@Composable
fun KeypadHeader(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    hideDescription: String = stringResource(R.string.ds_keypad_hide),
) {
    Row(
        modifier.fillMaxWidth().padding(start = Studio.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SegmentedControl(options, selectedIndex, onSelect, compact = true)
        StudioIconButton(
            R.drawable.ds_ic_keyboard_hide,
            contentDescription = hideDescription,
            onClick = onHide,
            size = ButtonSize.Small,
            tone = ButtonTone.Subtle,
        )
    }
}

/**
 * Panneau du pavé : `surfaceRaised`, padding 12, éléments espacés de 10. Posé en bas de l'écran
 * en direct (coins du haut ; en bas, [bottomInset] + 4) ou seul (rayon 20 partout, 12 en bas).
 */
@Composable
fun KeypadPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Studio.shapes.sheet,
    bottomInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Studio.colors.surfaceRaised, shape)
            .padding(
                start = PanelPadding,
                end = PanelPadding,
                top = PanelPadding,
                bottom = if (bottomInset > 0.dp) bottomInset + Studio.spacing.xxs else PanelPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
        content = content,
    )
}
