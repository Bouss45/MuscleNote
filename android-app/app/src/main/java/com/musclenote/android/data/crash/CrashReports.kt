package com.musclenote.android.data.crash

import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un rapport de plantage écrit sur l'appareil. */
data class CrashReport(val file: File, val timestampMillis: Long) {
    val instant: Instant get() = Instant.ofEpochMilli(timestampMillis)
}

/**
 * Rapports de plantage locaux (SPEC § 10.9, 🔄 D17) : `crash-<timestamp ms>.txt` dans [dir]
 * (`filesDir/crashes`), seuls les [KEEP] plus récents sont gardés. Aucun envoi réseau : Paramètres
 * partage le dernier ou les supprime tous (§ 2.12). Les autres fichiers du dossier sont ignorés.
 */
class CrashReports(private val dir: File) {

    /** Écrit un rapport, puis efface les plus anciens au-delà de [KEEP]. */
    fun write(timestampMillis: Long, content: String): File {
        dir.mkdirs()
        val file = File(dir, "$PREFIX$timestampMillis$SUFFIX")
        file.writeText(content, Charsets.UTF_8)
        list().drop(KEEP).forEach { it.file.delete() }
        return file
    }

    /** Rapports présents, du plus récent au plus ancien. */
    fun list(): List<CrashReport> =
        dir.listFiles().orEmpty()
            .mapNotNull { file -> timestampOf(file.name)?.let { CrashReport(file, it) } }
            .sortedByDescending { it.timestampMillis }

    fun latest(): CrashReport? = list().firstOrNull()

    fun deleteAll() {
        list().forEach { it.file.delete() }
    }

    companion object {
        /** Sous-dossier de `filesDir`, aussi déclaré dans `res/xml/file_paths.xml` (partage). */
        const val DIR_NAME = "crashes"
        const val KEEP = 5
        private const val PREFIX = "crash-"
        private const val SUFFIX = ".txt"

        /** Horodatage d'un nom `crash-<timestamp ms>.txt`, `null` pour tout autre fichier. */
        fun timestampOf(fileName: String): Long? {
            if (!fileName.startsWith(PREFIX) || !fileName.endsWith(SUFFIX)) return null
            return fileName.substring(PREFIX.length, fileName.length - SUFFIX.length).toLongOrNull()
        }
    }
}

/** Ce qui décrit l'app et l'appareil en tête d'un rapport (lu une fois, à l'installation du gestionnaire). */
data class CrashEnvironment(
    val appVersion: String,
    val androidVersion: String,
    val device: String,
)

/** Texte d'un rapport (§ 10.9) : date locale, version de l'app, Android, appareil, trace complète. */
object CrashReportFormat {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx", Locale.ROOT)

    fun format(at: ZonedDateTime, environment: CrashEnvironment, threadName: String, error: Throwable): String =
        buildString {
            appendLine("MuscleNote crash report")
            appendLine("Date: ${dateFormatter.format(at)} (${at.zone.id})")
            appendLine("App: ${environment.appVersion}")
            appendLine("Android: ${environment.androidVersion}")
            appendLine("Device: ${environment.device}")
            appendLine("Thread: $threadName")
            appendLine()
            // Causes (« Caused by ») et exceptions supprimées comprises.
            append(error.stackTraceToString())
        }
}
