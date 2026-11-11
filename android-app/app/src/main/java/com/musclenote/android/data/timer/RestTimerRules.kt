package com.musclenote.android.data.timer

import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.RestTimerState
import kotlin.math.ceil

/** Couleur du bandeau de repos selon la part restante (§ 7.2). */
enum class RestTimerTone {
    /** Plus de la moitié du repos reste : `success`. */
    Plenty,

    /** Plus du quart : ambre `#F59E0B`. */
    Half,

    /** Le dernier quart : `destructive`. */
    Low,
}

/**
 * Règles du minuteur de repos (SPEC § 3.13, § 7.2), en fonctions pures testables sur la JVM.
 * Tout repose sur l'**instant de fin** `endsAt` (ms depuis l'époque, § 10.10) : le temps restant
 * se recalcule à chaque lecture, l'écran éteint ou le processus tué ne le faussent pas.
 */
object RestTimerRules {
    /** Préréglages proposés dans Paramètres (§ 2.12). */
    val PRESETS = listOf(60, 90, 120, 150, 180, 240, 300)

    /**
     * Un état resté sur disque plus longtemps que ça après sa fin n'attend plus d'alarme (elle a
     * été perdue, par exemple au redémarrage du téléphone) : il est effacé au chargement.
     */
    const val STALE_AFTER_MS = 60 * 60 * 1000L

    /** Nouveau repos de [restSeconds] (> 0, sinon 180 s, § 3.14) à partir de [nowMillis]. */
    fun start(nowMillis: Long, restSeconds: Int, exerciseName: String): RestTimerState {
        val total = if (restSeconds > 0) restSeconds else Preferences.DEFAULT_REST_SECONDS
        return RestTimerState(endsAt = nowMillis + total * 1000L, total = total, exerciseName = exerciseName)
    }

    /** Le repos est fini dès que `maintenant ≥ endsAt` : le bandeau disparaît. */
    fun isFinished(state: RestTimerState, nowMillis: Long): Boolean = nowMillis >= state.endsAt

    fun remainingMillis(state: RestTimerState, nowMillis: Long): Long = (state.endsAt - nowMillis).coerceAtLeast(0)

    /**
     * Secondes restantes, arrondies au-dessus : le compte à rebours affiche `3:00` au départ et
     * `0:01` pendant la dernière seconde, puis le repos est fini.
     */
    fun secondsLeft(state: RestTimerState, nowMillis: Long): Int = ceil(remainingMillis(state, nowMillis) / 1000.0).toInt()

    /** Part restante du repos, entre 0 et 1 (barre de progression et couleur du bandeau). */
    fun remainingFraction(state: RestTimerState, nowMillis: Long): Double =
        if (state.total <= 0) 0.0 else (secondsLeft(state, nowMillis).toDouble() / state.total).coerceIn(0.0, 1.0)

    /** > 50 % → [RestTimerTone.Plenty] ; > 25 % → [RestTimerTone.Half] ; sinon [RestTimerTone.Low]. */
    fun tone(remainingFraction: Double): RestTimerTone = when {
        remainingFraction > 0.5 -> RestTimerTone.Plenty
        remainingFraction > 0.25 -> RestTimerTone.Half
        else -> RestTimerTone.Low
    }

    /** `m:ss`, minutes non bornées. */
    fun formatClock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** État illisible (durée nulle) ou fini depuis plus de [STALE_AFTER_MS]. */
    fun isStale(state: RestTimerState, nowMillis: Long): Boolean =
        state.total <= 0 || nowMillis - state.endsAt > STALE_AFTER_MS
}
