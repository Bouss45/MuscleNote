package com.musclenote.android.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Alarmes exactes de l'app (SPEC § 7.1), partagées par le minuteur de repos (§ 7.2) et les
 * rappels de compléments (§ 7.3). `RTC_WAKEUP` sur un instant absolu (ms depuis l'époque) :
 * un changement de fuseau ou d'heure d'été ne déplace pas l'alarme.
 *
 * `USE_EXACT_ALARM` (Android 13+) et `SCHEDULE_EXACT_ALARM` (12–12L) sont accordées d'office ;
 * si [AlarmManager.canScheduleExactAlarms] répond malgré tout `false`, repli sur une alarme
 * inexacte `setAndAllowWhileIdle`, qui peut sonner un peu plus tard mais jamais plus tôt.
 */
object ExactAlarms {
    private const val TAG = "ExactAlarms"

    /** Programme (ou remplace, à `PendingIntent` égal) l'alarme [operation] à [triggerAtMillis]. */
    fun schedule(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            return
        }
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarme exacte refusée, repli inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
    }
}
