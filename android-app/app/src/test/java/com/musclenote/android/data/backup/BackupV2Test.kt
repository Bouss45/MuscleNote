package com.musclenote.android.data.backup

import com.musclenote.android.data.backup.BackupFixtures.reader
import com.musclenote.android.data.backup.BackupFixtures.resource
import com.musclenote.android.data.backup.BackupFixtures.success
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.ExerciseType
import com.musclenote.android.data.model.DailyTracker.Companion.GOAL_HISTORY_ORIGIN
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RemovedWorkoutType
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.SupplementStock
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
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
    fun `un thème invalide ou absent revient au thème sombre, un thème connu est gardé`() {
        assertEquals(ThemeMode.Dark, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "bleu" }""").preferences!!.theme)
        assertEquals(ThemeMode.Dark, readV2(""""@gymtracker_preferences": { "restSeconds": 90 }""").preferences!!.theme)
        assertEquals(ThemeMode.System, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "system" }""").preferences!!.theme)
        assertEquals(ThemeMode.Dark, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "dark" }""").preferences!!.theme)
        assertEquals(ThemeMode.Light, readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "light" }""").preferences!!.theme)
    }

    // ─── Types retirés de « Mes types » (§ 15.19.5, 🔄 D45) ───────────────────────

    private fun removedOf(json: String) =
        readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "dark"$json }""").preferences!!.removedWorkoutTypes

    @Test
    fun `les types retirés sont lus entrée par entrée, sans faire échouer l'import`() {
        assertEquals(emptyList<RemovedWorkoutType>(), removedOf(""))
        assertEquals(emptyList<RemovedWorkoutType>(), removedOf(""", "removedWorkoutTypes": null"""))
        assertEquals(emptyList<RemovedWorkoutType>(), removedOf(""", "removedWorkoutTypes": "Bras""""))
        assertEquals(
            listOf(RemovedWorkoutType("Mollets", "2026-11-10T09:00:00.000Z"), RemovedWorkoutType("bras / abdos", "2026-11-12T09:00:00.000Z")),
            removedOf(
                """, "removedWorkoutTypes": [ { "name": "Mollets", "removedAt": "2026-11-10T09:00:00.000Z" },""" +
                    """ { "name": "Bras / Abdos", "removedAt": "2026-11-11T09:00:00.000Z" }, { "name": "bras / abdos", "removedAt": "2026-11-12T09:00:00.000Z" },""" +
                    """ { "name": " ", "removedAt": "2026-11-12T09:00:00.000Z" }, { "name": "Dos", "removedAt": 12 }, { "name": "Dos" }, 3 ]""",
            ),
        )
        // Fichier de l'app Expo : aucun type retiré.
        assertEquals(emptyList<RemovedWorkoutType>(), success(reader().read(resource("v1_sample.json"))).data.preferences!!.removedWorkoutTypes)
    }

    @Test
    fun `l'export n'écrit les types retirés que s'il y en a, et les relit`() {
        fun preferencesJson(preferences: Preferences) =
            Json.parseToJsonElement(BackupWriter.write(BackupData(preferences = preferences), exportedAt)).jsonObject.getValue(BackupKeys.PREFERENCES).jsonObject
        assertFalse("removedWorkoutTypes" in preferencesJson(Preferences.DEFAULT))
        val custom = Preferences.DEFAULT.copy(removedWorkoutTypes = listOf(RemovedWorkoutType("Bras / Abdos", "2026-11-11T09:00:00.000Z")))
        val written = preferencesJson(custom).getValue("removedWorkoutTypes").jsonArray.single().jsonObject
        assertEquals("Bras / Abdos", written.getValue("name").jsonPrimitive.content)
        assertEquals("2026-11-11T09:00:00.000Z", written.getValue("removedAt").jsonPrimitive.content)
        assertEquals(custom, success(reader().read(BackupWriter.write(BackupData(preferences = custom), exportedAt))).data.preferences)
    }

    // ─── Pesée facultative et rappel de pesée (§ 15.13.5, 🔄 D38) ───────────────────

    private fun reminderOf(json: String) =
        readV2(""""@gymtracker_preferences": { "restSeconds": 90, "theme": "dark"$json }""").preferences!!.weighInReminder

    @Test
    fun `le rappel de pesée absent ou qui n'est pas un objet est le réglage par défaut`() {
        assertEquals(WeighInReminder.DEFAULT, reminderOf(""))
        assertEquals(WeighInReminder.DEFAULT, reminderOf(""", "weighInReminder": "7:30""""))
        assertEquals(WeighInReminder.DEFAULT, reminderOf(""", "weighInReminder": null"""))
        // Une sauvegarde v1 de l'app Expo n'en a pas.
        assertEquals(WeighInReminder.DEFAULT, success(reader().read(resource("v1_sample.json"))).data.preferences!!.weighInReminder)
    }

    @Test
    fun `le rappel de pesée est lu champ par champ, sans faire échouer l'import`() {
        assertEquals(
            WeighInReminder(true, 6, 45, listOf(1, 5)),
            reminderOf(""", "weighInReminder": { "enabled": true, "hour": 6, "minute": 45, "days": [5, 1, 1, 9, "x"] }"""),
        )
        // enabled non booléen → false ; heure hors limites → 7 h 30 ; jours absents → tous ; [] → aucun.
        assertEquals(WeighInReminder(false, 7, 30, listOf(1, 2, 3, 4, 5, 6, 7)), reminderOf(""", "weighInReminder": { "enabled": "oui", "hour": 24, "minute": 0 }"""))
        assertEquals(WeighInReminder(true, 7, 30, emptyList()), reminderOf(""", "weighInReminder": { "enabled": true, "days": [] }"""))
        assertEquals(WeighInReminder(true, 7, 30, listOf(1, 2, 3, 4, 5, 6, 7)), reminderOf(""", "weighInReminder": { "enabled": true, "days": "lun" }"""))
    }

    @Test
    fun `l'export n'écrit le rappel de pesée que s'il diffère du défaut, avec ses quatre champs`() {
        fun preferencesJson(preferences: Preferences) =
            Json.parseToJsonElement(BackupWriter.write(BackupData(preferences = preferences), exportedAt)).jsonObject.getValue(BackupKeys.PREFERENCES).jsonObject
        assertFalse("weighInReminder" in preferencesJson(Preferences.DEFAULT))
        val custom = Preferences.DEFAULT.copy(weighInReminder = WeighInReminder(enabled = true))
        val written = preferencesJson(custom).getValue("weighInReminder").jsonObject
        assertEquals(listOf("enabled", "hour", "minute", "days"), written.keys.toList())
        assertEquals(custom, success(reader().read(BackupWriter.write(BackupData(preferences = custom), exportedAt))).data.preferences)
    }

    @Test
    fun `une séance sans poids garde bodyWeight 0 à l'export, un poids absent ou null se relit à 0`() {
        val skipped = Workout(id = "w", name = "Push", date = "2026-03-01T10:00:00.000Z", bodyWeight = 0.0, exercises = emptyList())
        val written = Json.parseToJsonElement(BackupWriter.write(BackupData(workouts = listOf(skipped)), exportedAt))
            .jsonObject.getValue(BackupKeys.WORKOUTS).jsonArray[0].jsonObject
        assertEquals(0.0, written.getValue("bodyWeight").jsonPrimitive.content.toDouble(), 0.0)
        val workouts = readV2(
            """"@gymtracker_workouts": [
               { "id": "a", "name": "A", "date": "2026-03-01T10:00:00.000Z", "exercises": [] },
               { "id": "b", "name": "B", "date": "2026-03-02T10:00:00.000Z", "bodyWeight": null, "exercises": [] } ]""",
        ).workouts!!
        assertEquals(listOf(0.0, 0.0), workouts.map { it.bodyWeight })
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

    // ─── Historique des prises (§ 15.15.4, 🔄 D41) ───────────────────────────────────

    private fun intakesOf(json: String) =
        readV2(
            """"@gymtracker_supplements": [ { "id": "s1", "name": "Créatine", "dosage": "5", "unit": "g",
               "reminderEnabled": false, "reminders": []$json } ]""",
        ).supplements!!.single().intakes

    @Test
    fun `l'historique des prises est lu, ses entrées invalides écartées`() {
        assertEquals(
            mapOf("2026-11-14" to 1, "2026-11-15" to 2),
            intakesOf(""", "intakes": { "2026-11-14": 1, "2026-11-15": 2, "2026-02-30": 1, "hier": 1, "2026-11-13": 0, "2026-11-12": -1, "2026-11-11": "1", "2026-11-10": 1.5 }"""),
        )
    }

    @Test
    fun `un complément sans historique, ou dont l'historique n'est pas un objet, a un historique vide`() {
        assertTrue(intakesOf("").isEmpty())
        assertTrue(intakesOf(""", "intakes": [ "2026-11-14" ]""").isEmpty())
        assertTrue(intakesOf(""", "intakes": null""").isEmpty())
        // Une sauvegarde v1 de l'app Expo n'en a pas.
        assertTrue(success(reader().read(resource("v1_sample.json"))).data.supplements!!.all { it.intakes.isEmpty() })
    }

    @Test
    fun `l'export n'écrit l'historique des prises que s'il existe, et le relit à l'identique`() {
        val plain = Supplement("s1", "Créatine", "5", "g", false, emptyList())
        val withHistory = plain.copy(id = "s2", intakes = mapOf("2026-11-14" to 1, "2026-11-15" to 2))
        val text = BackupWriter.write(BackupData(supplements = listOf(plain, withHistory)), exportedAt)
        val written = Json.parseToJsonElement(text).jsonObject.getValue(BackupKeys.SUPPLEMENTS).jsonArray
        assertFalse("intakes" in written[0].jsonObject)
        assertTrue("intakes" in written[1].jsonObject)
        assertEquals(listOf(plain, withHistory), success(reader().read(text)).data.supplements)
    }

    // ─── Protéines et stock (§ 15.16.8, 🔄 D42) ──────────────────────────────────────

    private fun supplementOf(json: String) =
        readV2(
            """"@gymtracker_supplements": [ { "id": "s1", "name": "Créatine", "dosage": "5", "unit": "g",
               "reminderEnabled": false, "reminders": []$json } ]""",
        ).supplements!!.single()

    @Test
    fun `les protéines et leur objectif sont lus`() {
        val tracker = readV2(
            trackerJson(
                """"proteinLog": { "2026-11-14": 140, "2026-11-15": 112.4, "hier": 50, "2026-11-13": -20, "2026-02-30": 10 },
                   "proteinGoal": { "mode": "fixed", "perKg": 1.8, "grams": 150 }""",
            ),
        ).dailyTracker!!
        assertEquals(mapOf("2026-11-13" to 0, "2026-11-14" to 140, "2026-11-15" to 112), tracker.proteinLog)
        assertEquals(ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.8, grams = 150), tracker.proteinGoal)
    }

    @Test
    fun `un objectif de protéines absent ou invalide revient au défaut`() {
        fun goalOf(json: String) = readV2(trackerJson(json)).dailyTracker!!.proteinGoal
        assertEquals(ProteinGoal.DEFAULT, goalOf(""""calorieGoal": 2000"""))
        assertEquals(ProteinGoal.DEFAULT, goalOf(""""proteinGoal": 150"""))
        assertEquals(ProteinGoal.DEFAULT, goalOf(""""proteinGoal": { "mode": "auto", "perKg": 9 }"""))
        assertEquals(ProteinGoal(ProteinGoalMode.PerKg, perKg = 1.7), goalOf(""""proteinGoal": { "perKg": 1.66 }"""))
        // Valeur fixe sans grammes valables : le mode par kg, avec son facteur.
        assertEquals(ProteinGoal(ProteinGoalMode.PerKg, perKg = 2.0), goalOf(""""proteinGoal": { "mode": "fixed", "perKg": 2.0, "grams": 0 }"""))
        assertEquals(ProteinGoal(ProteinGoalMode.PerKg, perKg = 1.6, grams = 120), goalOf(""""proteinGoal": { "mode": "perKg", "grams": 120 }"""))
        // Une sauvegarde d'avant (ou v1 de l'app Expo) n'a ni protéines ni objectif.
        val v1 = success(reader().read(resource("v1_sample.json"))).data.dailyTracker!!
        assertTrue(v1.proteinLog.isEmpty())
        assertEquals(ProteinGoal.DEFAULT, v1.proteinGoal)
    }

    @Test
    fun `le stock, les doses par jour et le seuil d'un complément sont lus`() {
        val supplement = supplementOf(
            """, "intakes": { "2026-11-14": 2 }, "dosesPerDay": 2, "lowStockDays": 10,
               "stock": { "doses": 24, "day": "2026-11-12", "dayIntake": 1 }""",
        )
        assertEquals(2, supplement.dosesPerDay)
        assertEquals(10, supplement.lowStockDays)
        assertEquals(SupplementStock(doses = 24, day = "2026-11-12", dayIntake = 1), supplement.stock)
    }

    @Test
    fun `un ancien complément, ou des valeurs invalides, prennent les défauts`() {
        val old = supplementOf("")
        assertEquals(Supplement.DEFAULT_DOSES_PER_DAY, old.dosesPerDay)
        assertEquals(Supplement.DEFAULT_LOW_STOCK_DAYS, old.lowStockDays)
        assertNull(old.stock)
        val invalid = supplementOf(""", "dosesPerDay": 0, "lowStockDays": 120""")
        assertEquals(1, invalid.dosesPerDay)
        assertEquals(7, invalid.lowStockDays)
        assertEquals(1, supplementOf(""", "dosesPerDay": 21""").dosesPerDay)
        assertEquals(1, supplementOf(""", "dosesPerDay": "2"""").dosesPerDay)
        assertNull(supplementOf(""", "stock": 24""").stock)
        assertNull(supplementOf(""", "stock": { "doses": -1, "day": "2026-11-12" }""").stock)
        assertNull(supplementOf(""", "stock": { "doses": 24, "day": "hier" }""").stock)
        assertNull(supplementOf(""", "stock": { "day": "2026-11-12" }""").stock)
        assertEquals(SupplementStock(24, "2026-11-12"), supplementOf(""", "stock": { "doses": 24, "day": "2026-11-12", "dayIntake": -3 }""").stock)
    }

    @Test
    fun `l'export omet les champs nouveaux à leur défaut et les relit à l'identique`() {
        val plain = Supplement("s1", "Créatine", "5", "g", false, emptyList())
        val tracked = plain.copy(id = "s2", dosesPerDay = 2, lowStockDays = 10, stock = SupplementStock(24, "2026-11-12", dayIntake = 1))
        val tracker = DailyTracker.DEFAULT.copy(proteinLog = mapOf("2026-11-14" to 140), proteinGoal = ProteinGoal(ProteinGoalMode.Fixed, 1.8, 150))
        val text = BackupWriter.write(BackupData(supplements = listOf(plain, tracked), dailyTracker = tracker), exportedAt)
        val root = Json.parseToJsonElement(text).jsonObject
        val written = root.getValue(BackupKeys.SUPPLEMENTS).jsonArray
        for (key in listOf("dosesPerDay", "lowStockDays", "stock")) {
            assertFalse(key, key in written[0].jsonObject)
            assertTrue(key, key in written[1].jsonObject)
        }
        val defaultTracker = Json.parseToJsonElement(BackupWriter.write(BackupData(dailyTracker = DailyTracker.DEFAULT), exportedAt))
            .jsonObject.getValue(BackupKeys.DAILY_TRACKER).jsonObject
        assertFalse("proteinLog" in defaultTracker)
        assertFalse("proteinGoal" in defaultTracker)
        val reread = success(reader().read(text)).data
        assertEquals(listOf(plain, tracked), reread.supplements)
        assertEquals(tracker.proteinLog, reread.dailyTracker!!.proteinLog)
        assertEquals(tracker.proteinGoal, reread.dailyTracker!!.proteinGoal)
    }

    // ─── Muscles des exercices perso (§ 15.17.8, 🔄 D43) ────────────────────────

    private fun exercisesOf(items: String) = readV2(""""@gymtracker_exercises": [ $items ]""").exercises!!

    @Test
    fun `les muscles d'un exercice perso sont lus`() {
        val exercise = exercisesOf(
            """{ "id": "c1", "name": "Hip thrust", "muscleGroup": "jambes",
                 "primaryMuscles": ["fessiers"], "secondaryMuscles": ["ischio-jambiers", "adducteurs"] }""",
        ).single()
        assertEquals(listOf(Muscle.Fessiers), exercise.primaryMuscles)
        assertEquals(listOf(Muscle.IschioJambiers, Muscle.Adducteurs), exercise.secondaryMuscles)
    }

    @Test
    fun `un ancien exercice perso se lit sans muscle`() {
        val exercise = exercisesOf("""{ "id": "c1", "name": "Shrug", "muscleGroup": "dos" }""").single()
        assertEquals(emptyList<Muscle>(), exercise.primaryMuscles)
        assertEquals(emptyList<Muscle>(), exercise.secondaryMuscles)
    }

    @Test
    fun `des muscles invalides sont écartés sans faire échouer l'import`() {
        val exercises = exercisesOf(
            """{ "id": "c1", "name": "A", "muscleGroup": "dos",
                 "primaryMuscles": ["triceps", "inconnu", "pectoraux", "triceps", 3, null], "secondaryMuscles": "biceps" },
               { "id": "c2", "name": "B", "muscleGroup": "dos",
                 "primaryMuscles": ["biceps"], "secondaryMuscles": ["biceps", "avant-bras", "Biceps"] },
               { "id": "c3", "name": "C", "muscleGroup": "dos", "primaryMuscles": {}, "secondaryMuscles": null }""",
        )
        assertEquals(listOf("c1", "c2", "c3"), exercises.map { it.id })
        // Ordre de la liste des muscles, doublons et ids inconnus retirés ; pas un tableau → vide.
        assertEquals(listOf(Muscle.Pectoraux, Muscle.Triceps), exercises[0].primaryMuscles)
        assertEquals(emptyList<Muscle>(), exercises[0].secondaryMuscles)
        // Un muscle des deux listes reste principal.
        assertEquals(listOf(Muscle.Biceps), exercises[1].primaryMuscles)
        assertEquals(listOf(Muscle.AvantBras), exercises[1].secondaryMuscles)
        assertEquals(emptyList<Muscle>(), exercises[2].primaryMuscles + exercises[2].secondaryMuscles)
    }

    @Test
    fun `l'export omet les muscles vides et relit les autres à l'identique`() {
        val plain = Exercise("c1", "Shrug", MuscleGroup.Dos)
        val withMuscles = Exercise(
            "c2", "Hip thrust", MuscleGroup.Jambes,
            primaryMuscles = listOf(Muscle.Fessiers), secondaryMuscles = listOf(Muscle.IschioJambiers),
        )
        val text = BackupWriter.write(BackupData(exercises = listOf(plain, withMuscles)), exportedAt)
        val written = Json.parseToJsonElement(text).jsonObject.getValue(BackupKeys.EXERCISES).jsonArray
        assertEquals(listOf("id", "name", "muscleGroup"), written[0].jsonObject.keys.toList())
        assertEquals(
            listOf("fessiers"),
            written[1].jsonObject.getValue("primaryMuscles").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("ischio-jambiers"),
            written[1].jsonObject.getValue("secondaryMuscles").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf(plain, withMuscles), success(reader().read(text)).data.exercises)
    }

    // ─── Exercices au temps (§ 15.18.8, 🔄 D44) ─────────────────────────────────

    private fun setsOf(sets: String) = readV2(
        """"@gymtracker_workouts": [ { "id": "w1", "name": "Abdos", "date": "2026-11-12T17:00:00.000Z", "bodyWeight": 0,
             "exercises": [ { "exerciseId": "plank", "sets": [ $sets ] } ] } ]""",
    ).workouts!!.single().exercises.single().sets

    @Test
    fun `l'exemple de la SPEC, sans les champs nouveaux, se relit sans durée ni type`() {
        assertTrue(specData.workouts!!.flatMap { w -> w.exercises.flatMap { it.sets } }.all { it.durationSeconds == null })
        assertTrue(specData.exercises!!.all { it.type == ExerciseType.Reps })
    }

    @Test
    fun `la durée en secondes d'une série est lue, arrondie, jamais négative`() {
        val sets = setsOf(
            """{ "id": "a", "reps": 0, "weight": 10, "completed": true, "durationSeconds": 75 },
               { "id": "b", "reps": 0, "weight": 0, "completed": true, "durationSeconds": 62.5 },
               { "id": "c", "reps": 0, "weight": 0, "completed": true, "durationSeconds": -4 },
               { "id": "d", "reps": 0, "weight": 0, "completed": true, "durationSeconds": "1:30" },
               { "id": "e", "reps": 0, "weight": 0, "completed": true, "durationSeconds": null },
               { "id": "f", "reps": 12, "weight": 0, "completed": true }""",
        )
        assertEquals(listOf(75, 63, 0, null, null, null), sets.map { it.durationSeconds })
        assertEquals(10.0, sets[0].weight, 0.0)
        assertEquals(12, sets[5].reps)
    }

    @Test
    fun `le type d'un exercice perso est lu, inconnu ou absent il vaut charge × reps`() {
        val exercises = exercisesOf(
            """{ "id": "c1", "name": "Tenue", "muscleGroup": "épaules", "type": "timed" },
               { "id": "c2", "name": "Shrug", "muscleGroup": "dos" },
               { "id": "c3", "name": "Futur", "muscleGroup": "dos", "type": "distance" },
               { "id": "c4", "name": "Bizarre", "muscleGroup": "dos", "type": 3 }""",
        )
        assertEquals(listOf(ExerciseType.Timed, ExerciseType.Reps, ExerciseType.Reps, ExerciseType.Reps), exercises.map { it.type })
    }

    @Test
    fun `l'export omet la durée absente et le type par défaut, et relit le reste à l'identique`() {
        val plank = WorkoutSet("h1", 0, 10.0, true, durationSeconds = 60)
        val bench = WorkoutSet("s1", 8, 80.0, true)
        val workout = Workout("w1", "Push", null, "2026-11-12T17:00:00.000Z", 0.0, listOf(WorkoutExercise("plank", sets = listOf(plank, bench))), 45)
        val timed = Exercise("c1", "Tenue", MuscleGroup.Epaules, type = ExerciseType.Timed)
        val plain = Exercise("c2", "Shrug", MuscleGroup.Dos)
        val text = BackupWriter.write(BackupData(workouts = listOf(workout), exercises = listOf(timed, plain)), exportedAt)
        val root = Json.parseToJsonElement(text).jsonObject
        val sets = root.getValue(BackupKeys.WORKOUTS).jsonArray[0].jsonObject.getValue("exercises").jsonArray[0].jsonObject.getValue("sets").jsonArray
        assertEquals("60", sets[0].jsonObject.getValue("durationSeconds").jsonPrimitive.content)
        assertFalse("durationSeconds" in sets[1].jsonObject)
        val exercises = root.getValue(BackupKeys.EXERCISES).jsonArray
        assertEquals("timed", exercises[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("id", "name", "muscleGroup"), exercises[1].jsonObject.keys.toList())
        val reread = success(reader().read(text)).data
        assertEquals(listOf(workout), reread.workouts)
        assertEquals(listOf(timed, plain), reread.exercises)
    }
}
