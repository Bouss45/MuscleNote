package com.musclenote.android.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.backup.AutoBackupRules
import com.musclenote.android.data.repository.AutoBackupScheduler
import java.util.concurrent.TimeUnit

/**
 * Tâche périodique de la sauvegarde automatique (SPEC § 15.2) : une sauvegarde si l'option est
 * toujours activée. Le résultat (réussite ou échec) est enregistré par le dépôt et affiché dans
 * Paramètres ; la tâche se termine toujours sans nouvel essai immédiat (prochaine échéance).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MuscleNoteApplication).container
        container.awaitReady()
        container.autoBackup.runScheduled()
        return Result.success()
    }
}

/** Planification par WorkManager : tâche périodique unique [WORK_NAME], toutes les 24 h, sans contrainte. */
class WorkManagerAutoBackupScheduler(context: Context) : AutoBackupScheduler {
    private val appContext = context.applicationContext

    private fun workManager() = WorkManager.getInstance(appContext)

    // La sauvegarde de l'activation est faite directement : première échéance dans 24 h.
    private fun request() =
        PeriodicWorkRequestBuilder<AutoBackupWorker>(AutoBackupRules.PERIOD_HOURS, TimeUnit.HOURS)
            .setInitialDelay(AutoBackupRules.PERIOD_HOURS, TimeUnit.HOURS)
            .build()

    override fun schedule() {
        workManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request())
    }

    override fun ensureScheduled() {
        workManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request())
    }

    override fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "auto-backup"
    }
}
