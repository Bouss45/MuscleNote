package com.musclenote.android.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.repository.UpdateCheckScheduler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Vérification quotidienne des mises à jour (SPEC § 15.14.2, 🔄 D39) : le dépôt vérifie si le
 * réglage est toujours activé, notifie une nouvelle version et programme la vérification du
 * lendemain. Toujours terminée sans nouvel essai : un échec attend la 18:00 suivante.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as MuscleNoteApplication).container.runDailyUpdateCheck()
        return Result.success()
    }
}

/**
 * Planification par WorkManager : une tâche ponctuelle unique [WORK_NAME] vers 18 h, réseau requis,
 * reprogrammée par elle-même à chaque passage (une tâche ponctuelle suit l'heure murale, là où une
 * tâche périodique dériverait).
 */
class WorkManagerUpdateCheckScheduler(context: Context) : UpdateCheckScheduler {
    private val appContext = context.applicationContext

    private fun workManager() = WorkManager.getInstance(appContext)

    override fun schedule(at: Instant, mode: UpdateCheckScheduler.Mode) {
        val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val policy = when (mode) {
            UpdateCheckScheduler.Mode.KeepExisting -> ExistingWorkPolicy.KEEP
            UpdateCheckScheduler.Mode.Replace -> ExistingWorkPolicy.REPLACE
            // Depuis la tâche en cours : REPLACE l'arrêterait ; la suivante démarre après elle.
            UpdateCheckScheduler.Mode.AfterCurrentRun -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        workManager().enqueueUniqueWork(WORK_NAME, policy, request)
    }

    override fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "update-check"
    }
}
