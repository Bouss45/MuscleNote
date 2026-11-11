package com.musclenote.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.musclenote.android.R

/**
 * Canaux de notification de l'app (SPEC § 7.1). Les identifiants sont figés : Android garde les
 * réglages de l'utilisateur par canal.
 */
enum class AppChannel(
    val id: String,
    @param:StringRes val nameRes: Int,
    val importance: Int,
    /** Son et vibration par défaut ; sinon canal silencieux. */
    val alerting: Boolean,
    val lockscreenVisibility: Int,
) {
    /** Notification en cours du repos (🔄 D6) : silencieuse, compte à rebours système. */
    RestTimerOngoing(
        "rest-timer-ongoing",
        R.string.channel_rest_timer_ongoing,
        NotificationManager.IMPORTANCE_LOW,
        alerting = false,
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
    ),

    /** Fin du repos, app en arrière-plan. */
    RestTimer(
        "rest-timer",
        R.string.channel_rest_timer,
        NotificationManager.IMPORTANCE_HIGH,
        alerting = true,
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
    ),

    /** Rappels de compléments (§ 7.3). */
    Supplements(
        "supplements",
        R.string.channel_supplements,
        NotificationManager.IMPORTANCE_HIGH,
        alerting = true,
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE,
    ),
}

object NotificationChannels {
    /** Vibration des canaux qui alertent : `[0, 250, 250, 250]` ms. */
    private val VIBRATION_PATTERN = longArrayOf(0, 250, 250, 250)

    /**
     * Crée les canaux, ou met à jour leur nom dans la langue courante de l'app : appelé au
     * démarrage de l'app et à chaque création de l'activité (donc après un changement de langue).
     * L'importance d'un canal existant n'est jamais modifiée par Android ; seuls nom et
     * description le sont.
     */
    fun createOrUpdate(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = context.withAppLocale()
        val channels = AppChannel.entries.map { channel ->
            NotificationChannel(channel.id, localized.getString(channel.nameRes), channel.importance).apply {
                lockscreenVisibility = channel.lockscreenVisibility
                setShowBadge(false)
                if (channel.alerting) {
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                } else {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
        }
        manager.createNotificationChannels(channels)
    }
}

/**
 * Contexte dans la langue de l'app (§ 9.4) : les textes construits hors d'une activité (récepteurs,
 * canaux) doivent suivre le choix FR / EN fait via AppCompat, pas seulement la langue du système.
 * Avant Android 13, AppCompat ne connaît ce choix qu'une fois une activité créée dans le
 * processus : dans un processus lancé par une alarme, le texte suit alors la langue du système.
 */
fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    }
    return createConfigurationContext(configuration)
}
