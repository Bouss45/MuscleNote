package com.musclenote.android

import android.os.Build
import android.os.Process
import android.util.Log
import com.musclenote.android.data.crash.CrashEnvironment
import com.musclenote.android.data.crash.CrashReportFormat
import com.musclenote.android.data.crash.CrashReports
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Rapport de plantage local (SPEC § 10.9, 🔄 D17). Pour toute exception non rattrapée : écrit le
 * rapport (les 5 plus récents sont gardés), force l'écriture de la séance en cours si possible,
 * puis passe la main au gestionnaire par défaut — l'app se ferme normalement. Aucun envoi réseau.
 */
internal object CrashHandler {

    /** Au-delà, la séance en cours n'est pas attendue : le processus doit se terminer. */
    private const val FLUSH_TIMEOUT_MS = 1_500L

    private const val TAG = "CrashHandler"

    fun install(reports: CrashReports, flushPendingWrites: suspend () -> Unit) {
        // Lu tout de suite : pendant un plantage, on n'appelle plus que le strict nécessaire.
        val environment = CrashEnvironment(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // Une seule fois par processus : deux threads qui plantent ensemble n'écrivent qu'un rapport.
        val handled = AtomicBoolean(false)

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (handled.compareAndSet(false, true)) {
                try {
                    val text = CrashReportFormat.format(ZonedDateTime.now(), environment, thread.name, error)
                    reports.write(System.currentTimeMillis(), text)
                } catch (t: Throwable) {
                    Log.e(TAG, "Rapport de plantage impossible", t)
                }
                try {
                    runBlocking { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { flushPendingWrites() } }
                } catch (t: Throwable) {
                    Log.e(TAG, "Écriture de la séance en cours impossible", t)
                }
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
