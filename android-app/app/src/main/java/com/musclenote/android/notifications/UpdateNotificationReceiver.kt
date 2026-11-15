package com.musclenote.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musclenote.android.MuscleNoteApplication
import kotlinx.coroutines.launch

/**
 * « Ignorer cette version » de la notification de mise à jour (§ 15.14.4) : la notification part
 * tout de suite, puis la version est enregistrée comme ignorée (ni notification ni annonce de
 * l'Accueil pour elle).
 */
class UpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_IGNORE) return
        val version = intent.getStringExtra(EXTRA_VERSION) ?: return
        AppNotifications.cancel(context, AppNotifications.ID_UPDATE)
        val container = (context.applicationContext as MuscleNoteApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.awaitReady()
                container.updates.dismiss(version)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_IGNORE = "com.musclenote.android.action.IGNORE_UPDATE"
        const val EXTRA_VERSION = "version"
    }
}
