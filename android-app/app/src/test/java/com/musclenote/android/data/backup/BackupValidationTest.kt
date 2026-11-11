package com.musclenote.android.data.backup

import com.musclenote.android.data.backup.BackupFixtures.reader
import com.musclenote.android.data.backup.BackupFixtures.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Validation de forme et refus d'import (§ 5.1, § 5.3). */
class BackupValidationTest {

    private fun read(json: String) = reader().read(json)

    private fun invalidKey(key: String) = BackupReadResult.InvalidKey(key)

    /** Une séance valide dont on remplace un morceau. */
    private fun workoutsWith(
        workout: String = """"id": "w1", "name": "Push", "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80""",
        exercises: String = """[ { "exerciseId": "bench", "sets": [ $VALID_SET ] } ]""",
    ) = """{ "version": 1, "@gymtracker_workouts": [ { $workout, "exercises": $exercises } ] }"""

    @Test
    fun `une séance valide est acceptée`() {
        assertEquals(1, success(read(workoutsWith())).data.workouts!!.size)
    }

    // ─── Fichier ────────────────────────────────────────────────────────────────

    @Test
    fun `un JSON illisible est un format invalide`() {
        assertEquals(BackupReadResult.InvalidFormat, read("{"))
        assertEquals(BackupReadResult.InvalidFormat, read(""))
        assertEquals(BackupReadResult.InvalidFormat, read("pas du json"))
    }

    @Test
    fun `une racine qui n'est pas un objet est un format invalide`() {
        assertEquals(BackupReadResult.InvalidFormat, read("[]"))
        assertEquals(BackupReadResult.InvalidFormat, read("\"@gymtracker_workouts\""))
        assertEquals(BackupReadResult.InvalidFormat, read("null"))
    }

    @Test
    fun `un fichier sans clé MuscleNote ne contient aucune donnée`() {
        assertEquals(BackupReadResult.NoData, read("{}"))
        assertEquals(BackupReadResult.NoData, read("""{ "version": 1, "exportedAt": "2026-03-14T19:20:00.000Z" }"""))
        assertEquals(BackupReadResult.NoData, read("""{ "workouts": [] }"""))
    }

    @Test
    fun `une version future est refusée avant toute validation`() {
        assertEquals(BackupReadResult.NewerVersion, read("""{ "version": 3, "@gymtracker_workouts": "?" }"""))
        assertEquals(BackupReadResult.NewerVersion, read("""{ "version": 42 }"""))
    }

    @Test
    fun `une version incohérente est un format invalide`() {
        for (version in listOf("\"2\"", "1.5", "0", "-1", "true", "{}")) {
            assertEquals("version = $version", BackupReadResult.InvalidFormat, read("""{ "version": $version, "@gymtracker_preferences": {} }"""))
        }
    }

    @Test
    fun `une version null ou 2_0 est acceptée`() {
        assertEquals(1, success(read("""{ "version": null, "@gymtracker_preferences": {} }""")).sourceVersion)
        assertEquals(2, success(read("""{ "version": 2.0, "@gymtracker_preferences": {} }""")).sourceVersion)
    }

    @Test
    fun `une marque d'ordre des octets en tête est ignorée`() {
        success(read("﻿{ \"@gymtracker_preferences\": {} }"))
    }

    @Test
    fun `seules les clés présentes sont importées`() {
        val data = success(read("""{ "@gymtracker_body_weights": [ { "date": "2026-03-10T11:00:00.000Z", "weight": 79.8 } ] }""")).data
        assertEquals(1, data.bodyWeights!!.size)
        assertNull(data.workouts)
        assertNull(data.exercises)
        assertNull(data.supplements)
        assertNull(data.supplementsTaken)
        assertNull(data.dailyTracker)
        assertNull(data.preferences)
    }

    // ─── Séances ────────────────────────────────────────────────────────────────

