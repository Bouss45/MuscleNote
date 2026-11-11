package com.musclenote.android.data.timer

import com.musclenote.android.data.model.RestTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure du minuteur de repos (§ 3.13, § 7.2). */
class RestTimerRulesTest {

    private val start = 1_700_000_000_000L

    /** Repos de 3 min démarré à [start]. */
    private val rest = RestTimerRules.start(start, 180, "Squat")

    @Test
    fun `un repos finit restSeconds après son démarrage`() {
        assertEquals(RestTimerState(endsAt = start + 180_000, total = 180, exerciseName = "Squat"), rest)
        assertEquals(start + 60_000, RestTimerRules.start(start, 60, "Dips").endsAt)
    }

    @Test
    fun `une durée nulle ou négative prend la durée par défaut de 180 s`() {
        assertEquals(180, RestTimerRules.start(start, 0, "Squat").total)
        assertEquals(start + 180_000, RestTimerRules.start(start, -5, "Squat").endsAt)
    }

    @Test
    fun `le repos est fini dès que maintenant atteint la fin`() {
        assertFalse(RestTimerRules.isFinished(rest, start))
        assertFalse(RestTimerRules.isFinished(rest, start + 179_999))
        assertTrue(RestTimerRules.isFinished(rest, start + 180_000))
        assertTrue(RestTimerRules.isFinished(rest, start + 500_000))
    }

    @Test
    fun `le temps restant est arrondi à la seconde supérieure et jamais négatif`() {
        assertEquals(180, RestTimerRules.secondsLeft(rest, start))
        assertEquals(180, RestTimerRules.secondsLeft(rest, start + 400))
        assertEquals(179, RestTimerRules.secondsLeft(rest, start + 1_000))
        assertEquals(1, RestTimerRules.secondsLeft(rest, start + 179_001))
        assertEquals(0, RestTimerRules.secondsLeft(rest, start + 180_000))
        assertEquals(0, RestTimerRules.secondsLeft(rest, start + 999_999))
        assertEquals(30_000L, RestTimerRules.remainingMillis(rest, start + 150_000))
        assertEquals(0L, RestTimerRules.remainingMillis(rest, start + 200_000))
    }

    @Test
    fun `la part restante va de 1 à 0`() {
        assertEquals(1.0, RestTimerRules.remainingFraction(rest, start), 0.0)
        assertEquals(0.5, RestTimerRules.remainingFraction(rest, start + 90_000), 1e-9)
        assertEquals(0.0, RestTimerRules.remainingFraction(rest, start + 180_000), 0.0)
        assertEquals(0.0, RestTimerRules.remainingFraction(rest.copy(total = 0), start), 0.0)
    }

    @Test
    fun `la couleur du bandeau suit la part restante`() {
        assertEquals(RestTimerTone.Plenty, RestTimerRules.tone(1.0))
        assertEquals(RestTimerTone.Plenty, RestTimerRules.tone(0.51))
        // Strictement plus de 50 % pour le vert, plus de 25 % pour l'ambre.
        assertEquals(RestTimerTone.Half, RestTimerRules.tone(0.5))
        assertEquals(RestTimerTone.Half, RestTimerRules.tone(0.26))
        assertEquals(RestTimerTone.Low, RestTimerRules.tone(0.25))
        assertEquals(RestTimerTone.Low, RestTimerRules.tone(0.0))
    }

    @Test
    fun `la couleur change au fil d'un repos de 3 min`() {
        fun toneAt(elapsedSeconds: Int) = RestTimerRules.tone(RestTimerRules.remainingFraction(rest, start + elapsedSeconds * 1000L))
        assertEquals(RestTimerTone.Plenty, toneAt(0))
        assertEquals(RestTimerTone.Plenty, toneAt(89)) // 91 s restantes
        assertEquals(RestTimerTone.Half, toneAt(90)) // 90 s = 50 %
        assertEquals(RestTimerTone.Half, toneAt(134)) // 46 s
        assertEquals(RestTimerTone.Low, toneAt(135)) // 45 s = 25 %
        assertEquals(RestTimerTone.Low, toneAt(179))
    }

    @Test
    fun `le compte à rebours s'affiche en m ss`() {
        assertEquals("3:00", RestTimerRules.formatClock(180))
        assertEquals("1:05", RestTimerRules.formatClock(65))
        assertEquals("0:09", RestTimerRules.formatClock(9))
        assertEquals("0:00", RestTimerRules.formatClock(0))
        assertEquals("0:00", RestTimerRules.formatClock(-3))
        assertEquals("75:03", RestTimerRules.formatClock(4_503))
    }

    @Test
    fun `un état fini depuis plus d'une heure, ou sans durée, est périmé`() {
        assertFalse(RestTimerRules.isStale(rest, start + 180_000))
        assertFalse(RestTimerRules.isStale(rest, start + 180_000 + RestTimerRules.STALE_AFTER_MS))
        assertTrue(RestTimerRules.isStale(rest, start + 180_001 + RestTimerRules.STALE_AFTER_MS))
        assertTrue(RestTimerRules.isStale(rest.copy(total = 0), start))
    }
}
