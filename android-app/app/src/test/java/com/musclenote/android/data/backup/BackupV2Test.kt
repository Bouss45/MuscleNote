package com.musclenote.android.data.backup

import com.musclenote.android.data.backup.BackupFixtures.reader
import com.musclenote.android.data.backup.BackupFixtures.resource
import com.musclenote.android.data.backup.BackupFixtures.success
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.DailyTracker.Companion.GOAL_HISTORY_ORIGIN
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.ThemeMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Format v2 : exemple de la SPEC, export, aller-retour et normalisation (§ 5.2, § 5.3). */
class BackupV2Test {

    private val specDay: LocalDate = LocalDate.of(2026, 11, 11)
    private val specData = success(reader(today = specDay).read(resource("v2_spec_example.json"))).data
    private val exportedAt: Instant = Instant.parse("2026-11-11T18:42:07.123Z")

    // ─── Exemple de la SPEC (§ 5.2) ─────────────────────────────────────────────

    @Test
    fun `l'exemple v2 de la SPEC est lu sans perte`() {
        val result = success(reader(today = specDay).read(resource("v2_spec_example.json")))
        assertEquals(2, result.sourceVersion)
        val push = specData.workouts!![0]
        assertEquals(PresetId.Push, push.preset)
        assertEquals(72, push.durationMinutes)
        assertEquals("Prise un peu plus serrée", push.exercises[0].note)
        // En v2, une série non validée le reste.
        assertEquals(listOf(true, false), push.exercises[0].sets.map { it.completed })
        assertEquals(3.2, push.exercises[2].sets[0].distance!!, 0.0)
        // Pas de déduction de préréglage en v2 : seul le champ `preset` compte.
        assertNull(specData.workouts!![1].preset)
        assertTrue(specData.exercises!!.single { it.name == "Shrug" }.archived)
        assertEquals(listOf(8, 20), specData.supplements!![0].reminders.map { it.hour })
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 2500, "2026-11-01" to 2800), specData.dailyTracker!!.calorieGoalHistory)
        assertEquals(Preferences(150, ThemeMode.System), specData.preferences)
    }

    @Test
    fun `un export relu redonne exactement les mêmes données`() {
        val written = BackupWriter.write(specData, exportedAt)
        val reread = success(reader(today = specDay).read(written))
        assertEquals(2, reread.sourceVersion)
        assertEquals(specData, reread.data)
    }

    @Test
    fun `la migration v1 puis l'export v2 sont stables`() {
        val fromV1 = success(reader().read(resource("v1_sample.json"))).data
        val reread = success(reader().read(BackupWriter.write(fromV1, exportedAt))).data
        assertEquals(fromV1, reread)
    }

    // ─── Forme du fichier exporté (§ 5.2, § 5.6) ────────────────────────────────

    @Test
    fun `l'export commence par version et exportedAt puis toutes les clés dans l'ordre`() {
        val text = BackupWriter.write(specData, exportedAt)
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(listOf("version", "exportedAt") + BackupKeys.ALL, root.keys.toList())
        assertEquals("2", root.getValue("version").jsonPrimitive.content)
        assertEquals("2026-11-11T18:42:07.123Z", root.getValue("exportedAt").jsonPrimitive.content)
        assertTrue("indentation de 2 espaces", text.startsWith("{\n  \"version\": 2,\n  \"exportedAt\": "))
    }

    @Test
    fun `un export vide contient toutes les clés avec des documents vides`() {
        val text = BackupWriter.write(BackupData(), exportedAt)
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(listOf("version", "exportedAt") + BackupKeys.ALL, root.keys.toList())
        assertEquals(JsonArray(emptyList()), root.getValue(BackupKeys.WORKOUTS))
        assertEquals(JsonObject(emptyMap()), root.getValue(BackupKeys.SUPPLEMENTS_TAKEN))

        val reread = success(reader().read(text)).data
        assertEquals(emptyList<Any>(), reread.workouts)
        assertEquals(DailyTracker.DEFAULT, reread.dailyTracker)
        assertEquals(Preferences.DEFAULT, reread.preferences)
    }

    @Test
    fun `les champs optionnels absents sont omis et jamais null`() {
        val text = BackupWriter.write(specData, exportedAt)
        assertFalse(text.contains("null"))
        val root = Json.parseToJsonElement(text).jsonObject
        val workouts = root.getValue(BackupKeys.WORKOUTS).jsonArray
        assertFalse("preset" in workouts[1].jsonObject)
        val firstExercise = workouts[0].jsonObject.getValue("exercises").jsonArray[0].jsonObject
        assertEquals(listOf("exerciseId", "note", "sets"), firstExercise.keys.toList())
        val strengthSet = firstExercise.getValue("sets").jsonArray[0].jsonObject
        assertEquals(listOf("id", "reps", "weight", "completed"), strengthSet.keys.toList())
        val exercises = root.getValue(BackupKeys.EXERCISES).jsonArray
        assertFalse("archived" in exercises[0].jsonObject)
        assertEquals("true", exercises[1].jsonObject.getValue("archived").jsonPrimitive.content)
    }

    // ─── Normalisation v2 (§ 5.3) ───────────────────────────────────────────────

    private fun readV2(body: String) = success(reader().read("""{ "version": 2, $body }""")).data

    private fun trackerJson(fields: String) =
        """"@gymtracker_daily_tracker": { "caloriesLog": {}, "waterLog": {}, $fields }"""

    @Test
    fun `un préréglage inconnu est retiré`() {
        val workout = readV2(
            """"@gymtracker_workouts": [ { "id": "w1", "name": "Chest day", "preset": "chest",
               "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80, "exercises": [] } ]""",
        ).workouts!!.single()
        assertNull(workout.preset)
        assertEquals("Chest day", workout.name)
    }

    @Test
    fun `une série v2 sans champ completed n'est pas validée`() {
        val set = readV2(
            """"@gymtracker_workouts": [ { "id": "w1", "name": "", "date": "2026-03-01T10:00:00.000Z",
               "bodyWeight": 80, "exercises": [ { "exerciseId": "bench", "sets": [ { "id": "s1", "reps": 8, "weight": 60 } ] } ] } ]""",
        ).workouts!!.single().exercises.single().sets.single()
        assertFalse(set.completed)
    }

    @Test
    fun `des séances en double ne gardent que la première`() {
        val workouts = readV2(
            """"@gymtracker_workouts": [
               { "id": "w1", "name": "A", "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80, "exercises": [] },
               { "id": "w1", "name": "B", "date": "2026-03-02T10:00:00.000Z", "bodyWeight": 80, "exercises": [] } ]""",
        ).workouts!!
        assertEquals(listOf("A"), workouts.map { it.name })
    }

    @Test
    fun `un thème invalide revient au thème système`() {
        assertEquals(ThemeMode.System, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "bleu" }""").preferences!!.theme)
        assertEquals(ThemeMode.Dark, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "dark" }""").preferences!!.theme)
        assertEquals(ThemeMode.Light, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "light" }""").preferences!!.theme)
    }

    @Test
    fun `un objectif courant différent de l'historique devient l'objectif du jour`() {
        val tracker = readV2(
            trackerJson(
                """"calorieGoal": 2800, "waterGoal": 2000,
                   "calorieGoalHistory": { "0000-01-01": 2500 }, "waterGoalHistory": { "0000-01-01": 2000 }""",
            ),
        ).dailyTracker!!
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 2500, "2026-03-14" to 2800), tracker.calorieGoalHistory)
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 2000), tracker.waterGoalHistory)
        assertEquals(2800, tracker.calorieGoal)
    }

    @Test
    fun `un historique d'objectif absent ou vide part de l'objectif courant`() {
        val tracker = readV2(trackerJson(""""calorieGoal": 2300, "waterGoal": 3000, "calorieGoalHistory": {}""")).dailyTracker!!
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 2300), tracker.calorieGoalHistory)
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 3000), tracker.waterGoalHistory)
    }

    @Test
    fun `un historique sans entrée d'origine la reçoit`() {
        val tracker = readV2(
            trackerJson(""""calorieGoal": 2300, "waterGoal": 2000, "calorieGoalHistory": { "2026-01-01": 2300 }"""),
        ).dailyTracker!!
        assertEquals(mapOf(GOAL_HISTORY_ORIGIN to 2300, "2026-01-01" to 2300), tracker.calorieGoalHistory)
    }

    @Test
    fun `un objectif courant absent est celui en vigueur aujourd'hui`() {
        val tracker = readV2(
            trackerJson(""""calorieGoalHistory": { "0000-01-01": 2100, "2026-03-01": 2400, "2026-04-01": 2600 }"""),
        ).dailyTracker!!
        assertEquals(2400, tracker.calorieGoal)
        assertEquals(DailyTracker.DEFAULT_WATER_GOAL, tracker.waterGoal)
    }

    @Test
    fun `les journaux ne gardent que des jours valides et des entiers positifs`() {
        val tracker = readV2(
            """"@gymtracker_daily_tracker": { "calorieGoal": 2000, "waterGoal": 2000,
               "caloriesLog": { "2026-03-14": 1200.4, "hier": 500, "2026-03-13": "x", "2026-03-12": -50, "2026-02-30": 10 },
               "waterLog": { "2026-03-14": 750 } }""",
        ).dailyTracker!!
        assertEquals(mapOf("2026-03-12" to 0, "2026-03-14" to 1200), tracker.caloriesLog)
        assertEquals(mapOf("2026-03-14" to 750), tracker.waterLog)
    }

    @Test
    fun `les prises d'un complément inconnu sont retirées`() {
        val data = readV2(
            """"@gymtracker_supplements": [ { "id": "s1", "name": "Créatine", "dosage": "5", "unit": "g",
               "reminderEnabled": false, "reminders": [] } ],
               "@gymtracker_supplements_taken": { "s1": "2026-03-10", "s2": "2026-03-14", "s3": 12 }""",
        )
        // En v2, seule l'appartenance compte ; le filtre « aujourd'hui » se fait au chargement.
        assertEquals(mapOf("s1" to "2026-03-10"), data.supplementsTaken)
    }

    @Test
    fun `sans liste de compléments dans le fichier les prises sont gardées`() {
        val data = readV2(""""@gymtracker_supplements_taken": { "s2": "2026-03-14" }""")
        assertEquals(mapOf("s2" to "2026-03-14"), data.supplementsTaken)
    }

    @Test
    fun `les rappels inutilisables sont écartés et les autres complétés`() {
        val supplement = readV2(
            """"@gymtracker_supplements": [ { "id": "s1", "name": "Zinc", "dosage": 15, "reminderEnabled": true,
               "reminders": [ { "hour": 7, "minute": 45 }, { "id": "r2", "hour": 24, "minute": 0 },
                              { "id": "r3", "hour": 12, "minute": 60 }, "r4", { "id": "r5", "hour": 22, "minute": 0 } ] } ]""",
        ).supplements!!.single()
        assertEquals("15", supplement.dosage)
        assertEquals("g", supplement.unit)
        assertEquals(listOf(7 to 45, 22 to 0), supplement.reminders.map { it.hour to it.minute })
        assertTrue(supplement.reminders[0].id.isNotEmpty())
        assertEquals("r5", supplement.reminders[1].id)
    }
}