    @Test
    fun `des séances qui ne sont pas un tableau sont refusées`() {
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read("""{ "@gymtracker_workouts": {} }"""))
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read("""{ "@gymtracker_workouts": null }"""))
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read("""{ "@gymtracker_workouts": [ 1 ] }"""))
    }

    @Test
    fun `une séance sans id ou avec un id vide est refusée`() {
        val noId = """"name": "Push", "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80"""
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = noId)))
        val emptyId = """"id": "", "name": "Push", "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80"""
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = emptyId)))
    }

    @Test
    fun `une séance dont la date n'est pas une date est refusée`() {
        for (date in listOf("\"pas une date\"", "\"2026-02-31T10:00:00.000Z\"", "1741000000000", "null")) {
            val workout = """"id": "w1", "name": "Push", "date": $date, "bodyWeight": 80"""
            assertEquals("date = $date", invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = workout)))
        }
    }

    @Test
    fun `les formes de date courantes sont acceptées`() {
        for (date in listOf("2026-03-01T10:00:00.000Z", "2026-03-01T10:00:00Z", "2026-03-01T11:00:00+01:00", "2026-03-01T11:00:00", "2026-03-01")) {
            val workout = """"id": "w1", "name": "Push", "date": "$date", "bodyWeight": 80"""
            success(read(workoutsWith(workout = workout)))
        }
    }

    @Test
    fun `un nom ou un poids du mauvais type est refusé`() {
        val numericName = """"id": "w1", "name": 42, "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80"""
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = numericName)))
        val textWeight = """"id": "w1", "name": "Push", "date": "2026-03-01T10:00:00.000Z", "bodyWeight": "80""""
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = textWeight)))
        val numericPreset = """"id": "w1", "name": "Push", "preset": 1, "date": "2026-03-01T10:00:00.000Z", "bodyWeight": 80"""
        assertEquals(invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(workout = numericPreset)))
    }

    @Test
    fun `des exercices de séance mal formés sont refusés`() {
        for (exercises in listOf(
            "null",
            "{}",
            """[ { "sets": [] } ]""",
            """[ { "exerciseId": 7, "sets": [] } ]""",
            """[ { "exerciseId": "bench" } ]""",
            """[ { "exerciseId": "bench", "sets": {} } ]""",
        )) {
            assertEquals("exercises = $exercises", invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(exercises = exercises)))
        }
    }

    @Test
    fun `une série mal formée est refusée`() {
        for (set in listOf(
            """{ "reps": 8 }""",
            """{ "weight": 60 }""",
            """{ "reps": "8", "weight": 60 }""",
            """{ "reps": 8, "weight": "60" }""",
            """{ "reps": 8, "weight": null }""",
            """{ "reps": 8, "weight": 60, "duration": "20" }""",
            """{ "reps": 8, "weight": 60, "incline": true }""",
            "[]",
        )) {
            val exercises = """[ { "exerciseId": "bench", "sets": [ $set ] } ]"""
            assertEquals("set = $set", invalidKey(BackupKeys.WORKOUTS), read(workoutsWith(exercises = exercises)))
        }
    }

    @Test
    fun `des champs cardio null ou absents sont acceptés`() {
        val exercises = """[ { "exerciseId": "bike", "sets": [ { "reps": 0, "weight": 0, "duration": null, "distance": 12.5 } ] } ]"""
        success(read(workoutsWith(exercises = exercises)))
    }

    // ─── Autres clés ────────────────────────────────────────────────────────────

    @Test
    fun `un exercice perso incomplet est refusé`() {
        for (exercise in listOf(
            """{ "id": "e1", "name": "Hip thrust" }""",
            """{ "id": "e1", "muscleGroup": "jambes" }""",
            """{ "name": "Hip thrust", "muscleGroup": "jambes" }""",
            """{ "id": "", "name": "Hip thrust", "muscleGroup": "jambes" }""",
            """{ "id": "e1", "name": "Hip thrust", "muscleGroup": 3 }""",
        )) {
            assertEquals(exercise, invalidKey(BackupKeys.EXERCISES), read("""{ "@gymtracker_exercises": [ $exercise ] }"""))
        }
    }

    @Test
    fun `une pesée mal formée est refusée`() {
        for (entry in listOf(
            """{ "date": "2026-03-10T11:00:00.000Z" }""",
            """{ "date": "2026-03-10T11:00:00.000Z", "weight": "79,8" }""",
            """{ "date": "hier", "weight": 79.8 }""",
            """{ "weight": 79.8 }""",
        )) {
            assertEquals(entry, invalidKey(BackupKeys.BODY_WEIGHTS), read("""{ "@gymtracker_body_weights": [ $entry ] }"""))
        }
    }

    @Test
    fun `un complément sans id ou sans nom est refusé`() {
        assertEquals(invalidKey(BackupKeys.SUPPLEMENTS), read("""{ "@gymtracker_supplements": [ { "id": "s1" } ] }"""))
        assertEquals(invalidKey(BackupKeys.SUPPLEMENTS), read("""{ "@gymtracker_supplements": [ { "name": "Créatine" } ] }"""))
    }

    @Test
    fun `les documents objets doivent être des objets`() {
        for (key in listOf(BackupKeys.SUPPLEMENTS_TAKEN, BackupKeys.DAILY_TRACKER, BackupKeys.PREFERENCES)) {
            for (value in listOf("[]", "null", "3", "\"x\"")) {
                assertEquals("$key = $value", invalidKey(key), read("""{ "$key": $value }"""))
            }
        }
    }

    @Test
    fun `une seule clé invalide fait refuser tout le fichier`() {
        val json = """{ "@gymtracker_preferences": { "restSeconds": 90 }, "@gymtracker_supplements_taken": [] }"""
        assertEquals(invalidKey(BackupKeys.SUPPLEMENTS_TAKEN), read(json))
    }

    @Test
    fun `la première clé invalide signalée suit l'ordre du format`() {
        val json = """{ "@gymtracker_supplements": [ {} ], "@gymtracker_exercises": [ {} ] }"""
        assertEquals(invalidKey(BackupKeys.EXERCISES), read(json))
    }

    private companion object {
        const val VALID_SET = """{ "id": "s1", "reps": 8, "weight": 60, "completed": true }"""
    }
}
