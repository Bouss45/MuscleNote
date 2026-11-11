package com.musclenote.android.data.export

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Export CSV (§ 8) : format selon la langue, guillemets RFC 4180, séries validées seulement. */
class CsvExportTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today = LocalDate.parse("2026-03-14")

    private val frenchHeaders = mapOf(
        CsvColumn.Date to "Date", CsvColumn.Time to "Heure", CsvColumn.SessionName to "Nom de la séance",
        CsvColumn.Session to "Séance", CsvColumn.Duration to "Durée (min)", CsvColumn.BodyWeight to "Poids de corps (kg)",
        CsvColumn.Exercises to "Exercices", CsvColumn.TotalSets to "Séries totales", CsvColumn.TotalVolume to "Volume total (kg)",
        CsvColumn.Exercise to "Exercice", CsvColumn.MuscleGroup to "Groupe musculaire", CsvColumn.SetNumber to "Série n°",
        CsvColumn.Reps to "Répétitions", CsvColumn.Weight to "Poids (kg)", CsvColumn.Distance to "Distance (km)",
        CsvColumn.Speed to "Vitesse (km/h)", CsvColumn.Incline to "Inclinaison (%)", CsvColumn.Calories to "Calories (kcal)",
        CsvColumn.Water to "Eau (ml)",
    )

    private val texts = CsvTexts(
        header = { frenchHeaders.getValue(it) },
        workoutName = { it.name },
        exerciseName = { id -> mapOf("bench" to "Développé couché", "treadmill" to "Tapis roulant")[id] ?: id },
        muscleGroupName = { id -> mapOf("bench" to "Pecs", "treadmill" to "Cardio")[id].orEmpty() },
        isCardio = { it == "treadmill" },
    )

    private fun set(id: String, reps: Int, weight: Double, completed: Boolean = true) = WorkoutSet(id, reps, weight, completed)

    private val push = Workout(
        id = "w2",
        name = "Push",
        date = "2026-03-13T23:05:00.000Z", // samedi 14 mars, 0 h 05 à Paris
        bodyWeight = 78.5,
        exercises = listOf(
            WorkoutExercise("bench", sets = listOf(set("a", 8, 62.5, completed = false), set("b", 8, 62.5), set("c", 6, 70.0))),
            WorkoutExercise(
                "treadmill",
                sets = listOf(WorkoutSet("t", 0, 0.0, true, duration = 20.0, distance = 3.2, speed = 9.6, incline = 0.0)),
            ),
            WorkoutExercise("inconnu", sets = listOf(set("x", 10, 0.0))),
        ),
        durationMinutes = 62,
    )
    private val old = Workout("w1", "Jambes", null, "2026-03-10T17:00:00.000Z", 0.0, listOf(WorkoutExercise("bench", sets = listOf(set("z", 5, 80.0, completed = false)))), null)

    private fun lines(csv: String): List<String> {
        assertTrue("BOM UTF-8 en tête", csv.startsWith("﻿"))
        assertTrue("CRLF partout", csv.endsWith("\r\n") && !csv.replace("\r\n", "").contains('\n'))
        return csv.removePrefix("﻿").removeSuffix("\r\n").split("\r\n")
    }

    @Test
    fun `séances en français, point-virgule et virgule décimale, jour et heure locaux`() {
        val csv = CsvExport.sessions(listOf(push, old), texts, CsvFormat.FRENCH, zone)!!
        assertEquals(
            listOf(
                "Date;Heure;Nom de la séance;Durée (min);Poids de corps (kg);Exercices;Séries totales;Volume total (kg)",
                // 3 exercices avec une série validée ; 4 séries validées ; 8 × 62,5 + 6 × 70 = 920.
                "2026-03-14;00:05;Push;62;78,5;3;4;920",
                // Durée absente et poids 0 → champs vides ; aucune série validée.
                "2026-03-10;18:00;Jambes;;;0;0;0",
            ),
            lines(csv),
        )
    }

    @Test
    fun `en anglais, virgule et point décimal`() {
        val csv = CsvExport.sessions(listOf(push), texts, CsvFormat.ENGLISH, zone)!!
        assertEquals("2026-03-14,00:05,Push,62,78.5,3,4,920", lines(csv)[1])
    }

    @Test
    fun `les séries validées seulement, colonnes fixes et rang dans l'exercice`() {
        val csv = CsvExport.sets(listOf(push, old), texts, CsvFormat.FRENCH, zone)!!
        assertEquals(
            listOf(
                "Date;Séance;Exercice;Groupe musculaire;Série n°;Répétitions;Poids (kg);Durée (min);Distance (km);Vitesse (km/h);Inclinaison (%)",
                "2026-03-14;Push;Développé couché;Pecs;2;8;62,5;;;;",
                "2026-03-14;Push;Développé couché;Pecs;3;6;70;;;;",
                "2026-03-14;Push;Tapis roulant;Cardio;1;;;20;3,2;9,6;0",
                // Exercice inconnu : son id, groupe vide ; poids 0 → vide.
                "2026-03-14;Push;inconnu;;1;10;;;;;",
            ),
            lines(csv),
        )
    }

    @Test
    fun `guillemets RFC 4180 selon le séparateur de la langue`() {
        assertEquals("\"Haut; bas\"", CsvExport.field("Haut; bas", ';'))
        assertEquals("Haut, bas", CsvExport.field("Haut, bas", ';'))
        assertEquals("\"Haut, bas\"", CsvExport.field("Haut, bas", ','))
        assertEquals("\"Push \"\"lourd\"\"\"", CsvExport.field("Push \"lourd\"", ';'))
        assertEquals("\"ligne 1\nligne 2\"", CsvExport.field("ligne 1\nligne 2", ','))
        assertEquals("\"a\rb\"", CsvExport.field("a\rb", ';'))
        val named = push.copy(name = "Push; \"jour A\"")
        assertEquals("2026-03-14;00:05;\"Push; \"\"jour A\"\"\";62;78,5;3;4;920", lines(CsvExport.sessions(listOf(named), texts, CsvFormat.FRENCH, zone)!!)[1])
    }

    @Test
    fun `nombres sans séparateur de milliers ni zéros inutiles`() {
        assertEquals("80", CsvExport.number(80.0, CsvFormat.FRENCH))
        assertEquals("1234,5", CsvExport.number(1234.5, CsvFormat.FRENCH))
        assertEquals("1234.5", CsvExport.number(1234.5, CsvFormat.ENGLISH))
        assertEquals("99,9", CsvExport.number(3 * 33.3, CsvFormat.FRENCH)) // 99,899999…
        assertEquals("0", CsvExport.number(-0.0, CsvFormat.FRENCH))
        assertEquals("12500", CsvExport.number(12500.0, CsvFormat.ENGLISH))
    }

    @Test
    fun `pesées manuelles, la plus récente d'abord`() {
        val csv = CsvExport.bodyWeight(
            listOf(BodyWeightEntry("2026-03-14T11:00:00.000Z", 78.2), BodyWeightEntry("2026-03-08T11:00:00.000Z", 79.0)),
            texts,
            CsvFormat.FRENCH,
            zone,
        )!!
        assertEquals(listOf("Date;Poids (kg)", "2026-03-14;78,2", "2026-03-08;79"), lines(csv))
    }

    @Test
    fun `nutrition, union des jours en ordre croissant, jours à 0 compris`() {
        val tracker = DailyTracker.DEFAULT.copy(
            caloriesLog = mapOf("2026-03-12" to 2100, "2026-03-10" to 0),
            waterLog = mapOf("2026-03-11" to 1500, "2026-03-12" to 2250),
        )
        assertEquals(
            listOf("Date;Calories (kcal);Eau (ml)", "2026-03-10;0;0", "2026-03-11;0;1500", "2026-03-12;2100;2250"),
            lines(CsvExport.nutrition(tracker, texts, CsvFormat.FRENCH)!!),
        )
    }

    @Test
    fun `seules les tables non vides deviennent des fichiers, nommés au jour local`() {
        val files = CsvExport.export(listOf(old), emptyList(), DailyTracker.DEFAULT, texts, CsvFormat.FRENCH, today, zone)
        // Une séance sans série validée : fichier des séances seulement.
        assertEquals(listOf("musclenote_sessions_2026-03-14.csv"), files.map { it.name })
        assertTrue(CsvExport.export(emptyList(), emptyList(), DailyTracker.DEFAULT, texts, CsvFormat.FRENCH, today, zone).isEmpty())
        assertNull(CsvExport.sets(listOf(old), texts, CsvFormat.FRENCH, zone))

        val all = CsvExport.export(
            listOf(push),
            listOf(BodyWeightEntry("2026-03-14T11:00:00.000Z", 78.2)),
            DailyTracker.DEFAULT.copy(waterLog = mapOf("2026-03-14" to 500)),
            texts,
            CsvFormat.ENGLISH,
            today,
            zone,
        )
        assertEquals(
            listOf(
                "musclenote_sessions_2026-03-14.csv",
                "musclenote_sets_2026-03-14.csv",
                "musclenote_bodyweight_2026-03-14.csv",
                "musclenote_nutrition_2026-03-14.csv",
            ),
            all.map { it.name },
        )
    }
}
