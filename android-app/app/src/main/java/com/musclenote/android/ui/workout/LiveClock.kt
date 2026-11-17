package com.musclenote.android.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.SetTimerRules
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * Horloges de la séance en cours, partagées par l'écran de séance, la surface « En cours » de
 * l'Accueil et la mini-barre (SPEC § 15.7.1, § 15.8.1, § 15.8.2).
 */

/** Heure courante en ms, rafraîchie à chaque seconde pleine. */
@Composable
internal fun rememberSecondTicker(time: TimeSource): Long {
    val now by produceState(time.now().toEpochMilli()) {
        while (true) {
            value = time.now().toEpochMilli()
            delay(1_000 - value % 1_000)
        }
    }
    return now
}

/** Le repos est en cours : vrai jusqu'à `endsAt`, sans relire l'heure chaque seconde. */
@Composable
internal fun rememberRestRunning(rest: RestTimerState?, time: TimeSource): Boolean {
    val running by produceState(rest != null && time.now().toEpochMilli() < rest.endsAt, rest) {
        if (rest == null) {
            value = false
            return@produceState
        }
        val remaining = rest.endsAt - time.now().toEpochMilli()
        value = remaining > 0
        if (remaining > 0) {
            delay(remaining)
            value = false
        }
    }
    return running
}

/**
 * Secondes écoulées du chrono d'une série au temps (§ 15.18.4), recalculées à chaque seconde pleine
 * depuis son départ ; 0 sans chrono.
 */
@Composable
internal fun rememberHoldSeconds(timer: SetTimerState?, time: TimeSource): Int {
    val seconds by produceState(timer?.let { SetTimerRules.elapsedSeconds(it, time.now().toEpochMilli()) } ?: 0, timer) {
        if (timer == null) {
            value = 0
            return@produceState
        }
        while (true) {
            val now = time.now().toEpochMilli()
            value = SetTimerRules.elapsedSeconds(timer, now)
            delay(1_000 - (now - timer.startedAt).mod(1_000L))
        }
    }
    return seconds
}

/** Durée `m:ss`, minutes non bornées (« 75:03 »). */
internal fun clock(seconds: Long): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

/**
 * Temps de la séance en cours à l'instant : chrono depuis son début ([elapsed]) et, pendant un
 * repos, le temps restant ([rest], arrondi au-dessus : « 0:01 » jusqu'à la fin).
 */
internal data class LiveTimes(val elapsed: String, val rest: String?)

/** [LiveTimes] recalculés à chaque seconde. */
@Composable
internal fun rememberLiveTimes(workout: Workout, rest: RestTimerState?, time: TimeSource): LiveTimes {
    val now = rememberSecondTicker(time)
    val start = remember(workout.date) { parseInstantOrNull(workout.date, time.zone())?.toEpochMilli() }
    val elapsed = if (start == null) 0L else ((now - start) / 1_000L).coerceAtLeast(0L)
    val remainingMs = rest?.let { it.endsAt - now }?.takeIf { it > 0 }
    return LiveTimes(clock(elapsed), remainingMs?.let { clock((it + 999L) / 1_000L) })
}
