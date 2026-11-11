package com.musclenote.android.data.repository

import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.backup.BackupFixtures
import com.musclenote.android.data.backup.BackupReadResult
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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

/** Chargement des dépôts depuis le disque, import et export (§ 3.4, § 3.12, § 4.2, § 5.4). */
class RepositoryLoadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-03-14T12:00:00Z")
    private val time = object : TimeSource {
        override fun now() = now
        override fun zone(): ZoneId = BackupFixtures.ZONE
    }

    private val dataDir get() = File(tmp.root, "data")

    private fun container() = AppContainer(dataDir, time)

    private fun currentWorkout(startedAt: Instant, completed: Boolean) = Workout(
        id = "current",
        name = "Push",
        date = startedAt.toString(),
        bodyWeight = 80.0,
        exercises = listOf(WorkoutExercise("bench", sets = listOf(WorkoutSet("s1", 8, 60.0, completed)))),
    )

    private fun writeCurrent(workout: Workout) {
        dataDir.mkdirs()
        File(dataDir, "current_workout.json").writeText(Json.encodeToString(Workout.serializer(), workout))
    }

    @Test
    fun `une séance en cours récente est gardée`() = runBlocking<Unit> {
        writeCurrent(currentWorkout(now.minusSeconds(3_600), completed = false))
        val c = container().also { it.awaitReady() }
        assertEquals("current", c.workouts.currentWorkout.value?.id)
        assertTrue(c.workouts.workouts.value.isEmpty())
    }

    @Test
    fun `une séance de plus de 24 h avec une série validée passe dans l'historique sans durée`() = runBlocking<Unit> {
        writeCurrent(currentWorkout(now.minusSeconds(25 * 3_600), completed = true))
        val c = container().also { it.awaitReady() }
        assertNull(c.workouts.currentWorkout.value)
        val finalized = c.workouts.workouts.value.single()
        assertEquals("current", finalized.id)
        assertNull(finalized.durationMinutes)
    }

    @Test
    fun `une séance de plus de 24 h sans série validée est abandonnée`() = runBlocking<Unit> {
        writeCurrent(currentWorkout(now.minusSeconds(25 * 3_600), completed = false))
        val c = container().also { it.awaitReady() }
        assertNull(c.workouts.currentWorkout.value)
        assertTrue(c.workouts.workouts.value.isEmpty())
    }

    @Test
    fun `l'import remplace les documents, recharge les dépôts et survit à un redémarrage`() = runBlocking<Unit> {
        val c = container().also { it.awaitReady() }
        val read = BackupFixtures.reader().read(BackupFixtures.resource("v1_sample.json"))
        c.importBackup((read as BackupReadResult.Success).data)

        assertEquals(7, c.workouts.workouts.value.size)
        assertEquals(6, c.workouts.customExercises.value.size)
        assertEquals(2, c.nutrition.bodyWeights.value.size)
        assertEquals(3, c.nutrition.supplements.value.size)
        assertEquals(150, c.preferences.preferences.value.restSeconds)

        // Nouveau processus : tout est relu depuis filesDir/data.
        val restarted = container().also { it.awaitReady() }
        assertEquals(c.workouts.workouts.value, restarted.workouts.workouts.value)
        assertEquals(c.nutrition.tracker.value, restarted.nutrition.tracker.value)
        assertEquals(mapOf("1739000000000sup00001" to "2026-03-14"), restarted.nutrition.taken.value)
        assertTrue(File(dataDir, "meta.json").readText().contains("\"schemaVersion\":2"))
    }

    @Test
    fun `l'export contient les données chargées`() = runBlocking<Unit> {
        val c = container().also { it.awaitReady() }
        val read = BackupFixtures.reader().read(BackupFixtures.resource("v1_sample.json"))
        c.importBackup((read as BackupReadResult.Success).data)
        val exported = Json.parseToJsonElement(c.exportBackupJson())
        val workouts = (exported as kotlinx.serialization.json.JsonObject).getValue("@gymtracker_workouts").jsonArray
        assertEquals(7, workouts.size)
    }

    @Test
    fun `au chargement seules les prises d'aujourd'hui de compléments existants restent`() = runBlocking<Unit> {
        dataDir.mkdirs()
        File(dataDir, "supplements.json").writeText(
            """[{"id":"s1","name":"Créatine","dosage":"5","unit":"g","reminderEnabled":false,"reminders":[]}]""",
        )
        File(dataDir, "supplements_taken.json").writeText("""{"s1":"2026-03-14","s2":"2026-03-14","s3":"2026-03-13"}""")
        val c = container().also { it.awaitReady() }
        assertEquals(mapOf("s1" to "2026-03-14"), c.nutrition.taken.value)
    }

    @Test
    fun `la carte de transfert masquée reste masquée, hors sauvegarde et malgré un import`() = runBlocking<Unit> {
        val c = container().also { it.awaitReady() }
        assertFalse(c.preferences.localPreferences.value.migrateCardDismissed)
        c.preferences.dismissMigrateCard()

        val read = BackupFixtures.reader().read(BackupFixtures.resource("v1_sample.json"))
        c.importBackup((read as BackupReadResult.Success).data)
        assertTrue(c.preferences.localPreferences.value.migrateCardDismissed)
        assertFalse(c.exportBackupJson().contains("migrateCardDismissed"))

        val restarted = container().also { it.awaitReady() }
        assertTrue(restarted.preferences.localPreferences.value.migrateCardDismissed)
    }
}
