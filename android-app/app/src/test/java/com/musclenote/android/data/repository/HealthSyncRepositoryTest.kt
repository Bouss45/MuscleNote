package com.musclenote.android.data.repository

import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.health.HealthRecords
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

/** Dépôt des envois à Health Connect (§ 15.3) : activation, file, mises à jour, suppressions, échecs. */
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
        var upsertCalls = 0

        override fun isAvailable() = true

        override suspend fun grantedTypes() = granted

        override suspend fun upsert(records: List<HealthRecordSpec>, version: Long) {
            fail?.let { throw it }
            upsertCalls++
            for (r in records) {
                check(r.type in granted)
                val previous = stored[r.clientRecordId]
                // Health Connect ne remplace que si la version est plus grande.
                if (previous == null || version > previous.second) stored[r.clientRecordId] = r.fingerprint to version
            }
        }

        override suspend fun delete(type: HealthRecordType, clientRecordIds: List<String>) {
            fail?.let { throw it }
            check(type in granted)
            clientRecordIds.forEach { stored.remove(it) }
        }
    }

    private val gateway = FakeGateway()
    private var workouts = listOf(workout("w1"))
    private var weighIns = listOf(BodyWeightEntry("2026-11-09T11:00:00.000Z", 78.2))

    private fun workout(id: String, durationMinutes: Int? = 60, bodyWeight: Double = 78.5) = Workout(
        id = id,
        name = "Séance $id",
        date = "2026-11-10T17:00:00.000Z",
        bodyWeight = bodyWeight,
        exercises = emptyList(),
        durationMinutes = durationMinutes,
    )

    private suspend fun CoroutineScope.repository(): HealthSyncRepository {
        val files = DataFiles(File(tmp.root, "data"), this, Dispatchers.IO)
        return HealthSyncRepository(files.healthConnect, time, gateway) { HealthRecords.all(workouts, weighIns, zone) }.also { it.load() }
    }

    /** Ce que ferait AppContainer : changements entre deux états mis en attente, puis envoi. */
    private suspend fun HealthSyncRepository.change(newWorkouts: List<Workout> = workouts, newWeighIns: List<BodyWeightEntry> = weighIns) {
        val before = HealthRecords.fingerprints(workouts, weighIns, zone)
        workouts = newWorkouts
        weighIns = newWeighIns
        enqueue(HealthRecords.changedIds(before, HealthRecords.fingerprints(workouts, weighIns, zone)))
        sync()
    }

    @Test
    fun `sans autorisation l'option reste coupée, avec elle seuls les changements suivants partent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertFalse(repo.onPermissionsResult(emptySet()))
            assertFalse(repo.state.value.enabled)

            assertTrue(repo.onPermissionsResult(HealthRecordType.entries.toSet()))
            assertTrue(repo.state.value.enabled)
            // L'existant n'est pas envoyé à l'activation.
            assertTrue(gateway.stored.isEmpty())

            repo.change(newWorkouts = workouts + workout("w2"))
            assertEquals(setOf("musclenote-session-w2", "musclenote-weight-workout-w2"), gateway.stored.keys)
            assertEquals(clock.toEpochMilli(), repo.state.value.lastSyncAt)
            assertTrue(repo.state.value.pending.isEmpty())
            assertNull(repo.state.value.lastError)
        }
    }

    @Test
    fun `envoyer l'historique écrit tout, sans doublon, et compte par type`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            val result = repo.sendHistory()
            assertEquals(HealthHistoryResult.Sent(sessions = 1, weights = 2), result)
            assertEquals(
                setOf("musclenote-session-w1", "musclenote-weight-workout-w1", "musclenote-weight-weighin-2026-11-09T11:00:00.000Z"),
                gateway.stored.keys,
            )
            clock = clock.plusSeconds(1)
            repo.sendHistory()
            assertEquals(3, gateway.stored.size)
            assertEquals(result, repo.historyResult.value)
        }
    }

    @Test
    fun `modifier remplace avec une version plus grande, supprimer retire de Health Connect`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            repo.sendHistory()
            val firstVersion = gateway.stored.getValue("musclenote-session-w1").second

            // Horloge reculée : la version augmente quand même.
            clock = clock.minusSeconds(3600)
            repo.change(newWorkouts = listOf(workout("w1", durationMinutes = 90)))
            val (fingerprint, version) = gateway.stored.getValue("musclenote-session-w1")
            assertTrue(version > firstVersion)
            assertEquals(HealthRecords.forWorkout(workouts[0], zone)[0].fingerprint, fingerprint)

            // Pesée supprimée, séance supprimée.
            repo.change(newWorkouts = emptyList(), newWeighIns = emptyList())
            assertTrue(gateway.stored.isEmpty())
            assertTrue(repo.state.value.sent.isEmpty())
        }
    }

    @Test
    fun `un poids remis à 0 supprime la mesure de la séance`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            repo.sendHistory()
            repo.change(newWorkouts = listOf(workout("w1", bodyWeight = 0.0)))
            assertFalse("musclenote-weight-workout-w1" in gateway.stored)
            assertTrue("musclenote-session-w1" in gateway.stored)
        }
    }

    @Test
    fun `un échec garde la file, le prochain essai la vide`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            gateway.fail = IllegalStateException("Health Connect indisponible")
            repo.change(newWorkouts = workouts + workout("w2"))
            assertEquals("Health Connect indisponible", repo.state.value.lastError)
            assertEquals(setOf("musclenote-session-w2", "musclenote-weight-workout-w2"), repo.state.value.pending.toSet())
            assertTrue(gateway.stored.isEmpty())
            assertTrue(repo.sendHistory() is HealthHistoryResult.Failed)
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
    fun `un type non autorisé attend son autorisation`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            gateway.granted = setOf(HealthRecordType.Weight)
            repo.onPermissionsResult(gateway.granted)
            assertTrue(repo.state.value.enabled)
            repo.change(newWorkouts = workouts + workout("w2"))
            assertEquals(setOf("musclenote-weight-workout-w2"), gateway.stored.keys)
            assertEquals(listOf("musclenote-session-w2"), repo.state.value.pending)

            gateway.granted = HealthRecordType.entries.toSet()
            repo.onPermissionsResult(gateway.granted)
            assertTrue("musclenote-session-w2" in gateway.stored)
            assertTrue(repo.state.value.pending.isEmpty())
        }
    }

    @Test
    fun `coupée, rien ne part ; réactivée, ce qui avait été envoyé puis modifié est remis à jour`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            repo.sendHistory()
            repo.disable()
            assertFalse(repo.state.value.enabled)

            // Modifiée et ajoutée pendant que l'option est coupée.
            repo.change(newWorkouts = listOf(workout("w1", durationMinutes = 45), workout("w3")))
            assertFalse("musclenote-session-w3" in gateway.stored)
            assertTrue(repo.state.value.pending.isEmpty())
            assertFalse(repo.sync())

            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            val expected = HealthRecords.forWorkout(workouts[0], zone)[0].fingerprint
            assertEquals(expected, gateway.stored.getValue("musclenote-session-w1").first)
            // La séance ajoutée pendant la coupure n'est envoyée que par « Envoyer l'historique ».
            assertFalse("musclenote-session-w3" in gateway.stored)
        }
    }

    @Test
    fun `les identifiants étrangers en attente sont abandonnés`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.onPermissionsResult(HealthRecordType.entries.toSet())
            repo.enqueue(listOf("autre-app-1"))
            assertTrue(repo.sync())
            assertTrue(repo.state.value.pending.isEmpty())
            assertEquals(0, gateway.upsertCalls)
        }
    }
}
