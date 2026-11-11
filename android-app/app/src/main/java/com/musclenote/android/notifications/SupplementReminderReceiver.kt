package com.musclenote.android.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musclenote.android.MuscleNoteApplication
import kotlinx.coroutines.launch

/**
 * Sonnerie d'un rappel de complément (§ 7.3) : le dépôt relit les données (processus relancé par
 * l'alarme compris), notifie puis reprogramme le lendemain — ou ne fait rien si le rappel a
 * disparu.
 */
class SupplementReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val ref = SupplementReminderScheduler.refOf(intent.data) ?: return
        val container = (context.applicationContext as MuscleNoteApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.onSupplementReminderAlarm(ref)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.musclenote.android.action.SUPPLEMENT_REMINDER"
    }
}

/**
 * Resynchronisation complète des rappels (§ 7.3) quand le système a pu perdre ou décaler les
 * alarmes : redémarrage, mise à jour de l'app, changement d'heure ou de fuseau (le jour courant
 * est aussi relu, § 3.12), droit aux alarmes exactes rendu.
 */
class ReminderResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as MuscleNoteApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.resyncReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
