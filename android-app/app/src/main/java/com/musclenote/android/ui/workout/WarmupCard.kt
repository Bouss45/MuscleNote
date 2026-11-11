package com.musclenote.android.ui.workout

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme

private class WarmupItem(@DrawableRes val icon: Int, @StringRes val label: Int, @StringRes val detail: Int)

/** Les 5 éléments de l'échauffement (§ 2.4). */
private val WARMUP_ITEMS = listOf(
    WarmupItem(R.drawable.ic_air, R.string.workout_warmup_cardio_label, R.string.workout_warmup_cardio_detail),
    WarmupItem(R.drawable.ic_rotate_right, R.string.workout_warmup_shoulders_label, R.string.workout_warmup_shoulders_detail),
    WarmupItem(R.drawable.ic_open_with, R.string.workout_warmup_hips_label, R.string.workout_warmup_hips_detail),
    WarmupItem(R.drawable.ic_arrow_downward, R.string.workout_warmup_stretch_label, R.string.workout_warmup_stretch_detail),
    WarmupItem(R.drawable.ic_accessibility_new, R.string.workout_warmup_squat_label, R.string.workout_warmup_squat_detail),
)

/**
 * Carte « Échauffement » (§ 2.4) : 5 éléments cochables, repliable, masquable. État propre à
 * l'écran, non persisté : il survit à un aller-retour vers le sélecteur d'exercices, et repart
 * de zéro à la prochaine ouverture de la séance.
 */
@Composable
fun WarmupCard(modifier: Modifier = Modifier) {
    // Éléments cochés, un bit par élément.
    var checkedMask by rememberSaveable { mutableIntStateOf(0) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return

    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    val doneCount = checkedMask.countOneBits()
    val allDone = doneCount == WARMUP_ITEMS.size
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (allDone) colors.primary.copy(alpha = 0.09f) else colors.card)
            .border(1.dp, if (allDone) colors.primary else colors.border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workout_warmup_title), color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.workout_warmup_count, doneCount, WARMUP_ITEMS.size),
                    color = colors.mutedForeground,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (allDone) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.workout_warmup_done), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { dismissed = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
            }
            Icon(
                painterResource(if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(18.dp),
            )
        }

        // Barre de progression 3 dp.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.muted),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(doneCount.toFloat() / WARMUP_ITEMS.size)
                    .height(3.dp)
                    .background(colors.primary),
            )
        }

        if (!collapsed) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                WARMUP_ITEMS.forEachIndexed { index, item ->
                    val done = (checkedMask and (1 shl index)) != 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.light()
                                checkedMask = checkedMask xor (1 shl index)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val boxShape = RoundedCornerShape(6.dp)
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(boxShape)
                                .background(if (done) colors.primary else Color.Transparent)
                                .border(1.5.dp, if (done) colors.primary else colors.border, boxShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(item.label),
                                color = if (done) colors.mutedForeground else colors.foreground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                modifier = if (done) Modifier.alpha(0.5f) else Modifier,
                            )
                            Text(stringResource(item.detail), color = colors.mutedForeground, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
                        }
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.muted),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(item.icon), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (index < WARMUP_ITEMS.lastIndex) HorizontalDivider(thickness = 1.dp, color = colors.border)
                }
            }
        }
    }
}
