package com.musclenote.android.data.repository

import com.musclenote.android.data.backup.AutoBackupFailure
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** Dépôt de la sauvegarde automatique (§ 15.2) : activation, fichier du jour, rotation, échecs. */
class AutoBackupRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T20:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    /** Dossiers en mémoire : URI → (nom → contenu). */
    private class FakeFolders : BackupFolders {
        val folders = mutableMapOf<String, MutableMap<String, String>>()
        val adopted = mutableSetOf<String>()
        var failWrite: Exception? = null

        override suspend fun adopt(folderUri: String): String {
            adopted += folderUri
            folders.getOrPut(folderUri) { mutableMapOf() }
            return "Nom de $folderUri"
        }

        override suspend fun release(folderUri: String) {
            adopted -= folderUri
        }

        private fun folder(uri: String): MutableMap<String, String> {
            if (uri !in adopted) throw BackupFolderUnavailableException("Autorisation absente")
            return folders[uri] ?: throw BackupFolderUnavailableException("Dossier introuvable")
        }

        override suspend fun list(folderUri: String): List<String> = folder(folderUri).keys.toList()

        override suspend fun write(folderUri: String, name: String, content: String) {
            val folder = folder(folderUri)
            failWrite?.let { throw it }
            folder[name] = content
        }

        override suspend fun delete(folderUri: String, name: String) {
            folder(folderUri).remove(name)
        }
    }

    private class FakeScheduler : AutoBackupScheduler {
        var scheduled = false
        var schedules = 0

        override fun schedule() {
            scheduled = true
            schedules++
        }

        override fun ensureScheduled() {
            scheduled = true
        }

        override fun cancel() {
            scheduled = false
        }
    }

    private val folders = FakeFolders()
    private val scheduler = FakeScheduler()
    private var exported = 0

    private suspend fun CoroutineScope.repository(): AutoBackupRepository {
        val files = DataFiles(File(tmp.root, "data"), this, Dispatchers.IO)
        return AutoBackupRepository(files.autoBackup, time, folders, scheduler) { "{\"n\":${++exported}}" }.also { it.load() }
    }

    @Test
    fun `choisir un dossier active la sauvegarde et sauvegarde tout de suite`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val result = repo.chooseFolder("tree:A")
            assertEquals(AutoBackupResult.Saved("musclenote_backup_2026-11-12.json"), result)
            assertEquals(mapOf("musclenote_backup_2026-11-12.json" to "{\"n\":1}"), folders.folders["tree:A"])
            assertTrue(scheduler.scheduled)
            val state = repo.state.value
            assertTrue(state.enabled)
            assertEquals("tree:A", state.folderUri)
            assertEquals("Nom de tree:A", state.folderName)
            assertEquals(clock.toEpochMilli(), state.lastSuccessAt)
            assertNull(state.failure)
            assertFalse(repo.running.value)
        }
        // Relu depuis auto_backup.json : planifiée de nouveau au démarrage si besoin.
        coroutineScope {
            scheduler.scheduled = false
            val repo = repository()
            assertTrue(repo.state.value.enabled)
            repo.ensureScheduled()
            assertTrue(scheduler.scheduled)
        }
    }

    @Test
    fun `la sauvegarde du même jour remplace le fichier, le lendemain en crée un autre`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.chooseFolder("tree:A")
            clock = clock.plusSeconds(3600) // 22 h à Paris : même jour local
            repo.backupNow()
            assertEquals(mapOf("musclenote_backup_2026-11-12.json" to "{\"n\":2}"), folders.folders["tree:A"])
            clock = clock.plusSeconds(3 * 3600) // 1 h du matin le 13 à Paris (le 13 à 0 h UTC)
            assertEquals(AutoBackupResult.Saved("musclenote_backup_2026-11-13.json"), repo.runScheduled())
            assertEquals(setOf("musclenote_backup_2026-11-12.json", "musclenote_backup_2026-11-13.json"), folders.folders.getValue("tree:A").keys)
        }
    }

    @Test
    fun `après une sauvegarde, seules les 14 plus récentes restent, les autres fichiers aussi`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.chooseFolder("tree:A")
            val folder = folders.folders.getValue("tree:A")
            for (day in 1..20) folder["musclenote_backup_2026-10-%02d.json".format(day)] = "ancienne"
            folder["notes.txt"] = "à garder"
            repo.backupNow()
            val backups = folder.keys.filter { it.startsWith("musclenote_backup_") }.sorted()
            assertEquals(14, backups.size)
            assertEquals("musclenote_backup_2026-10-08.json", backups.first())
            assertEquals("musclenote_backup_2026-11-12.json", backups.last())
            assertEquals("à garder", folder["notes.txt"])
        }
    }

    @Test
    fun `un échec d'écriture est enregistré et ne supprime rien`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.chooseFolder("tree:A")
            val folder = folders.folders.getValue("tree:A")
            for (day in 1..20) folder["musclenote_backup_2026-10-%02d.json".format(day)] = "ancienne"
            folders.failWrite = IOException("Disque plein")
            clock = clock.plusSeconds(60)
            val result = repo.backupNow()
            assertEquals(AutoBackupResult.Failed(AutoBackupFailure.Write, "Disque plein"), result)
            assertEquals(21, folder.size)
            val state = repo.state.value
            assertEquals(AutoBackupFailure.Write, state.failure)
            assertEquals(clock.toEpochMilli(), state.lastFailureAt)
            assertEquals(clock.minusSeconds(60).toEpochMilli(), state.lastSuccessAt)

            // Une réussite efface l'échec.
            folders.failWrite = null
            repo.backupNow()
            assertNull(repo.state.value.failure)
            assertNull(repo.state.value.lastFailureAt)
        }
    }

    @Test
    fun `dossier inaccessible, message dédié jusqu'à ce qu'un dossier soit choisi`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.chooseFolder("tree:A")
            folders.adopted.clear() // autorisation retirée
            val result = repo.runScheduled()
            assertTrue(result is AutoBackupResult.Failed && result.failure == AutoBackupFailure.Folder)
            assertEquals(AutoBackupFailure.Folder, repo.state.value.failure)
            assertTrue(repo.state.value.enabled)

            // Nouveau dossier : l'ancien est rendu, la sauvegarde repart.
            assertEquals(AutoBackupResult.Saved("musclenote_backup_2026-11-12.json"), repo.chooseFolder("tree:B"))
            assertEquals("tree:B", repo.state.value.folderUri)
            assertNull(repo.state.value.failure)
            assertEquals(2, scheduler.schedules)
        }
    }

    @Test
    fun `désactivée, plus de tâche ni de sauvegarde planifiée, le dossier reste retenu`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.chooseFolder("tree:A")
            assertNull(repo.setEnabled(false))
            assertFalse(scheduler.scheduled)
            assertFalse(repo.state.value.enabled)
            assertEquals("tree:A", repo.state.value.folderUri)
            assertNull(repo.runScheduled())
            assertEquals(1, exported)

            // Réactivée sur le même dossier : planifiée et sauvegarde tout de suite.
            assertEquals(AutoBackupResult.Saved("musclenote_backup_2026-11-12.json"), repo.setEnabled(true))
            assertTrue(scheduler.scheduled)
            assertEquals(2, exported)
        }
    }

    @Test
    fun `sans dossier, activer ne fait rien (l'écran ouvre le sélecteur)`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertNull(repo.setEnabled(true))
            assertFalse(repo.state.value.enabled)
            assertFalse(scheduler.scheduled)
            assertEquals(0, exported)
        }
    }
}
