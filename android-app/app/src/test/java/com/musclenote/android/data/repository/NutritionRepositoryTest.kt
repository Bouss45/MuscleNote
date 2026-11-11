package com.musclenote.android.data.repository

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pesées dans le dépôt nutrition (§ 3.11) : ajout, remplacement, suppression, persistance. */
class NutritionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val time = object : TimeSource {
        override fun now(): Instant = Instant.parse("2026-03-14T17:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")

    private suspend fun CoroutineScope.repository(): NutritionRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return NutritionRepository(files.bodyWeights, files.supplements, files.supplementsTaken, files.dailyTracker, time)
            .also { it.load() }
    }

    @Test
    fun `une pesée ajoutée remplace celle du jour, est triée et enregistrée`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertTrue(repo.addBodyWeight(LocalDate.parse("2026-03-10"), 80.0))
            assertTrue(repo.addBodyWeight(LocalDate.parse("2026-03-14"), 79.2))
            assertTrue(repo.addBodyWeight(LocalDate.parse("2026-03-10"), 79.8))
            assertEquals(
                listOf(BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.2), BodyWeightEntry("2026-03-10T11:00:00.000Z", 79.8)),
                repo.bodyWeights.value,
            )
        }
        coroutineScope { assertEquals(listOf(79.2, 79.8), repository().bodyWeights.value.map { it.weight }) }
    }

    @Test
    fun `un poids hors limites est refusé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertFalse(repo.addBodyWeight(LocalDate.parse("2026-03-10"), 0.0))
            assertFalse(repo.addBodyWeight(LocalDate.parse("2026-03-10"), 650.0))
            assertTrue(repo.bodyWeights.value.isEmpty())
        }
    }

    @Test
    fun `supprimer une pesée est immédiat et enregistré`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.addBodyWeight(LocalDate.parse("2026-03-10"), 80.0)
            repo.addBodyWeight(LocalDate.parse("2026-03-12"), 79.5)
            repo.removeBodyWeight("2026-03-10T11:00:00.000Z")
            assertEquals(listOf("2026-03-12T11:00:00.000Z"), repo.bodyWeights.value.map { it.date })
        }
        coroutineScope { assertEquals(listOf(79.5), repository().bodyWeights.value.map { it.weight }) }
    }
}
