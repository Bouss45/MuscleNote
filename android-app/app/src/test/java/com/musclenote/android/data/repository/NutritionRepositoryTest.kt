package com.musclenote.android.data.repository

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.SupplementStock
import com.musclenote.android.data.nutrition.StockRules
import com.musclenote.android.data.nutrition.ReminderRef
import com.musclenote.android.data.nutrition.ReminderSchedule
import com.musclenote.android.data.nutrition.SupplementReminders
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
import java.time.LocalDate
import java.time.ZoneId

/** Pesées dans le dépôt nutrition (§ 3.11) : ajout, remplacement, suppression, persistance. */
class NutritionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-03-14T17:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val dataDir get() = File(tmp.root, "data")
    private var nextId = 0

    /** Synchronisations demandées : ids des rappels d'avant → ids des rappels attendus après. */
    private class RecordingReminders : SupplementReminders {
        val syncs = mutableListOf<Pair<Set<ReminderRef>, Set<ReminderRef>>>()
        override suspend fun sync(previous: List<Supplement>, latest: () -> List<Supplement>) {
            syncs += ReminderSchedule.refs(previous) to ReminderSchedule.expectedAlarms(latest()).mapTo(HashSet()) { it.ref }
        }
        override suspend fun onAlarm(ref: ReminderRef, latest: () -> List<Supplement>) = Unit
    }

    private val reminders = RecordingReminders()

    private suspend fun CoroutineScope.repository(): NutritionRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return NutritionRepository(files.bodyWeights, files.supplements, files.supplementsTaken, files.dailyTracker, time, reminders) { "id-${++nextId}" }
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

    // ─── Suivi du jour ────────────────────────────────────────────────────────────────

    @Test
    fun `calories et eau du jour s'ajoutent, se définissent, se remettent à zéro et s'enregistrent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.addCalories(600)
            repo.addCalories(400)
            repo.addWater(500)
            repo.addWater(250)
            assertEquals(mapOf("2026-03-14" to 1000), repo.tracker.value.caloriesLog)
            assertEquals(mapOf("2026-03-14" to 750), repo.tracker.value.waterLog)
            repo.setCaloriesToday(1800)
            repo.setWaterToday(1250)
            assertEquals(1800, repo.tracker.value.caloriesLog["2026-03-14"])
            assertEquals(1250, repo.tracker.value.waterLog["2026-03-14"])
            repo.setWaterToday(0)
            assertEquals(0, repo.tracker.value.waterLog["2026-03-14"])
        }
        coroutineScope {
            assertEquals(1800, repository().tracker.value.caloriesLog["2026-03-14"])
            assertEquals(0, repository().tracker.value.waterLog["2026-03-14"])
        }
    }

    @Test
    fun `le jour des journaux est le jour local au moment de la saisie`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            clock = Instant.parse("2026-03-14T22:59:00Z") // 23 h 59 à Paris
            repo.addWater(300)
            clock = Instant.parse("2026-03-14T23:01:00Z") // 0 h 01 le 15
            repo.addWater(200)
            assertEquals(mapOf("2026-03-14" to 300, "2026-03-15" to 200), repo.tracker.value.waterLog)
        }
    }

    @Test
    fun `un objectif change à partir d'aujourd'hui et un objectif nul est ignoré`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.setCalorieGoal(2400)
            repo.setWaterGoal(0)
            repo.setWaterGoal(2500)
            val tracker = repo.tracker.value
            assertEquals(2400, tracker.calorieGoal)
            assertEquals(mapOf("0000-01-01" to 2000, "2026-03-14" to 2400), tracker.calorieGoalHistory)
            assertEquals(mapOf("0000-01-01" to 2000, "2026-03-14" to 2500), tracker.waterGoalHistory)
        }
        coroutineScope { assertEquals(2500, repository().tracker.value.waterGoal) }
    }

    // ─── Compléments ──────────────────────────────────────────────────────────────────

    @Test
    fun `un complément créé est enregistré et ses alarmes programmées`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            assertEquals(null, repo.addSupplement("  ", "5", "g", true, listOf(8 to 0)))
            val crea = repo.addSupplement("Créatine", "5", "g", true, listOf(8 to 0, 20 to 30))!!
            assertEquals(listOf(Reminder("id-2", 8, 0), Reminder("id-3", 20, 30)), crea.reminders)
            assertEquals(
                listOf(emptySet<ReminderRef>() to setOf(ReminderRef("id-1", "id-2"), ReminderRef("id-1", "id-3"))),
                reminders.syncs,
            )
        }
        coroutineScope { assertEquals(listOf("Créatine"), repository().supplements.value.map { it.name }) }
    }

    @Test
    fun `modifier les rappels resynchronise les alarmes`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val crea = repo.addSupplement("Créatine", "5", "g", true, listOf(8 to 0))!!
            repo.addReminder(crea.id) // 10:00
            repo.updateReminder(crea.id, "id-3", 21, 15)
            assertEquals(listOf(8 to 0, 21 to 15), repo.supplements.value.single().reminders.map { it.hour to it.minute })
            repo.removeReminder(crea.id, "id-2")
            repo.setRemindersEnabled(crea.id, false)
            val supplement = repo.supplements.value.single()
            assertEquals(listOf(Reminder("id-3", 21, 15)), supplement.reminders) // conservé, désactivé
            // Dernière synchronisation : le rappel restant n'a plus d'alarme attendue.
            assertEquals(setOf(ReminderRef(crea.id, "id-3")) to emptySet<ReminderRef>(), reminders.syncs.last())
        }
    }

    @Test
    fun `« pris aujourd'hui » se bascule et disparaît avec le complément supprimé`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val whey = repo.addSupplement("Whey", "", "dose", false, emptyList())!!
            val crea = repo.addSupplement("Créatine", "5", "g", true, listOf(8 to 0))!!
            repo.toggleTaken(whey.id)
            repo.toggleTaken(crea.id)
            repo.toggleTaken(crea.id)
            assertEquals(mapOf(whey.id to "2026-03-14"), repo.taken.value)
            repo.deleteSupplement(whey.id)
            assertTrue(repo.taken.value.isEmpty())
            assertEquals(listOf("Créatine"), repo.supplements.value.map { it.name })
        }
        coroutineScope { assertTrue(repository().taken.value.isEmpty()) }
    }

    @Test
    fun `au chargement seules les prises d'aujourd'hui restent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val whey = repo.addSupplement("Whey", "", "dose", false, emptyList())!!
            repo.toggleTaken(whey.id)
        }
        clock = Instant.parse("2026-03-15T08:00:00Z")
        coroutineScope { assertTrue(repository().taken.value.isEmpty()) }
    }

    // ─── Historique des prises (§ 15.15.4, 🔄 D41) ───────────────────────────────────────

    @Test
    fun `cocher garde la prise du jour dans l'historique du complément, décocher la retire`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val crea = repo.addSupplement("Créatine", "5", "g", true, listOf(8 to 0))!!
            val syncsBefore = reminders.syncs.size
            repo.toggleTaken(crea.id)
            assertEquals(mapOf("2026-03-14" to 1), repo.supplements.value.single().intakes)
            // L'historique ne touche pas aux rappels : aucune resynchronisation des alarmes.
            assertEquals(syncsBefore, reminders.syncs.size)
            repo.toggleTaken(crea.id)
            assertTrue(repo.supplements.value.single().intakes.isEmpty())
            repo.toggleTaken(crea.id)
        }
        // Le lendemain : la prise de la veille est gardée, celle du jour s'ajoute.
        clock = Instant.parse("2026-03-15T08:00:00Z")
        coroutineScope {
            val repo = repository()
            assertEquals(mapOf("2026-03-14" to 1), repo.supplements.value.single().intakes)
            repo.toggleTaken(repo.supplements.value.single().id)
            assertEquals(mapOf("2026-03-14" to 1, "2026-03-15" to 1), repo.supplements.value.single().intakes)
        }
    }

    @Test
    fun `un jour sans prise n'ajoute rien à l'historique`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.addSupplement("Whey", "", "dose", false, emptyList())
            assertTrue(repo.supplements.value.single().intakes.isEmpty())
        }
        val written = File(dataDir, "supplements.json").readText()
        assertFalse("historique vide omis", "intakes" in written)
    }

    @Test
    fun `un ancien fichier sans historique se relit tel quel`() = runBlocking<Unit> {
        dataDir.mkdirs()
        File(dataDir, "supplements.json").writeText(
            """[{"id":"s1","name":"Oméga-3","dosage":"2","unit":"caps","reminderEnabled":true,"reminders":[{"id":"r1","hour":20,"minute":0}]}]""",
        )
        coroutineScope {
            val repo = repository()
            val supplement = repo.supplements.value.single()
            assertEquals(Supplement("s1", "Oméga-3", "2", "caps", true, listOf(Reminder("r1", 20, 0))), supplement)
            assertTrue(supplement.intakes.isEmpty())
            repo.toggleTaken("s1")
            assertEquals(mapOf("2026-03-14" to 1), repo.supplements.value.single().intakes)
        }
    }

    // ─── Protéines et stock (§ 15.16, 🔄 D42) ────────────────────────────────────────

    @Test
    fun `les protéines du jour et leur objectif s'enregistrent`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.addProtein(40)
            repo.addProtein(72)
            assertEquals(mapOf("2026-03-14" to 112), repo.tracker.value.proteinLog)
            repo.setProteinToday(90)
            repo.setProteinGoal(ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.8, grams = 150))
        }
        coroutineScope {
            val repo = repository()
            assertEquals(mapOf("2026-03-14" to 90), repo.tracker.value.proteinLog)
            assertEquals(ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.8, grams = 150), repo.tracker.value.proteinGoal)
            // Un réglage invalide est ramené aux bornes avant d'être enregistré.
            repo.setProteinGoal(ProteinGoal(ProteinGoalMode.PerKg, perKg = 4.0))
            assertEquals(ProteinGoal.DEFAULT, repo.tracker.value.proteinGoal)
        }
        assertFalse("objectif par défaut omis", "proteinGoal" in File(dataDir, "daily_tracker.json").readText())
    }

    @Test
    fun `cocher enregistre les doses par jour et décompte le stock, sans toucher aux alarmes`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            val whey = repo.addSupplement("Whey", "30", "g", true, listOf(8 to 0))!!
            val syncsBefore = reminders.syncs.size
            repo.setDosesPerDay(whey.id, 2)
            repo.setStock(whey.id, 25)
            repo.toggleTaken(whey.id)
            val supplement = repo.supplements.value.single()
            assertEquals(mapOf("2026-03-14" to 2), supplement.intakes)
            assertEquals(23, StockRules.status(supplement)!!.doses)
            assertEquals(11, StockRules.status(supplement)!!.daysLeft)
            // Une seule dose par jour désormais : la prise du jour suit.
            repo.setDosesPerDay(whey.id, 1)
            assertEquals(24, StockRules.status(repo.supplements.value.single())!!.doses)
            repo.restock(whey.id, 30)
            assertEquals(54, StockRules.status(repo.supplements.value.single())!!.doses)
            repo.setLowStockDays(whey.id, 10)
            assertEquals(syncsBefore, reminders.syncs.size)
        }
        coroutineScope {
            val repo = repository()
            val supplement = repo.supplements.value.single()
            assertEquals(SupplementStock(doses = 54, day = "2026-03-14", dayIntake = 1), supplement.stock)
            assertEquals(10, supplement.lowStockDays)
            repo.clearStock(supplement.id)
            assertNull(repo.supplements.value.single().stock)
        }
    }

    @Test
    fun `un ancien fichier se relit avec une dose par jour et sans stock`() = runBlocking<Unit> {
        dataDir.mkdirs()
        File(dataDir, "supplements.json").writeText(
            """[{"id":"s1","name":"Oméga-3","dosage":"2","unit":"caps","reminderEnabled":false,"reminders":[]}]""",
        )
        File(dataDir, "daily_tracker.json").writeText(
            """{"calorieGoal":2000,"waterGoal":2000,"calorieGoalHistory":{"0000-01-01":2000},"waterGoalHistory":{"0000-01-01":2000},"caloriesLog":{},"waterLog":{}}""",
        )
        coroutineScope {
            val repo = repository()
            val supplement = repo.supplements.value.single()
            assertEquals(1, supplement.dosesPerDay)
            assertNull(supplement.stock)
            assertEquals(7, supplement.lowStockDays)
            assertTrue(repo.tracker.value.proteinLog.isEmpty())
            assertEquals(ProteinGoal.DEFAULT, repo.tracker.value.proteinGoal)
        }
    }
}
