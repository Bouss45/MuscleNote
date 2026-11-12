package com.musclenote.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.musclenote.android.backup.SafBackupFolders
import com.musclenote.android.backup.WorkManagerAutoBackupScheduler
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.crash.CrashReports
import com.musclenote.android.data.feedback.FeedbackRules
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.feedback.DiscordWebhookSender
import com.musclenote.android.health.AndroidHealthConnectGateway
import com.musclenote.android.notifications.NotificationChannels
import com.musclenote.android.notifications.RestTimerNotifier
import com.musclenote.android.notifications.SupplementReminderScheduler
import com.musclenote.android.update.GitHubReleaseSource
import com.musclenote.android.update.InstallSource
import java.io.File

class MuscleNoteApplication : Application() {

    /** Données de l'app, créées une fois par processus (SPEC § 4.2). */
    lateinit var container: AppContainer
        private set

    /**
     * Langue choisie dans l'app d'après les préférences locales (`null` = système, ou données pas
     * encore chargées) : avant Android 13, relais d'AppCompat hors activité (§ 9.4).
     */
    fun storedAppLanguage(): String? =
        if (::container.isInitialized) container.preferences.localPreferences.value.appLanguage else null

    /** Rapports de plantage locaux (§ 10.9), partagés ou supprimés depuis Paramètres (§ 2.12). */
    lateinit var crashReports: CrashReports
        private set

    override fun onCreate() {
        super.onCreate()
        // En premier : un plantage pendant la création des données laisse aussi un rapport.
        crashReports = CrashReports(File(filesDir, CrashReports.DIR_NAME))
        CrashHandler.install(crashReports) {
            if (::container.isInitialized) container.workouts.flushPendingWrites()
        }
        container = AppContainer(
            File(filesDir, "data"),
            restTimerEffects = RestTimerNotifier(this),
            supplementReminders = SupplementReminderScheduler(this, TimeSource.System),
            currentVersion = BuildConfig.VERSION_NAME,
            releaseSource = GitHubReleaseSource(BuildConfig.VERSION_NAME),
            // Installée par Obtainium : c'est lui qui suit les Releases (§ 3.17).
            updateChecksEnabled = !InstallSource.isManagedByUpdater(this),
            // Envoi d'un avis (§ 14) : seulement si la CI a fourni l'URL du webhook.
            feedbackSender = BuildConfig.FEEDBACK_WEBHOOK_URL
                .takeIf(FeedbackRules::isWebhookUrl)
                ?.let { DiscordWebhookSender(it, BuildConfig.VERSION_NAME) },
            // Sauvegarde automatique (§ 15.2) : dossier choisi par l'utilisateur, WorkManager.
            backupFolders = SafBackupFolders(this),
            autoBackupScheduler = WorkManagerAutoBackupScheduler(this),
            // Health Connect (§ 15.3) : disponibilité relue à chaque utilisation.
            healthConnect = AndroidHealthConnectGateway(this),
        )
        NotificationChannels.createOrUpdate(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = container.onAppBackground()
            },
        )
    }
}
