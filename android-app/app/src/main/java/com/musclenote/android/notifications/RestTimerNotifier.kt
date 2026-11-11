package com.musclenote.android.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.musclenote.android.MainActivity
import com.musclenote.android.R
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.repository.RestTimerEffects

/**
 * Effets système du minuteur de repos (SPEC § 7.2, 🔄 D6) : notification en cours avec compte à
 * rebours géré par le système, alarme exacte de fin, puis vibration (app au premier plan) ou
 * notification « Repos terminé » (arrière-plan). Pas de service de premier plan : rien n'est mis
 * à jour périodiquement.
 */
class RestTimerNotifier(context: Context) : RestTimerEffects {
    private val context = context.applicationContext

    override fun show(state: RestTimerState) {
        // Un ancien « Repos terminé » n'a plus lieu d'être pendant un nouveau repos.
        AppNotifications.cancel(context, AppNotifications.ID_REST_TIMER_FINISHED)
        ExactAlarms.schedule(context, state.endsAt, endAlarm(state.endsAt))
        AppNotifications.post(context, AppNotifications.ID_REST_TIMER_ONGOING, ongoingNotification(state))
    }

    override fun cancel() {
        ExactAlarms.cancel(context, endAlarm(endsAt = 0))
        AppNotifications.cancel(context, AppNotifications.ID_REST_TIMER_ONGOING)
    }

    override fun finished(state: RestTimerState, inForeground: Boolean) {
        AppNotifications.cancel(context, AppNotifications.ID_REST_TIMER_ONGOING)
        if (inForeground) {
            vibrateSuccess()
        } else {
            AppNotifications.post(context, AppNotifications.ID_REST_TIMER_FINISHED, finishedNotification(state))
        }
    }

    /** « Repos en cours » : compte à rebours système jusqu'à `endsAt`, action « Passer ». */
    private fun ongoingNotification(state: RestTimerState) = context.withAppLocale().let { localized ->
        val remaining = (state.endsAt - System.currentTimeMillis()).coerceAtLeast(1)
        AppNotifications.builder(context, AppChannel.RestTimerOngoing)
            .setContentTitle(localized.getString(R.string.rest_timer_ongoing_title))
            .setContentText(localized.getString(R.string.rest_timer_ongoing_text, state.exerciseName))
            .setWhen(state.endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(remaining)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openWorkout(context))
            .addAction(R.drawable.ic_skip_next, localized.getString(R.string.rest_timer_skip), skipAction())
            .build()
    }

    /** « Repos terminé 💪 » : son et vibration du canal, effacée au toucher. */
    private fun finishedNotification(state: RestTimerState) = context.withAppLocale().let { localized ->
        AppNotifications.builder(context, AppChannel.RestTimer)
            .setContentTitle(localized.getString(R.string.rest_timer_notif_title))
            .setContentText(localized.getString(R.string.rest_timer_notif_body, state.exerciseName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openWorkout(context))
            .build()
    }

    /**
     * Alarme de fin : un seul `PendingIntent` (même requête, extras ignorés pour l'égalité), donc
     * une seule alarme active, remplacée à chaque démarrage ; `endsAt` permet au récepteur
     * d'ignorer une alarme dépassée.
     */
    private fun endAlarm(endsAt: Long): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java)
            .setAction(RestTimerReceiver.ACTION_END)
            .putExtra(RestTimerReceiver.EXTRA_ENDS_AT, endsAt)
        return PendingIntent.getBroadcast(context, REQUEST_END, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun skipAction(): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_SKIP)
        return PendingIntent.getBroadcast(context, REQUEST_SKIP, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Vibration « succès » de fin de repos, app au premier plan (pas de notification). */
    private fun vibrateSuccess() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createWaveform(SUCCESS_PATTERN, -1))
    }

    companion object {
        private const val REQUEST_END = 1
        private const val REQUEST_SKIP = 2
        private const val REQUEST_OPEN_WORKOUT = 3

        /** Deux impulsions courtes, assez nettes pour être senties téléphone posé. */
        private val SUCCESS_PATTERN = longArrayOf(0, 90, 90, 90)

        /** Toucher une notification du minuteur → écran de la séance en cours (§ 7.2). */
        fun openWorkout(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_WORKOUT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_WORKOUT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
