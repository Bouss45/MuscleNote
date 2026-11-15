package com.musclenote.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musclenote.android.MuscleNoteApplication
import kotlinx.coroutines.launch

/**
 * Sonnerie du rappel de pesée (§ 15.13.4) : l'app relit le réglage et les poids (processus relancé
 * par l'alarme compris), notifie si personne ne s'est pesé ce jour-là, puis programme l'occurrence
 * suivante. La resynchronisation après un redémarrage passe par [ReminderResyncReceiver].
 */
class WeighInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val container = (context.applicationContext as MuscleNoteApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.onWeighInReminderAlarm()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.musclenote.android.action.WEIGH_IN_REMINDER"
    }
}
