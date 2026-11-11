package com.musclenote.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.crash.CrashReports
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.notifications.NotificationChannels
import com.musclenote.android.notifications.RestTimerNotifier
import com.musclenote.android.notifications.SupplementReminderScheduler
import java.io.File

class MuscleNoteApplication : Application() {

    /** Données de l'app, créées une fois par processus (SPEC § 4.2). */
    lateinit var container: AppContainer
        private set

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
