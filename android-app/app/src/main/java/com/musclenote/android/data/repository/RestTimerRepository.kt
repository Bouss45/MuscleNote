package com.musclenote.android.data.repository

import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.timer.RestTimerRules
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Effets système du minuteur (notifications, alarme, vibration), séparés du dépôt pour que sa
 * logique reste testable sur la JVM. Appelés sous le verrou du dépôt, dans l'ordre des mutations.
 */
interface RestTimerEffects {
    /** Repos démarré ou repris : notification en cours et alarme de fin (remplacent les précédentes). */
    fun show(state: RestTimerState)

    /** Repos arrêté : alarme et notification en cours annulées. */
    fun cancel()

    /** Fin du repos : notification en cours retirée ; vibration au premier plan, sinon notification. */
    fun finished(state: RestTimerState, inForeground: Boolean)

    /** Aucun effet (tests, et tant que l'app n'a pas fourni les siens). */
    object None : RestTimerEffects {
        override fun show(state: RestTimerState) = Unit
        override fun cancel() = Unit
        override fun finished(state: RestTimerState, inForeground: Boolean) = Unit
    }
}

/**
 * Le minuteur de repos de l'app (SPEC § 7.2) : **un seul** repos à la fois, indépendant des
 * écrans, persisté dans `rest_timer.json` pour survivre à la mort du processus.
 *
 * L'alarme de fin est la **seule** source de l'événement « repos terminé » ([finish]) : un état
 * fini mais récent reste donc chargé jusqu'à ce qu'elle passe (processus relancé par l'alarme
 * elle-même). L'interface, elle, considère le repos terminé dès `maintenant ≥ endsAt`.
 */
class RestTimerRepository(
    private val document: JsonDocument<RestTimerState>,
    private val time: TimeSource,
    private val restSeconds: () -> Int,
    private val effects: RestTimerEffects,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<RestTimerState?>(null)

    /** Repos en cours, ou fini dont l'alarme n'est pas encore passée ; `null` sinon. */
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    /**
     * Relit l'état. Un repos encore en cours retrouve sa notification et son alarme (perdues au
     * redémarrage du téléphone) ; un état périmé est effacé sans alerte.
     */
    suspend fun load() {
        mutex.withLock {
            val stored = document.read()
            val now = now()
            when {
                stored == null -> _state.value = null
                RestTimerRules.isStale(stored, now) -> {
                    _state.value = null
                    document.saveNow(null)
                }
                else -> {
                    _state.value = stored
                    if (!RestTimerRules.isFinished(stored, now)) effects.show(stored)
                }
            }
        }
    }

    /** Démarre un repos de `restSeconds` (préférence du moment) ; remplace le repos en cours. */
    suspend fun start(exerciseName: String) {
        mutex.withLock {
            val next = RestTimerRules.start(now(), restSeconds(), exerciseName)
            _state.value = next
            document.saveNow(next)
            effects.show(next)
        }
    }

    /**
     * Republie la notification du repos en cours (alarme comprise, à l'identique) : après un
     * changement de langue, son texte suit la nouvelle langue.
     */
    suspend fun refreshNotification() {
        mutex.withLock {
            val current = _state.value ?: return
            if (!RestTimerRules.isFinished(current, now())) effects.show(current)
        }
    }

    /** « Passer » (bandeau ou notification), fin ou abandon de la séance : tout est annulé. */
    suspend fun stop() {
        mutex.withLock {
            _state.value = null
            document.saveNow(null)
            effects.cancel()
        }
    }

    /**
     * Alarme de fin (récepteur). Sans effet si le repos n'est plus celui qui finit à [endsAt]
     * (arrêté, ou remplacé par un nouveau repos pendant que l'alarme était en route). Renvoie
     * `true` si le repos a été terminé.
     */
    suspend fun finish(endsAt: Long, inForeground: Boolean): Boolean {
        return mutex.withLock {
            val current = _state.value
            if (current == null || current.endsAt != endsAt) return false
            _state.value = null
            document.saveNow(null)
            effects.finished(current, inForeground)
            true
        }
    }

    private fun now(): Long = time.now().toEpochMilli()
}
