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

/** Dépôt de la vérification des mises à jour (§ 3.17) : échéance, échecs, annonce masquée. */
class UpdateRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T08:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")
    private var calls = 0
    private var next: () -> LatestRelease = { LatestRelease("1.0.5", "page", "apk") }
    private val source = object : ReleaseSource {
        override suspend fun latest(): LatestRelease {
            calls++
            return next()
        }
    }

    private suspend fun CoroutineScope.repository(): UpdateRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return UpdateRepository(files.updateCheck, time, "1.0.4", source).also { it.load() }
    }

    @Test
    fun `première vérification : la Release trouvée est enregistrée et proposée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertEquals(UpdateCheckResult.Available, repo.checkIfDue())
            assertEquals(1, calls)
            assertEquals(clock.toEpochMilli(), repo.state.value.checkedAt)
            assertEquals(LatestRelease("1.0.5", "page", "apk"), UpdateRules.available(repo.state.value, repo.currentVersion))
            assertFalse(repo.lastCheckFailed.value)
        }
        // Relue depuis le disque par un nouveau dépôt.
        coroutineScope { assertEquals("1.0.5", repository().state.value.latestVersion) }
    }

    @Test
    fun `pas de nouvelle vérification automatique avant 24 h, mais Vérifier passe toujours`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.checkIfDue()
            clock = clock.plusMillis(UpdateRules.CHECK_INTERVAL_MS - 1)
            assertNull(repo.checkIfDue())
            assertEquals(1, calls)
            next = { LatestRelease("1.0.4", "page", null) }
            assertEquals(UpdateCheckResult.UpToDate, repo.checkNow())
            assertEquals(2, calls)
            clock = clock.plusMillis(UpdateRules.CHECK_INTERVAL_MS)
            assertEquals(UpdateCheckResult.UpToDate, repo.checkIfDue())
            assertEquals(3, calls)
        }
    }

    @Test
    fun `un échec ne change rien d'enregistré et n'est retenté automatiquement qu'après une heure`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            next = { throw IOException("pas de réseau") }
            assertEquals(UpdateCheckResult.Failed, repo.checkIfDue())
            assertTrue(repo.lastCheckFailed.value)
            assertEquals(UpdateCheckState.DEFAULT, repo.state.value)
            assertFalse(repo.checking.value)

            clock = clock.plusMillis(UpdateRules.RETRY_INTERVAL_MS - 1)
            assertNull(repo.checkIfDue())
            assertEquals(1, calls)

            next = { LatestRelease("1.0.5", "page", "apk") }
            clock = clock.plusMillis(1)
            assertEquals(UpdateCheckResult.Available, repo.checkIfDue())
            assertFalse(repo.lastCheckFailed.value)
        }
    }

    @Test
    fun `masquer l'annonce est mémorisé pour cette version`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.checkIfDue()
            repo.dismiss("1.0.5")
            assertEquals("1.0.5", repo.state.value.dismissedVersion)
        }
        coroutineScope { assertEquals("1.0.5", repository().state.value.dismissedVersion) }
    }
}
