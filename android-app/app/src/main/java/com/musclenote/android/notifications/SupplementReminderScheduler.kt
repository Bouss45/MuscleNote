package com.musclenote.android.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.musclenote.android.MainActivity
import com.musclenote.android.R
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.nutrition.ReminderAlarm
import com.musclenote.android.data.nutrition.ReminderRef
import com.musclenote.android.data.nutrition.ReminderSchedule
import com.musclenote.android.data.nutrition.SupplementReminders
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rappels de compléments (SPEC § 7.3, 🔄 D7) : une alarme exacte par rappel, à sa prochaine
 * occurrence en heure locale murale, reprogrammée au lendemain à chaque sonnerie (`AlarmManager`
 * n'a pas de répétition exacte). Toutes les opérations passent par une **file sérielle**
 * ([queue]) et relisent la liste la plus récente.
 *
 * Identité d'une alarme : l'URI `musclenote://reminder/{supplementId}/{reminderId}` de son
 * `PendingIntent` ; l'annuler ne demande aucun id système stocké.
 */
class SupplementReminderScheduler(context: Context, private val time: TimeSource) : SupplementReminders {
    private val context = context.applicationContext
    private val queue = Mutex()

    override suspend fun sync(previous: List<Supplement>, latest: () -> List<Supplement>) {
        queue.withLock {
            val current = latest()
            for (ref in ReminderSchedule.toCancel(previous, current)) {
                ExactAlarms.cancel(context, alarmIntent(ref))
            }
            for (alarm in ReminderSchedule.expectedAlarms(current)) schedule(alarm)
        }
    }

    override suspend fun onAlarm(ref: ReminderRef, latest: () -> List<Supplement>) {
        queue.withLock {
            // Le récepteur se « répare » lui-même : rappel disparu ou désactivé → rien.
            val (supplement, alarm) = ReminderSchedule.alarmFor(ref, latest()) ?: return
            AppNotifications.post(context, notificationTag(supplement.id), AppNotifications.ID_SUPPLEMENT, notification(supplement))
            schedule(alarm)
        }
    }

    /** Alarme à la prochaine occurrence (strictement après maintenant : demain si elle sonne). */
    private fun schedule(alarm: ReminderAlarm) {
        val at = ReminderSchedule.nextOccurrence(alarm.hour, alarm.minute, time.now(), time.zone())
        ExactAlarms.schedule(context, at.toInstant().toEpochMilli(), alarmIntent(alarm.ref))
    }

    /**
     * « 💊 {nom} » + « N'oublie pas ta dose ({dosage} {unité}) ! » (sans parenthèses si le dosage
     * est vide), dans la langue de l'app au moment de l'affichage ; toucher → onglet Nutrition.
     */
    private fun notification(supplement: Supplement) = context.withAppLocale().let { localized ->
        val text = if (supplement.dosage.isNotBlank()) {
            localized.getString(R.string.nutrition_notif_body_dosage, supplement.dosage, supplement.unit)
        } else {
            localized.getString(R.string.nutrition_notif_body)
        }
        AppNotifications.builder(context, AppChannel.Supplements)
            .setContentTitle("💊 ${supplement.name}")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openNutrition(context))
            .build()
    }

    private fun alarmIntent(ref: ReminderRef): PendingIntent {
        val intent = Intent(context, SupplementReminderReceiver::class.java)
            .setAction(SupplementReminderReceiver.ACTION_REMINDER)
            .setData(reminderUri(ref))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val SCHEME = "musclenote"
        private const val HOST = "reminder"
        private const val REQUEST_OPEN_NUTRITION = 4

        /** `musclenote://reminder/{supplementId}/{reminderId}` (ids encodés : chaînes opaques). */
        fun reminderUri(ref: ReminderRef): Uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(ref.supplementId)
            .appendPath(ref.reminderId)
            .build()

        fun refOf(uri: Uri?): ReminderRef? {
            if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
            val segments = uri.pathSegments
            if (segments.size != 2) return null
            return ReminderRef(segments[0], segments[1])
        }

        /** Une notification par complément : une nouvelle sonnerie remplace la précédente. */
        fun notificationTag(supplementId: String) = "supplement:$supplementId"

        fun openNutrition(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NUTRITION)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_NUTRITION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
