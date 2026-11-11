package com.musclenote.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Confirmation en ligne (§ 10.8) : question, indication facultative, puis [Annuler] et une
 * action rouge. [large] : version du pied de page de la séance (textes 15 sp, centrés).
 */
@Composable
fun InlineConfirm(
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    @DrawableRes confirmIcon: Int? = null,
    large: Boolean = false,
) {
    val colors = MuscleNoteTheme.colors
    val textSize = if (large) 15.sp else 13.sp
    val buttonShape = RoundedCornerShape(if (large) 12.dp else 8.dp)
    val buttonPadding = if (large) 13.dp else 8.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                message,
                color = colors.foreground,
                fontSize = textSize,
                fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = if (large) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hint != null) Text(hint, color = colors.mutedForeground, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .border(1.dp, colors.border, buttonShape)
                    .clickable(onClick = onCancel)
                    .padding(vertical = buttonPadding),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = textSize, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .background(colors.destructive)
                    .clickable(onClick = onConfirm)
                    .padding(vertical = buttonPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (confirmIcon != null) {
                    Icon(painterResource(confirmIcon), contentDescription = null, tint = Color.White, modifier = Modifier.size(if (large) 16.dp else 14.dp))
                }
                Text(confirmLabel, color = Color.White, fontSize = textSize, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
