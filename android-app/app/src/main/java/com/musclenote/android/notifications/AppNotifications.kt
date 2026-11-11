package com.musclenote.android.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.musclenote.android.R

/**
 * Publication des notifications de l'app (SPEC § 7.1) : petite icône blanche, accent `#9B1C1C`,
 * et aucune exception si l'utilisateur a refusé les notifications.
 */
object AppNotifications {
    private const val TAG = "AppNotifications"

    /** Couleur d'accent des notifications (`primary`, identique dans les deux thèmes). */
    const val ACCENT_COLOR = 0xFF9B1C1C.toInt()

    /** Identifiants des notifications de l'app ; les rappels de compléments viendront ensuite. */
    const val ID_REST_TIMER_ONGOING = 1001
    const val ID_REST_TIMER_FINISHED = 1002

    /** Builder commun : canal, petite icône, couleur d'accent. */
    fun builder(context: Context, channel: AppChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT_COLOR)

    /**
     * `POST_NOTIFICATIONS` à demander avant de démarrer une séance (§ 2.3) : Android 13+ et pas
     * encore accordée. Si l'utilisateur l'a refusée deux fois, Android ne montre plus la demande et
     * répond tout de suite « refusé ».
     */
    fun needsPermissionRequest(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(context)

    /** Notifications visibles : permission accordée et notifications de l'app activées. */
    fun areEnabled(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Affiche (ou remplace) la notification [id] ; ne fait rien si les notifications sont refusées. */
    @SuppressLint("MissingPermission") // vérifiée juste avant par hasPermission
    fun post(context: Context, id: Int, notification: Notification) {
        if (!hasPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification $id refusée", e)
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
