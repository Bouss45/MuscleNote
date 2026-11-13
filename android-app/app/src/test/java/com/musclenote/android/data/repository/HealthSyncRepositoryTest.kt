package com.musclenote.android.data.repository

import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.health.HealthSyncState
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
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
import java.time.Instant
import java.time.ZoneId

/** Dépôt des envois à Health Connect (§ 15.3, D30) : activation, file, mises à jour, suppressions, échecs. */
class HealthSyncRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T08:00:00Z")
    private val zone = ZoneId.of("Europe/Paris")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = zone
    }

    /** Health Connect en mémoire : `clientRecordId` → (empreinte, version). */
    private class FakeGateway : HealthConnectGateway {
        val stored = mutableMapOf<String, Pair<String, Long>>()
        var granted: Set<HealthRecordType> = HealthRecordType.entries.toSet()
        var fail: Exception? = null

        /** Health Connect refuse un appel qui contient une séance (l'appel entier échoue). */
        var rejectSessions: Exception? = null

        /** Nombre d'écritures que Health Connect confirme par appel (`null` = toutes). */
        var confirmAtMost: Int? = null
        var upsertCalls = 0
        val deleteCalls = mutableListOf<List<String>>()

        override fun isAvailable() = true

        override suspend fun grantedTypes() = granted

        override suspend fun upsert(records: List<HealthRecordSpec>, version: Long): Int {
            fail?.let { throw it }
            if (records.any { it.type == HealthRecordType.Session }) rejectSessions?.let { throw it }
            check(records.map { it.type }.distinct().size == 1) { "un seul type par appel" }
            upsertCalls++
            val confirmed = records.take(confirmAtMost ?: records.size)
            for (r in confirmed) {
                check(r.type in granted)
                val previous = stored[r.clientRecordId]
                // Health Connect ne remplace que si la version est plus grande.
                if (previous == null || version > previous.second) stored[r.clientRecordId] = r.fingerprint to version
            }
            return confirmed.size
        }

        override suspend fun delete(type: HealthRecordType, clientRecordIds: List<String>) {
            fail?.let { throw it }
            check(type in granted)
            deleteCalls += clientRecordIds
            clientRecordIds.forEach { stored.remove(it) }
        }
    }

    private val gateway = FakeGateway()
    private var workouts = listOf(workout("w1"))
    private var weighIns = listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2))

    private val all = HealthRecordType.entries.toSet()

    private fun workout(id: String, date: String = "2026-11-10T17:00:00.000Z", durationMinutes: Int? = 60, bodyWeight: Double = 78.5) = Workout(
        id = id,
        name = "Séance $id",
        date = date,
        bodyWeight = bodyWeight,
        exercises = emptyList(),
        durationMinutes = durationMinutes,
    )

    /** Séance du lendemain (11 novembre) : son poids est celui d'un autre jour. */
    private fun nextDay(id: String) = workout(id, date = "2026-11-11T17:00:00.000Z")

    private fun files(scope: CoroutineScope) = DataFiles(File(tmp.root, "data"), scope, Dispatchers.IO)

    private suspend fun CoroutineScope.repository(): HealthSyncRepository =
        HealthSyncRepository(files(this).healthConnect, time, gateway) { HealthRecords.all(workouts, weighIns, zone) }.also { it.load() }

    /** Ce que ferait AppContainer : changements entre deux états mis en attente, puis envoi. */
    private suspend fun HealthSyncRepository.change(newWorkouts: List<Workout> = workouts, newWeighIns: List<BodyWeightEntry> = weighIns) {
        val before = HealthRecords.fingerprints(workouts, weighIns, zone)
        workouts = newWorkouts
        weighIns = newWeighIns
        enqueue(HealthRecords.changedIds(before, HealthRecords.fingerprints(workouts, weighIns, zone)))
        sync()
    }

    private fun storedOf(type: HealthRecordType) = gateway.stored.keys.filter { HealthRecords.typeOf(it) == type }

    @Test
    fun `sans autorisation l'option reste coupée, avec elle seuls les changements suivants partent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertFalse(repo.onPermissionsResult(emptySet()))
            assertFalse(repo.state.value.enabled)

            assertTrue(repo.onPermissionsResult(all))
            assertTrue(repo.state.value.enabled)
            // L'existant n'est pas envoyé à l'activation.
            assertTrue(gateway.stored.isEmpty())

            repo.change(newWorkouts = workouts + nextDay("w2"))
            assertEquals(setOf("musclenote-session-w2", "musclenote-weight-2026-11-11"), gateway.stored.keys)
            assertEquals(clock.toEpochMilli(), repo.state.value.lastSyncAt)
            assertTrue(repo.state.value.pending.isEmpty())
            assertNull(repo.state.value.lastError)
        }
    }

    @Test
    fun `envoyer l'historique écrit tout, sans doublon, et compte ce que Health Connect confirme`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            val result = repo.sendHistory()
            // Séance du 10 (poids saisi au démarrage) et pesée du 9 : deux jours, deux poids.
            assertEquals(HealthHistoryResult.Done(sessions = 1, weights = 2), result)
            assertEquals(
                setOf("musclenote-session-w1", "musclenote-weight-2026-11-10", "musclenote-weight-2026-11-09"),
                gateway.stored.keys,
            )
            assertEquals(mapOf(HealthRecordType.Session to 1, HealthRecordType.Weight to 2), HealthRecords.countByType(repo.state.value.sent.keys))
            clock = clock.plusSeconds(1)
            assertEquals(result, repo.sendHistory())
            assertEquals(3, gateway.stored.size)
            assertEquals(result, repo.historyResult.value)
        }
    }

    @Test
    fun `signalement - une séance et une pesée le même jour, une seule mesure de poids écrite et annoncée`() = runBlocking<Unit> {
        coroutineScope {
            // Séance du 10 novembre (poids saisi au démarrage) et pesée du même jour.
            weighIns = listOf(BodyWeightEntry("2026-11-10T11:00:00.000Z", 78.2))
            val repo = repository()
            repo.onPermissionsResult(all)
            val result = repo.sendHistory()
            assertEquals(1, storedOf(HealthRecordType.Session).size)
            assertEquals(listOf("musclenote-weight-2026-11-10"), storedOf(HealthRecordType.Weight))
            assertEquals(HealthRecordType.Weight, HealthRecords.typeOf("musclenote-weight-2026-11-10"))
            assertEquals(HealthHistoryResult.Done(sessions = 1, weights = 1), result)
        }
    }

    @Test
    fun `une pesée ajoutée remplace le poids de séance du jour, sans second enregistrement`() = runBlocking<Unit> {
        coroutineScope {
            weighIns = emptyList()
            val repo = repository()
            repo.onPermissionsResult(all)
            repo.sendHistory()
            assertEquals(listOf("musclenote-weight-2026-11-10"), storedOf(HealthRecordType.Weight))

            repo.change(newWeighIns = listOf(BodyWeightEntry("2026-11-10T11:00:00.000Z", 78.2)))
            assertEquals(listOf("musclenote-weight-2026-11-10"), storedOf(HealthRecordType.Weight))
            assertEquals("2026-11-10T11:00:00Z|78.2", gateway.stored.getValue("musclenote-weight-2026-11-10").first)
        }
    }

    @Test
    fun `une séance refusée par Health Connect n'empêche pas l'envoi du poids, et l'échec est annoncé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            gateway.rejectSessions = IllegalArgumentException("séance refusée")
            val result = repo.sendHistory()
            assertTrue(storedOf(HealthRecordType.Session).isEmpty())
            assertEquals(2, storedOf(HealthRecordType.Weight).size)
            assertEquals(
                HealthHistoryResult.Done(sessions = 0, weights = 2, failures = mapOf(HealthRecordType.Session to "séance refusée")),
                result,
            )
            val state = repo.state.value
            assertEquals("séance refusée", state.lastError)
            assertEquals(HealthRecordType.Session, state.lastErrorType)
            assertEquals(clock.toEpochMilli(), state.lastErrorAt)
            assertEquals(listOf("musclenote-session-w1"), state.pending)

            // Health Connect accepte de nouveau : la séance part, l'erreur s'efface.
            gateway.rejectSessions = null
            clock = clock.plusSeconds(60)
            assertTrue(repo.sync())
            assertEquals(listOf("musclenote-session-w1"), storedOf(HealthRecordType.Session))
            assertNull(repo.state.value.lastError)
            assertNull(repo.state.value.lastErrorAt)
        }
    }

    @Test
    fun `seules les écritures confirmées comptent, le reste attend le prochain essai`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            gateway.confirmAtMost = 1
            val result = repo.sendHistory()
            assertEquals(HealthHistoryResult.Done(sessions = 1, weights = 1, failures = mapOf(HealthRecordType.Weight to "1/2")), result)
            assertEquals(2, repo.state.value.sent.size)
            assertEquals(1, repo.state.value.pending.size)

            gateway.confirmAtMost = null
            assertTrue(repo.sync())
            assertEquals(3, repo.state.value.sent.size)
            assertTrue(repo.state.value.pending.isEmpty())
        }
    }

    @Test
    fun `les doublons de la v1_0_5 sont supprimés et remplacés par le poids du jour`() = runBlocking<Unit> {
        coroutineScope {
            weighIns = listOf(BodyWeightEntry("2026-11-10T11:00:00.000Z", 78.2))
            // Ce que la v1.0.5 a écrit : une séance, le poids de la séance et la pesée du même jour.
            val legacy = mapOf(
                "musclenote-session-w1" to "2026-11-10T17:00:00Z|60||Séance w1",
                "musclenote-weight-workout-w1" to "2026-11-10T17:00:00Z|78.5",
                "musclenote-weight-weighin-2026-11-10T11:00:00.000Z" to "2026-11-10T11:00:00Z|78.2",
            )
            legacy.forEach { (id, fingerprint) -> gateway.stored[id] = fingerprint to 1L }
            files(this).healthConnect.saveNow(HealthSyncState(enabled = true, sent = legacy, lastVersion = 1))

            val repo = repository()
            assertTrue(repo.sync())
            assertEquals(setOf("musclenote-session-w1", "musclenote-weight-2026-11-10"), gateway.stored.keys)
            assertEquals("2026-11-10T11:00:00Z|78.2", gateway.stored.getValue("musclenote-weight-2026-11-10").first)
            assertEquals(gateway.stored.keys, repo.state.value.sent.keys)
            assertTrue(repo.state.value.pending.isEmpty())
            // Plus rien à faire ensuite.
            val calls = gateway.upsertCalls
            assertTrue(repo.sync())
            assertEquals(calls, gateway.upsertCalls)
        }
    }

    @Test
    fun `modifier remplace avec une version plus grande, supprimer retire de Health Connect`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            repo.sendHistory()
            val firstVersion = gateway.stored.getValue("musclenote-session-w1").second

            // Horloge reculée : la version augmente quand même.
            clock = clock.minusSeconds(3600)
            repo.change(newWorkouts = listOf(workout("w1", durationMinutes = 90)))
            val (fingerprint, version) = gateway.stored.getValue("musclenote-session-w1")
            assertTrue(version > firstVersion)
            assertEquals(HealthRecords.all(workouts, weighIns, zone).getValue("musclenote-session-w1").fingerprint, fingerprint)

            // Pesée supprimée, séance supprimée.
            repo.change(newWorkouts = emptyList(), newWeighIns = emptyList())
            assertTrue(gateway.stored.isEmpty())
            assertTrue(repo.state.value.sent.isEmpty())
        }
    }

    @Test
    fun `ce qui n'a jamais été écrit n'est pas supprimé dans Health Connect`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            // Séance jamais envoyée (antérieure à l'activation), puis supprimée.
            repo.change(newWorkouts = emptyList())
            assertTrue(gateway.deleteCalls.isEmpty())
            assertTrue(repo.state.value.pending.isEmpty())
            assertTrue(repo.sync())
        }
    }

    @Test
    fun `un poids remis à 0 supprime la mesure du jour`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            repo.sendHistory()
            repo.change(newWorkouts = listOf(workout("w1", bodyWeight = 0.0)))
            assertFalse("musclenote-weight-2026-11-10" in gateway.stored)
            assertTrue("musclenote-session-w1" in gateway.stored)
        }
    }

    @Test
    fun `un échec garde la file, le prochain essai la vide`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            gateway.fail = IllegalStateException("Health Connect indisponible")
            repo.change(newWorkouts = workouts + nextDay("w2"))
            assertEquals("Health Connect indisponible", repo.state.value.lastError)
            assertEquals(setOf("musclenote-session-w2", "musclenote-weight-2026-11-11"), repo.state.value.pending.toSet())
            assertTrue(gateway.stored.isEmpty())
            val result = repo.sendHistory() as HealthHistoryResult.Done
            assertEquals(setOf(HealthRecordType.Session, HealthRecordType.Weight), result.failures.keys)
        }
        // Relu depuis health_connect.json : la file a survécu à l'arrêt de l'app.
        coroutineScope {
            gateway.fail = null
            val repo = repository()
            assertTrue(repo.sync())
            assertTrue("musclenote-session-w2" in gateway.stored)
            assertTrue(repo.state.value.pending.isEmpty())
            assertNull(repo.state.value.lastError)
        }
    }

    @Test
    fun `des autorisations illisibles ne tentent rien et l'annoncent`() = runBlocking<Unit> {
        coroutineScope {
            files(this).healthConnect.saveNow(HealthSyncState(enabled = true))
            val unavailable = object : HealthConnectGateway by gateway {
                override suspend fun grantedTypes(): Set<HealthRecordType> = throw IllegalStateException("service arrêté")
            }
            val broken = HealthSyncRepository(files(this).healthConnect, time, unavailable) { HealthRecords.all(workouts, weighIns, zone) }.also { it.load() }
            assertEquals(HealthHistoryResult.Failed("service arrêté"), broken.sendHistory())
            assertNull(broken.state.value.lastErrorType)
            assertEquals(clock.toEpochMilli(), broken.state.value.lastErrorAt)
        }
    }

    @Test
    fun `un type non autorisé attend son autorisation`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            gateway.granted = setOf(HealthRecordType.Weight)
            repo.onPermissionsResult(gateway.granted)
            assertTrue(repo.state.value.enabled)
            repo.change(newWorkouts = workouts + nextDay("w2"))
            assertEquals(setOf("musclenote-weight-2026-11-11"), gateway.stored.keys)
            assertEquals(listOf("musclenote-session-w2"), repo.state.value.pending)
            assertEquals(setOf(HealthRecordType.Session), (repo.sendHistory() as HealthHistoryResult.Done).notGranted)

            gateway.granted = all
            repo.onPermissionsResult(gateway.granted)
            assertTrue("musclenote-session-w2" in gateway.stored)
            assertTrue(repo.state.value.pending.isEmpty())
        }
    }

    @Test
    fun `coupée rien ne part, réactivée ce qui avait été envoyé puis modifié est remis à jour`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            repo.sendHistory()
            repo.disable()
            assertFalse(repo.state.value.enabled)

            // Modifiée et ajoutée pendant que l'option est coupée.
            repo.change(newWorkouts = listOf(workout("w1", durationMinutes = 45), nextDay("w3")))
            assertFalse("musclenote-session-w3" in gateway.stored)
            assertTrue(repo.state.value.pending.isEmpty())
            assertFalse(repo.sync())

            repo.onPermissionsResult(all)
            val expected = HealthRecords.all(workouts, weighIns, zone).getValue("musclenote-session-w1").fingerprint
            assertEquals(expected, gateway.stored.getValue("musclenote-session-w1").first)
            // La séance ajoutée pendant la coupure n'est envoyée que par « Envoyer l'historique ».
            assertFalse("musclenote-session-w3" in gateway.stored)
        }
    }

    @Test
    fun `les identifiants étrangers en attente sont abandonnés`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(all)
            repo.enqueue(listOf("autre-app-1"))
            assertTrue(repo.sync())
            assertTrue(repo.state.value.pending.isEmpty())
            assertEquals(0, gateway.upsertCalls)
        }
    }
}
