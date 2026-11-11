package com.musclenote.android.data.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Jour courant (§ 3.12) : minuterie jusqu'au prochain minuit local, relecture à la demande. */
class CurrentDayTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    @Test
    fun `la minuterie vise le prochain minuit local, jours de changement d'heure compris`() {
        // 22 h à Paris (UTC+1) → 2 h avant minuit.
        assertEquals(Duration.ofHours(2).plusMillis(50), CurrentDay.delayUntilNextDay(Instant.parse("2026-03-14T21:00:00Z"), paris))
        // Dimanche 29 mars, jour de 23 h : de 0 h à minuit le lendemain.
        assertEquals(Duration.ofHours(23).plusMillis(50), CurrentDay.delayUntilNextDay(Instant.parse("2026-03-28T23:00:00Z"), paris))
        // Dimanche 25 octobre, jour de 25 h.
        assertEquals(Duration.ofHours(25).plusMillis(50), CurrentDay.delayUntilNextDay(Instant.parse("2026-10-24T22:00:00Z"), paris))
    }

    @Test
    fun `refresh relit le jour, par exemple après un changement de fuseau`() {
        var zone = paris
        val time = object : TimeSource {
            override fun now(): Instant = Instant.parse("2026-03-14T23:30:00Z")
            override fun zone(): ZoneId = zone
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val day = CurrentDay(time, scope)
            assertEquals(LocalDate.parse("2026-03-15"), day.today.value) // 0 h 30 à Paris
            zone = ZoneId.of("America/New_York")
            day.refresh()
            assertEquals(LocalDate.parse("2026-03-14"), day.today.value) // 19 h 30 à New York
        } finally {
            scope.cancel()
        }
    }
}
