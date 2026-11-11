package com.musclenote.android.data.backup

import com.musclenote.android.data.backup.BackupFixtures.TODAY
import com.musclenote.android.data.backup.BackupFixtures.reader
import com.musclenote.android.data.backup.BackupFixtures.resource
import com.musclenote.android.data.backup.BackupFixtures.success
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Migration v1 (app Expo) → modèle natif, sur la sauvegarde d'exemple `v1_sample.json` (§ 5.3). */
class BackupV1MigrationTest {

    private val result = success(reader().read(resource("v1_sample.json")))
    private val data = result.data
    private val workouts = data.workouts!!
    private fun workout(id: String) = workouts.single { it.id == id }

    @Test
    fun `la sauvegarde v1 est acceptée et toutes ses clés sont lues`() {
        assertEquals(1, result.sourceVersion)
        assertEquals(7, workouts.size)
        assertEquals(6, data.exercises!!.size)
        assertEquals(2, data.bodyWeights!!.size)
        assertEquals(3, data.supplements!!.size)
        assertTrue(data.supplementsTaken != null && data.dailyTracker != null && data.preferences != null)
    }

    @Test
    fun `les séances sont triées de la plus récente à la plus ancienne`() {
        assertEquals(
            listOf(
                "1742000000000wkt00007",
                "1741900000000wkt00006",
                "1741800000000wkt00005",
                "1741700000000wkt00004",
                "1741600000000wkt00003",
                "1741200000000wkt00002",
                "1740900000000wkt00001",
            ),
            workouts.map { it.id },
        )
    }

    @Test
    fun `les dates sont réécrites au format ISO avec millisecondes`() {
        assertEquals("2026-03-13T17:45:00.000Z", workout("1742000000000wkt00007").date)
        assertEquals("2026-03-02T17:00:00.000Z", workout("1740900000000wkt00001").date)
    }

    @Test
    fun `le préréglage est déduit du nom en français puis en anglais`() {
        assertEquals(PresetId.Push, workout("1740900000000wkt00001").preset) // « Push »
        assertEquals(PresetId.LegsSplit, workout("1741200000000wkt00002").preset) // « jambes  »
        assertEquals(PresetId.Legs, workout("1741600000000wkt00003").preset) // « Legs » → legs, pas legsSplit
        assertEquals(PresetId.Pecs, workout("1741700000000wkt00004").preset) // « Chest » (EN)
        assertEquals(PresetId.Shoulders, workout("1741800000000wkt00005").preset) // « Epaules » sans accent
        assertNull(workout("1741900000000wkt00006").preset) // nom absent
        assertNull(workout("1742000000000wkt00007").preset) // « Haut du corps »
        // Le nom d'origine est conservé.
        assertEquals("jambes ", workout("1741200000000wkt00002").name)
    }

    @Test
    fun `un nom ou un poids absent devient une chaîne vide ou 0`() {
        val noName = workout("1741900000000wkt00006")
        assertEquals("", noName.name)
        assertEquals(0.0, noName.bodyWeight, 0.0)
        assertNull(noName.durationMinutes)
        assertEquals(0.0, workout("1741600000000wkt00003").bodyWeight, 0.0) // bodyWeight: null
        assertEquals(65, workout("1740900000000wkt00001").durationMinutes)
    }

    @Test
    fun `toutes les anciennes séries sont marquées validées`() {
        val sets = workouts.flatMap { w -> w.exercises.flatMap { it.sets } }
        assertEquals(8, sets.size)
        assertTrue(sets.all { it.completed })
    }

