package com.musclenote.android.data.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** Rapports de plantage locaux (§ 10.9) : nommage, 5 plus récents gardés, format du texte. */
class CrashReportsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun reports() = CrashReports(File(tmp.root, CrashReports.DIR_NAME))

    @Test
    fun `aucun rapport tant que le dossier n'existe pas`() {
        val reports = reports()
        assertEquals(emptyList<CrashReport>(), reports.list())
        assertNull(reports.latest())
        reports.deleteAll()
    }

    @Test
    fun `un rapport est écrit sous crash-timestamp txt et relu comme le plus récent`() {
        val reports = reports()
        val file = reports.write(1_700_000_000_123, "trace")
        assertEquals("crash-1700000000123.txt", file.name)
        assertEquals("trace", file.readText())
        assertEquals(CrashReport(file, 1_700_000_000_123), reports.latest())
    }

    @Test
    fun `seuls les 5 plus récents sont gardés, quel que soit l'ordre d'écriture`() {
        val reports = reports()
        for (ts in listOf(30L, 10L, 70L, 50L, 20L, 60L, 40L)) reports.write(ts, "r$ts")
        assertEquals(listOf(70L, 60L, 50L, 40L, 30L), reports.list().map { it.timestampMillis })
        assertEquals(70L, reports.latest()?.timestampMillis)
    }

    @Test
    fun `les autres fichiers du dossier sont ignorés et conservés`() {
        val dir = File(tmp.root, CrashReports.DIR_NAME).apply { mkdirs() }
        val foreign = listOf("notes.txt", "crash-abc.txt", "crash-12.log").map { File(dir, it).apply { writeText("x") } }
        val reports = CrashReports(dir)
        reports.write(5, "r")
        assertEquals(listOf(5L), reports.list().map { it.timestampMillis })
        reports.deleteAll()
        assertNull(reports.latest())
        assertTrue(foreign.all { it.exists() })
    }

    @Test
    fun `timestampOf ne reconnaît que crash-nombre txt`() {
        assertEquals(42L, CrashReports.timestampOf("crash-42.txt"))
        assertNull(CrashReports.timestampOf("crash-.txt"))
        assertNull(CrashReports.timestampOf("crash-42.json"))
        assertNull(CrashReports.timestampOf("x-crash-42.txt"))
    }

    @Test
    fun `le texte contient la date locale, l'app, Android, l'appareil et la trace avec sa cause`() {
        val at = ZonedDateTime.of(2026, 3, 14, 20, 5, 9, 0, ZoneId.of("Europe/Paris"))
        val error = IllegalStateException("boum", IllegalArgumentException("cause première"))
        val text = CrashReportFormat.format(
            at,
            CrashEnvironment(appVersion = "0.1.42 (42)", androidVersion = "16 (API 36)", device = "Google Pixel 9"),
            threadName = "main",
            error = error,
        )
        val lines = text.lines()
        assertEquals("MuscleNote crash report", lines[0])
        assertEquals("Date: 2026-03-14 20:05:09 +01:00 (Europe/Paris)", lines[1])
        assertEquals("App: 0.1.42 (42)", lines[2])
        assertEquals("Android: 16 (API 36)", lines[3])
        assertEquals("Device: Google Pixel 9", lines[4])
        assertEquals("Thread: main", lines[5])
        assertEquals("", lines[6])
        assertEquals("java.lang.IllegalStateException: boum", lines[7])
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException: cause première"))
    }
}
