package com.musclenote.android.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.workout.WorkoutNotificationRules
import kotlinx.coroutines.launch

/**
 * Récepteur du minuteur de repos (§ 7.2) — alarme de fin, seule source de l'événement « repos
 * terminé », même processus tué — et des actions de la notification de séance et de l'alerte de
 * fin de repos (§ 15.5) : « Valider la série », « −30 s », « +30 s », « Passer ». Chaque action
 * attend le chargement des données (processus relancé) et republie la notification avant de
 * rendre la main.
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
            ACTION_COMPLETE_SET -> runAsync(container) { container.completeNextSet() }
            ACTION_REST_MINUS -> runAsync(container) { container.adjustRest(-WorkoutNotificationRules.ADJUST_SECONDS) }
            ACTION_REST_PLUS -> runAsync(container) { container.adjustRest(WorkoutNotificationRules.ADJUST_SECONDS) }
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
        const val ACTION_COMPLETE_SET = "com.musclenote.android.action.WORKOUT_COMPLETE_SET"
        const val ACTION_REST_MINUS = "com.musclenote.android.action.REST_TIMER_MINUS"
        const val ACTION_REST_PLUS = "com.musclenote.android.action.REST_TIMER_PLUS"
        const val EXTRA_ENDS_AT = "endsAt"

        /** Code de requête par action : un `PendingIntent` distinct pour chacune. */
        private val REQUEST_CODES = mapOf(
            ACTION_SKIP to 2,
            ACTION_COMPLETE_SET to 4,
            ACTION_REST_MINUS to 5,
            ACTION_REST_PLUS to 6,
        )

        /** `PendingIntent` d'une action de notification ([ACTION_SKIP], [ACTION_COMPLETE_SET]…). */
        fun action(context: Context, action: String): PendingIntent {
            val intent = Intent(context, RestTimerReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODES.getValue(action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