    @Test
    fun `une série sans id reçoit un id et les répétitions sont arrondies`() {
        val bench = workout("1740900000000wkt00001").exercises.single { it.exerciseId == "bench" }
        val third = bench.sets[2]
        assertTrue(third.id.startsWith("gen-"))
        assertEquals(9, third.reps) // 8.6
        assertEquals(62.5, third.weight, 0.0)
        assertEquals("1740900000001set00011", bench.sets[0].id)
        val ids = workouts.flatMap { w -> w.exercises.flatMap { e -> e.sets.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `l'ordre des exercices, la note et les exercices perso inconnus sont conservés`() {
        val push = workout("1740900000000wkt00001")
        assertEquals(listOf("bench", "1740000000000exo00001"), push.exercises.map { it.exerciseId })
        assertEquals("Coudes serrés", push.exercises[0].note)
        assertNull(push.exercises[1].note)
    }

    @Test
    fun `les champs cardio null disparaissent et les autres sont gardés`() {
        val set = workout("1741900000000wkt00006").exercises.single().sets.single()
        assertEquals(25.0, set.duration!!, 0.0)
        assertEquals(4.1, set.distance!!, 0.0)
        assertEquals(9.8, set.speed!!, 0.0)
        assertNull(set.incline)
        val strength = workout("1741200000000wkt00002").exercises.single().sets.single()
        assertNull(strength.duration)

        val exported = BackupWriter.write(data, java.time.Instant.parse("2026-03-14T18:00:00Z"))
        assertFalse("aucun null ne doit être écrit", exported.contains("null"))
    }

    @Test
    fun `les groupes musculaires inconnus sont normalisés et les ids intégrés ignorés`() {
        val groups = data.exercises!!.associate { it.id to it.muscleGroup }
        assertEquals(MuscleGroup.Jambes, groups["1740000000000exo00001"])
        assertEquals(MuscleGroup.Epaules, groups["1740000000000exo00002"]) // « Epaules »
        assertEquals(MuscleGroup.Epaules, groups["1740000000000exo00003"]) // « shoulders »
        assertEquals(MuscleGroup.AvantBras, groups["1740000000000exo00004"]) // « avant bras »
        assertEquals(MuscleGroup.Dos, groups["1740000000000exo00005"]) // « mollets » : inconnu
        assertFalse("bench" in groups)
        assertTrue(data.exercises!!.single { it.id == "1740000000000exo00006" }.archived)
        assertFalse(data.exercises!!.single { it.id == "1740000000000exo00001" }.archived)
    }

    @Test
    fun `les pesées sont triées de la plus récente à la plus ancienne`() {
        assertEquals(listOf("2026-03-13T11:00:00.000Z", "2026-03-10T11:00:00.000Z"), data.bodyWeights!!.map { it.date })
        assertEquals(80.1, data.bodyWeights!![0].weight, 0.0)
    }

    @Test
    fun `un complément à l'ancien format reçoit une liste de rappels`() {
        val supplements = data.supplements!!.associateBy { it.id }

        val creatine = supplements.getValue("1739000000000sup00001")
        assertEquals("5", creatine.dosage)
        assertEquals("g", creatine.unit)
        assertTrue(creatine.reminderEnabled)
        assertEquals(1, creatine.reminders.size)
        assertEquals(8, creatine.reminders[0].hour)
        assertEquals(30, creatine.reminders[0].minute)
        assertTrue(creatine.reminders[0].id.startsWith("gen-"))

        val magnesium = supplements.getValue("1739000000000sup00002")
        assertEquals("", magnesium.dosage)
        assertEquals("g", magnesium.unit)
        assertFalse(magnesium.reminderEnabled)
        assertTrue(magnesium.reminders.isEmpty())
    }

    @Test
    fun `un complément au format récent garde ses rappels sans notifId`() {
        val vitaminD = data.supplements!!.single { it.id == "1739000000000sup00003" }
        assertEquals(
            listOf(Reminder("1739000000001rem00031", 9, 0), Reminder("1739000000002rem00032", 21, 15)),
            vitaminD.reminders,
        )
        val exported = BackupWriter.write(data, java.time.Instant.parse("2026-03-14T18:00:00Z"))
        assertFalse(exported.contains("notifId"))
        assertFalse(exported.contains("reminderHour"))
    }

    @Test
    fun `seules les prises d'aujourd'hui sont gardées`() {
        assertEquals(mapOf("1739000000000sup00001" to "2026-03-14"), data.supplementsTaken)
    }

    @Test
    fun `le suivi en verres est converti en millilitres et reçoit des objectifs datés`() {
        val tracker = data.dailyTracker!!
        assertEquals(2600, tracker.calorieGoal)
        assertEquals(2000, tracker.waterGoal) // 8 verres × 250
        assertEquals(mapOf("2026-03-13" to 1500, "2026-03-14" to 500), tracker.waterLog)
        assertEquals(mapOf("2026-03-13" to 2450, "2026-03-14" to 900), tracker.caloriesLog)
        assertEquals(mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to 2600), tracker.calorieGoalHistory)
        assertEquals(mapOf(DailyTracker.GOAL_HISTORY_ORIGIN to 2000), tracker.waterGoalHistory)
        assertFalse(BackupWriter.write(data, java.time.Instant.EPOCH).contains("bottleSize"))
    }

    @Test
    fun `les préférences perdent la langue et prennent le thème système`() {
        assertEquals(150, data.preferences!!.restSeconds)
        assertEquals(ThemeMode.System, data.preferences!!.theme)
        assertFalse(BackupWriter.write(data, java.time.Instant.EPOCH).contains("language"))
    }

    // ─── Cas v1 isolés ──────────────────────────────────────────────────────────

    private fun readV1(body: String) = success(reader().read("""{ "version": 1, $body }""")).data

    @Test
    fun `un suivi déjà en millilitres n'est pas converti`() {
        val tracker = readV1(
            """"@gymtracker_daily_tracker": { "calorieGoal": 2200, "waterGoal": 20, "bottleSize": 500,
               "caloriesLog": {}, "waterLog": { "2026-03-14": 20 } }""",
        ).dailyTracker!!
        assertEquals(20, tracker.waterGoal)
        assertEquals(mapOf("2026-03-14" to 20), tracker.waterLog)
    }

    @Test
    fun `un suivi vide prend les objectifs par défaut`() {
        assertEquals(DailyTracker.DEFAULT, readV1(""""@gymtracker_daily_tracker": {}""").dailyTracker)
    }

    @Test
    fun `une durée de repos invalide revient à 180 secondes`() {
        for (value in listOf("0", "-30", "\"90\"", "null", "0.2")) {
            val prefs = readV1(""""@gymtracker_preferences": { "restSeconds": $value }""").preferences!!
            assertEquals("restSeconds = $value", 180, prefs.restSeconds)
        }
    }

    @Test
    fun `un ancien complément activé sans heure prend 8 h 00`() {
        val supplement = readV1(""""@gymtracker_supplements": [ { "id": "s1", "name": "Oméga 3", "reminderEnabled": true } ]""")
            .supplements!!.single()
        assertEquals(8, supplement.reminders.single().hour)
        assertEquals(0, supplement.reminders.single().minute)
    }

    @Test
    fun `un fichier sans champ version est lu comme une v1`() {
        val result = success(
            reader().read(
                """{ "@gymtracker_workouts": [ { "id": "w1", "name": "Pull", "date": "2026-03-01T10:00:00.000Z",
                   "bodyWeight": 75, "exercises": [ { "exerciseId": "row", "sets": [ { "reps": 8, "weight": 50, "completed": false } ] } ] } ] }""",
            ),
        )
        assertEquals(1, result.sourceVersion)
        val workout = result.data.workouts!!.single()
        assertEquals(PresetId.Pull, workout.preset)
        assertTrue(workout.exercises.single().sets.single().completed)
        assertNull(result.data.exercises) // clé absente : document non touché à l'import
    }

    @Test
    fun `un préréglage déjà présent dans une v1 est conservé`() {
        val workout = readV1(
            """"@gymtracker_workouts": [ { "id": "w1", "name": "Legs", "preset": "legsSplit",
               "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 75, "exercises": [] } ]""",
        ).workouts!!.single()
        assertEquals(PresetId.LegsSplit, workout.preset)
    }

    @Test
    fun `aujourd'hui sert de référence pour les prises`() {
        val data = success(reader(today = TODAY.plusDays(1)).read(resource("v1_sample.json"))).data
        assertTrue(data.supplementsTaken!!.isEmpty())
    }
}
