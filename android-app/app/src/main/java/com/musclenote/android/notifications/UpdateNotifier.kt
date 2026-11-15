package com.musclenote.android.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.musclenote.android.R
import com.musclenote.android.data.repository.UpdateNotifications
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.update.InstallSource

/**
 * Notification « MuscleNote {version} disponible » (SPEC § 15.14.4, 🔄 D39) sur le canal
 * `updates` : la première ligne des notes, « Voir » (Obtainium s'il a installé l'app, sinon la page
 * de la Release) et « Ignorer cette version ». Un seul identifiant : une nouvelle version remplace
 * celle qui est affichée. Textes dans la langue de l'app au moment de l'affichage.
 */
class UpdateNotifier(context: Context) : UpdateNotifications {
    private val appContext = context.applicationContext

    override fun show(release: LatestRelease): Boolean {
        if (!AppNotifications.areEnabled(appContext, AppChannel.Updates)) return false
        val localized = appContext.withAppLocale()
        val text = release.notes ?: localized.getString(R.string.update_notif_body_default)
        // Ouverte directement par la notification : Android 12+ interdit le relais par un récepteur.
        val view = PendingIntent.getActivity(
            appContext,
            REQUEST_VIEW,
            InstallSource.viewUpdateIntent(appContext, release).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ignore = PendingIntent.getBroadcast(
            appContext,
            REQUEST_IGNORE,
            Intent(appContext, UpdateNotificationReceiver::class.java)
                .setAction(UpdateNotificationReceiver.ACTION_IGNORE)
                .putExtra(UpdateNotificationReceiver.EXTRA_VERSION, release.version),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = AppNotifications.builder(appContext, AppChannel.Updates)
            .setContentTitle(localized.getString(R.string.update_notif_title, release.version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(view)
            .setAutoCancel(true)
            .addAction(0, localized.getString(R.string.update_notif_view), view)
            .addAction(0, localized.getString(R.string.update_notif_ignore), ignore)
            .build()
        AppNotifications.post(appContext, AppNotifications.ID_UPDATE, notification)
        return true
    }

    override fun cancel() = AppNotifications.cancel(appContext, AppNotifications.ID_UPDATE)

    private companion object {
        const val REQUEST_VIEW = 3001
        const val REQUEST_IGNORE = 3002
    }
}
