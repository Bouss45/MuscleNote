package com.musclenote.android.data.repository

import com.musclenote.android.data.model.SetTimerState
import com.musclenote.android.data.storage.JsonDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le chrono d'une série au temps (SPEC § 15.18.4, 🔄 D44) : **un seul** à la fois, indépendant des
 * écrans, gardé dans `set_timer.json` (propre à l'appareil, hors sauvegarde) pour continuer écran
 * éteint, app fermée ou téléphone redémarré. Seul l'instant de départ est stocké. Les règles (série
 * encore là, non validée ; durée reportée) sont dans `SetTimerRules`, l'enchaînement dans
 * `AppContainer`.
 */
class SetTimerRepository(private val document: JsonDocument<SetTimerState>) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SetTimerState?>(null)

    /** Chrono en cours ; `null` sinon. */
    val state: StateFlow<SetTimerState?> = _state.asStateFlow()

    suspend fun load() {
        mutex.withLock { _state.value = document.read() }
    }

    /** Démarre le chrono de [setId] à [startedAt] (ms) ; sans effet si un chrono tourne déjà. */
    suspend fun start(setId: String, startedAt: Long): Boolean = mutex.withLock {
        if (_state.value != null) return@withLock false
        val next = SetTimerState(setId, startedAt)
        _state.value = next
        document.saveNow(next)
        true
    }

    /** Retire le chrono [expected] (ou n'importe lequel) ; renvoie celui qui tournait. */
    suspend fun clear(expected: SetTimerState? = null): SetTimerState? = mutex.withLock {
        val current = _state.value ?: return@withLock null
        if (expected != null && current != expected) return@withLock null
        _state.value = null
        document.saveNow(null)
        current
    }
}
