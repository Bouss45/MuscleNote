package com.musclenote.android.data.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * « Jour courant » local de l'app (SPEC § 3.12) : recalculé au prochain minuit local (minuterie),
 * à chaque retour au premier plan et au changement de fuseau ([refresh]). La minuterie peut
 * prendre du retard quand le téléphone dort : le retour au premier plan rattrape.
 */
class CurrentDay(private val time: TimeSource, private val scope: CoroutineScope) {
    private val _today = MutableStateFlow(time.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val lock = Any()
    private var timer: Job? = null

    init {
        refresh()
    }

    /** Relit le jour et relance la minuterie jusqu'au prochain minuit (fuseau relu). */
    fun refresh() {
        _today.value = time.today()
        synchronized(lock) {
            timer?.cancel()
            timer = scope.launch {
                while (true) {
                    delay(delayUntilNextDay(time.now(), time.zone()).toMillis())
                    _today.value = time.today()
                }
            }
        }
    }

    companion object {
        /** Marge après minuit, pour lire sûrement le nouveau jour. */
        private const val MARGIN_MS = 50L

        /**
         * Durée jusqu'au début du jour local suivant ([zone]) : 23 h ou 25 h les jours de
         * changement d'heure.
         */
        fun delayUntilNextDay(now: Instant, zone: ZoneId): Duration {
            val nextDay = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            return Duration.between(now, nextDay).plusMillis(MARGIN_MS)
        }
    }
}
