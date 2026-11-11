package com.musclenote.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.AppContainer
import kotlinx.coroutines.launch

/**
 * Récepteur du minuteur de repos (§ 7.2) : alarme de fin — seule source de l'événement « repos
 * terminé », même processus tué — et action « Passer » de la notification en cours.
 */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as MuscleNoteApplication).container
        when (intent.action) {
            ACTION_END -> {
                val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, -1)
                // Lu ici, sur le thread principal : l'app est-elle visible au moment de l'alarme ?
                val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                runAsync(container) { container.onRestTimerAlarm(endsAt, inForeground) }
            }
            ACTION_SKIP -> runAsync(container) { container.skipRest() }
        }
    }

    /**
     * Le dépôt peut encore charger ses données (processus relancé par l'alarme) : travail
     * asynchrone, le système garde le récepteur en vie jusqu'à `finish()`.
     */
    private fun runAsync(container: AppContainer, block: suspend () -> Unit) {
        val pending = goAsync()
        container.appScope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_END = "com.musclenote.android.action.REST_TIMER_END"
        const val ACTION_SKIP = "com.musclenote.android.action.REST_TIMER_SKIP"
        const val EXTRA_ENDS_AT = "endsAt"
    }
}
