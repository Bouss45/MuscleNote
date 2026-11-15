package com.musclenote.android.data.repository

import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.data.update.UpdateCheckState
import com.musclenote.android.data.update.UpdateRules
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

/**
 * Dépôt de la vérification des mises à jour (§ 3.17, § 15.14) : vérification quotidienne et
 * « Vérifier maintenant », une notification par version, version ignorée ou intermédiaire,
 * notification remplacée ou retirée, réglage par défaut selon la source d'installation, tâche.
 */
class UpdateRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Jeudi 12 novembre 2026, 09:00 à Paris. */
    private var clock: Instant = Instant.parse("2026-11-12T08:00:00Z")
    private val zone = ZoneId.of("Europe/Paris")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = zone
    }

    private val dataDir get() = File(tmp.root, "data")
    private var calls = 0
    private var next: () -> LatestRelease = { release("1.0.12") }
    private val source = object : ReleaseSource {
        override suspend fun latest(): LatestRelease {
            calls++
            return next()
        }
    }

    /** Une seule notification affichée à la fois, comme sur l'appareil (un seul identifiant). */
    private class FakeNotifications : UpdateNotifications {
        var canShow = true
        var displayed: LatestRelease? = null
        val shown = mutableListOf<String>()
        var cancels = 0

        override fun show(release: LatestRelease): Boolean {
            if (!canShow) return false
            displayed = release
            shown += release.version
            return true
        }

        override fun cancel() {
            displayed = null
            cancels++
        }
    }

    private class FakeScheduler : UpdateCheckScheduler {
        val scheduled = mutableListOf<Pair<Instant, UpdateCheckScheduler.Mode>>()
        var cancels = 0

        override fun schedule(at: Instant, mode: UpdateCheckScheduler.Mode) {
            scheduled += at to mode
        }

        override fun cancel() {
            cancels++
        }
    }

    private val notifications = FakeNotifications()
    private val scheduler = FakeScheduler()

    private fun release(version: String, notes: String? = "Nouveauté de la $version") =
        LatestRelease(version, "https://github.com/o/r/releases/tag/v$version", "https://example.test/MuscleNote-v$version.apk", notes)

    private suspend fun CoroutineScope.repository(
        installedName: String = "1.0.11",
        installedCode: Int = 1011,
        obtainium: Boolean = false,
        notificationsAvailable: Boolean = true,
    ): UpdateRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return UpdateRepository(
            files.updateCheck,
            time,
            currentVersion = installedName,
            currentVersionCode = installedCode,
            source = source,
            installedByObtainium = obtainium,
            notificationsAvailable = notificationsAvailable,
            notifications = notifications,
            scheduler = scheduler,
        ).also { it.load() }
    }

    /** 18:00 locale du jour, puis du lendemain : prochaines vérifications depuis 09:00. */
    private val today18h = Instant.parse("2026-11-12T17:00:00Z")
    private val tomorrow18h = Instant.parse("2026-11-13T17:00:00Z")

    @Test
    fun `vérification quotidienne, nouvelle version enregistrée, notifiée et suivante programmée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            clock = today18h
            assertEquals(UpdateCheckResult.Available, repo.dailyCheck())
            assertEquals(1, calls)
            assertEquals(today18h.toEpochMilli(), repo.state.value.checkedAt)
            assertEquals("1.0.12", repo.state.value.latestVersion)
            assertEquals(release("1.0.12"), notifications.displayed)
            assertEquals("1.0.12", repo.state.value.notifiedVersion)
            assertEquals(tomorrow18h to UpdateCheckScheduler.Mode.AfterCurrentRun, scheduler.scheduled.last())
        }
        // Relu depuis le disque par un nouveau dépôt.
        coroutineScope { assertEquals("1.0.12", repository().state.value.notifiedVersion) }
    }

    @Test
    fun `une seule notification par version`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.dailyCheck()
            clock = tomorrow18h
            assertEquals(UpdateCheckResult.Available, repo.dailyCheck())
            assertEquals(2, calls)
            assertEquals(listOf("1.0.12"), notifications.shown)
        }
    }

    @Test
    fun `réponse absente ou invalide, rien d'enregistré, pas de notification, essai suivant le lendemain`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            clock = today18h
            next = { throw IOException("pas de réseau") }
            assertEquals(UpdateCheckResult.Failed, repo.dailyCheck())
            assertTrue(repo.lastCheckFailed.value)
            assertEquals(UpdateCheckState.DEFAULT, repo.state.value)
            assertFalse(repo.checking.value)
            assertEquals(emptyList<String>(), notifications.shown)
            assertEquals(tomorrow18h to UpdateCheckScheduler.Mode.AfterCurrentRun, scheduler.scheduled.last())

            // Réponse illisible (GitHubReleaseSource lève une IOException) : pareil.
            next = { throw IOException("Réponse inattendue") }
            assertEquals(UpdateCheckResult.Failed, repo.checkNow())
            assertEquals(UpdateCheckState.DEFAULT, repo.state.value)

            next = { release("1.0.12") }
            assertEquals(UpdateCheckResult.Available, repo.dailyCheck())
            assertFalse(repo.lastCheckFailed.value)
        }
    }

    @Test
    fun `version ignorée, ni notification ni annonce, la suivante est notifiée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.dailyCheck()
            repo.dismiss("1.0.12")
            assertEquals("1.0.12", repo.state.value.dismissedVersion)
            assertNull(notifications.displayed)

            // Même version revue : rien.
            repo.dailyCheck()
            assertEquals(listOf("1.0.12"), notifications.shown)

            next = { release("1.0.13") }
            repo.dailyCheck()
            assertEquals(listOf("1.0.12", "1.0.13"), notifications.shown)
        }
        coroutineScope { assertEquals("1.0.12", repository().state.value.dismissedVersion) }
    }

    @Test
    fun `une version ignorée avant d'être notifiée ne l'est jamais`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            // « Plus tard » de l'Accueil après « Vérifier maintenant ».
            repo.checkNow()
            repo.dismiss("1.0.12")
            repo.dailyCheck()
            assertEquals(emptyList<String>(), notifications.shown)
        }
    }

    @Test
    fun `version intermédiaire sautée`() = runBlocking<Unit> {
        coroutineScope {
            // 1.0.10 installée ; 1.0.11, 1.0.12 et 1.0.13 publiées entre deux vérifications.
            val repo = repository(installedName = "1.0.10", installedCode = 1010)
            next = { release("1.0.13") }
            repo.dailyCheck()
            assertEquals(listOf("1.0.13"), notifications.shown)

            // L'API renvoie ensuite une Release plus ancienne (la 1.0.13 retirée) : pas notifiée.
            next = { release("1.0.12") }
            repo.dailyCheck()
            assertEquals(listOf("1.0.13"), notifications.shown)
            assertEquals("1.0.13", repo.state.value.notifiedVersion)
        }
    }

    @Test
    fun `une nouvelle version remplace la notification affichée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.dailyCheck()
            assertEquals("1.0.12", notifications.displayed?.version)

            next = { release("1.0.13") }
            clock = tomorrow18h
            repo.dailyCheck()
            assertEquals(release("1.0.13"), notifications.displayed)
            assertEquals(listOf("1.0.12", "1.0.13"), notifications.shown)
            assertEquals("1.0.13", repo.state.value.notifiedVersion)
        }
    }

    @Test
    fun `une notification qui n'a pas pu s'afficher n'est pas enregistrée et sera retentée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            notifications.canShow = false
            repo.dailyCheck()
            assertNull(repo.state.value.notifiedVersion)

            notifications.canShow = true
            repo.dailyCheck()
            assertEquals(listOf("1.0.12"), notifications.shown)
            assertEquals("1.0.12", repo.state.value.notifiedVersion)
        }
    }

    @Test
    fun `Vérifier maintenant enregistre la version sans notifier`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertEquals(UpdateCheckResult.Available, repo.checkNow())
            assertEquals(LatestRelease("1.0.12", release("1.0.12").pageUrl, release("1.0.12").apkUrl), UpdateRules.available(repo.state.value, repo.currentVersion, repo.currentVersionCode))
            assertEquals(emptyList<String>(), notifications.shown)
            assertNull(repo.state.value.notifiedVersion)

            next = { release("1.0.11") }
            assertEquals(UpdateCheckResult.UpToDate, repo.checkNow())
        }
    }

    @Test
    fun `installée par Obtainium, réglage coupé par défaut, aucune requête automatique`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository(obtainium = true)
            assertFalse(repo.notifyEnabled())
            repo.onAppStart()
            assertEquals(emptyList<Pair<Instant, UpdateCheckScheduler.Mode>>(), scheduler.scheduled)
            assertTrue(scheduler.cancels > 0)

            // Une tâche restée programmée ne contacte pas GitHub.
            assertNull(repo.dailyCheck())
            assertEquals(0, calls)

            // « Vérifier maintenant » reste possible, sans notification.
            assertEquals(UpdateCheckResult.Available, repo.checkNow())
            assertEquals(1, calls)
            assertEquals(emptyList<String>(), notifications.shown)
        }
    }

    @Test
    fun `réglage coupé sans Obtainium, aucune requête automatique non plus`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertTrue(repo.notifyEnabled())
            repo.setNotifyEnabled(false)
            assertFalse(repo.notifyEnabled())
            assertNull(repo.dailyCheck())
            assertEquals(0, calls)
        }
    }

    @Test
    fun `sans Obtainium la tâche est programmée au démarrage, l'existante gardée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onAppStart()
            assertEquals(listOf(today18h to UpdateCheckScheduler.Mode.KeepExisting), scheduler.scheduled)
        }
    }

    @Test
    fun `activer le réglage avec Obtainium enregistre le choix et programme la tâche`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository(obtainium = true)
            repo.setNotifyEnabled(true)
            assertTrue(repo.notifyEnabled())
            assertEquals(true, repo.state.value.notifyEnabled)
            assertEquals(today18h to UpdateCheckScheduler.Mode.Replace, scheduler.scheduled.last())

            repo.dailyCheck()
            assertEquals(listOf("1.0.12"), notifications.shown)
        }
        // Choix relu depuis le disque : il l'emporte sur la source d'installation.
        coroutineScope { assertTrue(repository(obtainium = true).notifyEnabled()) }
    }

    @Test
    fun `couper le réglage annule la tâche et retire la notification`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.dailyCheck()
            val cancelsBefore = scheduler.cancels
            repo.setNotifyEnabled(false)
            assertEquals(false, repo.state.value.notifyEnabled)
            assertEquals(cancelsBefore + 1, scheduler.cancels)
            assertNull(notifications.displayed)
        }
    }

    @Test
    fun `variante preview, ni tâche ni notification, même réglage enregistré`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository(notificationsAvailable = false)
            repo.setNotifyEnabled(true)
            assertFalse(repo.notifyEnabled())
            repo.onAppStart()
            assertEquals(emptyList<Pair<Instant, UpdateCheckScheduler.Mode>>(), scheduler.scheduled)
            assertNull(repo.dailyCheck())
            assertEquals(0, calls)
            assertEquals(UpdateCheckResult.Available, repo.checkNow())
            assertEquals(emptyList<String>(), notifications.shown)
        }
    }

    @Test
    fun `la notification est retirée au démarrage quand la version notifiée est installée`() = runBlocking<Unit> {
        coroutineScope {
            repository().dailyCheck()
            assertEquals("1.0.12", notifications.displayed?.version)
        }
        // Toujours la 1.0.11 : la notification reste.
        coroutineScope {
            repository().onAppStart()
            assertEquals("1.0.12", notifications.displayed?.version)
        }
        // La 1.0.12 est installée : elle part.
        coroutineScope {
            repository(installedName = "1.0.12", installedCode = 1012).onAppStart()
            assertNull(notifications.displayed)
        }
    }
}
