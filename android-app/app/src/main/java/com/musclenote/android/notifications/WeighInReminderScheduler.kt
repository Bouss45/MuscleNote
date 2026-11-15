package com.musclenote.android.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.musclenote.android.MainActivity
import com.musclenote.android.R
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.nutrition.WeighInReminderDecision
import com.musclenote.android.data.nutrition.WeighInReminderRules
import com.musclenote.android.data.nutrition.WeighInReminders
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Rappel de pesée (SPEC § 15.13.4, 🔄 D38), sur le modèle des rappels de compléments
 * ([SupplementReminderScheduler]) : une alarme exacte ([ExactAlarms], même repli inexact quand le
 * téléphone refuse les alarmes exactes) à la prochaine occurrence d'un jour coché, en heure locale
 * murale, reprogrammée à chaque sonnerie. File sérielle ([queue]) ; chaque opération relit le réglage
 * le plus récent.
 *
 * Identité de l'alarme : l'URI `musclenote://weigh-in-reminder` de son `PendingIntent`.
 */
class WeighInReminderScheduler(context: Context, private val time: TimeSource) : WeighInReminders {
    private val context = context.applicationContext
    private val queue = Mutex()

    override suspend fun sync(latest: () -> WeighInReminder) {
        queue.withLock { schedule(latest()) }
    }

    override suspend fun onAlarm(latest: () -> WeighInReminder, decide: (WeighInReminder, LocalDate) -> WeighInReminderDecision) {
        queue.withLock {
            val reminder = latest()
            val day = time.now().atZone(time.zone()).toLocalDate()
            when (decide(reminder, day)) {
                // Le récepteur se « répare » lui-même : désactivé → rien, pas de reprogrammation.
                WeighInReminderDecision.Disabled -> return
                WeighInReminderDecision.Notify -> AppNotifications.post(context, AppNotifications.ID_WEIGH_IN, notification())
                WeighInReminderDecision.DayNotSelected, WeighInReminderDecision.AlreadyWeighed -> Unit
            }
            schedule(reminder)
        }
    }

    /** Prochaine occurrence strictement après maintenant, ou aucune alarme (désactivé, aucun jour). */
    private fun schedule(reminder: WeighInReminder) {
        val at = WeighInReminderRules.nextAlarm(reminder, time.now(), time.zone())
        if (at == null) {
            ExactAlarms.cancel(context, alarmIntent())
        } else {
            ExactAlarms.schedule(context, at.toInstant().toEpochMilli(), alarmIntent())
        }
    }

    /** « ⚖️ Pesée du jour », dans la langue de l'app au moment de l'affichage ; toucher → « Nouvelle pesée ». */
    private fun notification() = context.withAppLocale().let { localized ->
        AppNotifications.builder(context, AppChannel.WeighIn)
            .setContentTitle(localized.getString(R.string.weigh_in_notif_title))
            .setContentText(localized.getString(R.string.weigh_in_notif_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openWeighIn(context))
            .build()
    }

    private fun alarmIntent(): PendingIntent {
        val intent = Intent(context, WeighInReminderReceiver::class.java)
            .setAction(WeighInReminderReceiver.ACTION_REMINDER)
            .setData(REMINDER_URI)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val REQUEST_OPEN_WEIGH_IN = 5

        /** `musclenote://weigh-in-reminder` : une seule alarme. */
        val REMINDER_URI: Uri = Uri.Builder().scheme("musclenote").authority("weigh-in-reminder").build()

        fun openWeighIn(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_WEIGH_IN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_WEIGH_IN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
