package com.musclenote.android.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.timer.RestTimerRules
import com.musclenote.android.data.timer.RestTimerTone
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.ui.theme.FixedColors
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.delay

/**
 * Bandeau du repos en cours (SPEC § 7.2), au pied de l'écran de séance : « Repos », compte à
 * rebours `m:ss` recalculé depuis `endsAt`, barre de progression (restant / total) et « Passer ».
 * Vert, puis ambre, puis rouge selon la part restante. Rien quand aucun repos ne court.
 */
@Composable
fun RestTimerBanner(state: RestTimerState?, time: TimeSource, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    if (state == null) return
    // Recalculé à chaque changement de seconde affichée, jusqu'à la fin du repos.
    val now by produceState(time.now().toEpochMilli(), state) {
        while (true) {
            value = time.now().toEpochMilli()
            val remaining = state.endsAt - value
            if (remaining <= 0) break
            delay(if (remaining % 1000 == 0L) 1000 else remaining % 1000)
        }
    }
    if (RestTimerRules.isFinished(state, now)) return

    val colors = MuscleNoteTheme.colors
    val fraction = RestTimerRules.remainingFraction(state, now)
    val tint = when (RestTimerRules.tone(fraction)) {
        RestTimerTone.Plenty -> colors.success
        RestTimerTone.Half -> FixedColors.restTimerAmber
        RestTimerTone.Low -> colors.destructive
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.13f))
            .border(1.dp, tint.copy(alpha = 0.33f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.rest_timer_label), color = tint, fontSize = 12.sp)
            Text(
                RestTimerRules.formatClock(RestTimerRules.secondsLeft(state, now)),
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        ProgressTrack(fraction.toFloat(), tint, Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.rest_timer_skip), color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProgressTrack(fraction: Float, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .height(4.dp)
            .clip(shape)
            .background(MuscleNoteTheme.colors.border),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(tint),
        )
    }
}
