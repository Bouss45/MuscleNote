package com.musclenote.android.data.model

import com.musclenote.android.R
import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.util.normalizeSearch
import com.musclenote.android.data.util.normalizeWorkoutName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Catalogue (§ 6.1), groupes (§ 6.2), préréglages (§ 6.3) et cohérence avec les ressources. */
class CatalogTest {

    // ─── Exercices intégrés ─────────────────────────────────────────────────────

    @Test
    fun `le catalogue contient 45 exercices aux ids uniques dans l'ordre de la SPEC`() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals(45, ids.size)
        assertEquals(45, ids.toSet().size)
        assertEquals("deadlift", ids.first())
        assertEquals("deadhang", ids.last())
        assertEquals(specRows("### 6.1 Exercices intégrés", columns = 6).map { it[1].removeSurrounding("`") }, ids)
        // Ids figés, fautes comprises.
        assertTrue(ExerciseCatalog.isBuiltIn("calfraize") && ExerciseCatalog.isBuiltIn("legrise"))
        assertTrue(!ExerciseCatalog.isBuiltIn("1740000000000exo00001"))
    }

    @Test
    fun `les exercices sont répartis dans les 9 groupes comme dans la SPEC`() {
        val counts = ExerciseCatalog.all.groupingBy { it.muscleGroup }.eachCount()
        assertEquals(
            mapOf(
                MuscleGroup.Dos to 6, MuscleGroup.Pecs to 7, MuscleGroup.Biceps to 3, MuscleGroup.Triceps to 4,
                MuscleGroup.Jambes to 10, MuscleGroup.Epaules to 3, MuscleGroup.Abdos to 4, MuscleGroup.Cardio to 2,
                MuscleGroup.AvantBras to 6,
            ),
            counts,
        )
        assertEquals(listOf("treadmill", "bike"), ExerciseCatalog.all.filter { it.muscleGroup == MuscleGroup.Cardio }.map { it.id })
    }

    @Test
    fun `chaque exercice intégré a un nom en français et en anglais`() {
        for (exercise in ExerciseCatalog.all) {
            val name = resourceName(exercise.nameRes)
            assertEquals("exercise_${exercise.id}", name)
            assertTrue("FR manquant : $name", !FR[name].isNullOrBlank())
            assertTrue("EN manquant : $name", !EN[name].isNullOrBlank())
        }
        assertEquals("Soulevé de terre", FR["exercise_deadlift"])
        assertEquals("Farmer's walk", EN["exercise_farmerwalk"])
    }

    @Test
    fun `les exercices au temps sont ceux de la SPEC, avec leurs noms`() {
        // § 6.1 et § 15.18.1 (🔄 D44) : la colonne « type » fait foi.
        val timed = specRows("### 6.1 Exercices intégrés", columns = 6).filter { it[5] == "au temps" }.map { it[1].removeSurrounding("`") }
        assertEquals(listOf("wallsit", "plank", "sideplank", "deadhang"), timed)
        assertEquals(timed, ExerciseCatalog.all.filter { it.type == ExerciseType.Timed }.map { it.id })
        assertEquals("Planche", FR["exercise_plank"])
        assertEquals("Planche latérale", FR["exercise_sideplank"])
        assertEquals("Chaise contre le mur", FR["exercise_wallsit"])
        assertEquals("Suspension à la barre", FR["exercise_deadhang"])
        assertEquals("Side plank", EN["exercise_sideplank"])
        assertEquals("Wall sit", EN["exercise_wallsit"])
        assertEquals("Dead hang", EN["exercise_deadhang"])
        // Le cardio reste décidé par le groupe, même avec un type au temps.
        val definitions = exerciseDefinitions(listOf(Exercise("c", "Corde", MuscleGroup.Cardio, type = ExerciseType.Timed)))
        assertEquals(SetKind.Cardio, definitions.single { it.id == "c" }.kind)
        assertEquals(SetKind.Timed, definitions.single { it.id == "plank" }.kind)
        assertEquals(SetKind.Strength, definitions.single { it.id == "crunch" }.kind)
        assertEquals(SetKind.Cardio, definitions.single { it.id == "treadmill" }.kind)
    }

    // ─── Groupes musculaires ────────────────────────────────────────────────────

    @Test
    fun `les groupes sont sérialisés en chaînes exactes accents compris`() {
        assertEquals(
            listOf("dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio", "avant-bras"),
            MuscleGroup.entries.map { it.id },
        )
        for (group in MuscleGroup.entries) {
            assertEquals("\"${group.id}\"", AppJson.storage.encodeToString(MuscleGroup.serializer(), group))
        }
    }

    @Test
    fun `les libellés de migration des groupes sont ceux des ressources`() {
        for (group in MuscleGroup.entries) {
            val name = resourceName(group.labelRes)
            assertEquals(name, FR[name], group.labelFr)
            assertEquals(name, EN[name], group.labelEn)
        }
    }

    @Test
    fun `un groupe inconnu est normalisé puis rangé dans dos`() {
        val cases = mapOf(
            "épaules" to MuscleGroup.Epaules,
            "épaules" to MuscleGroup.Epaules, // forme décomposée (NFD)
            "Epaules" to MuscleGroup.Epaules,
            "SHOULDERS" to MuscleGroup.Epaules,
            "avant bras" to MuscleGroup.AvantBras,
            "Avant-Bras" to MuscleGroup.AvantBras,
            "forearms" to MuscleGroup.AvantBras,
            "Legs" to MuscleGroup.Jambes,
            "chest" to MuscleGroup.Pecs,
            " Abs " to MuscleGroup.Abdos,
            "mollets" to MuscleGroup.Dos,
            "" to MuscleGroup.Dos,
        )
        for ((raw, expected) in cases) assertEquals("« $raw »", expected, MuscleGroup.fromLegacy(raw))
    }

    // ─── Préréglages ────────────────────────────────────────────────────────────

    @Test
    fun `les préréglages ont les ids, les groupes et l'ordre de la SPEC`() {
        // 🔄 D45 : groupe « Haut / Bas » et Abdos, Fessiers dans Split (15 préréglages).
        val rows = specRows("### 6.3 Préréglages de séance", columns = 4)
        assertEquals(15, rows.size)
        assertEquals(rows.map { it[1].removeSurrounding("`") }, PresetId.entries.map { it.id })
        val groups = mapOf("PPL" to PresetGroup.Ppl, "Haut / Bas" to PresetGroup.UpperLower, "Split" to PresetGroup.Split, "Autre" to PresetGroup.Other)
        assertEquals(rows.map { groups.getValue(it[0].substringBefore(" (").trim()) }, PresetId.entries.map { it.group })
        assertEquals(listOf(PresetGroup.Ppl, PresetGroup.UpperLower, PresetGroup.Split, PresetGroup.Other), PresetGroup.entries)
        // Libellés FR et EN du tableau = ceux de la migration.
        for ((row, preset) in rows.zip(PresetId.entries)) {
            assertEquals(row[2], preset.labelFr)
            assertEquals(row[3], preset.labelEn)
        }
        for (preset in PresetId.entries) {
            assertEquals("\"${preset.id}\"", AppJson.storage.encodeToString(PresetId.serializer(), preset))
        }
    }

    @Test
    fun `les libellés de migration des préréglages sont ceux des ressources`() {
        for (preset in PresetId.entries) {
            val name = resourceName(preset.labelRes)
            assertEquals(name, FR[name], preset.labelFr)
            assertEquals(name, EN[name], preset.labelEn)
        }
    }

    @Test
    fun `le préréglage d'une ancienne séance se déduit de son nom`() {
        val cases = mapOf(
            "Push" to PresetId.Push,
            "PUSH" to PresetId.Push,
            " pull " to PresetId.Pull,
            "Legs" to PresetId.Legs, // ancien libellé EN de legsSplit aussi : rattaché à legs (§ 5.3)
            "Legs (split)" to PresetId.LegsSplit,
            "Jambes" to PresetId.LegsSplit,
            "Chest" to PresetId.Pecs,
            "Pecs" to PresetId.Pecs,
            "Back" to PresetId.Dos,
            "Épaules" to PresetId.Shoulders,
            "epaules" to PresetId.Shoulders,
            "Shoulders" to PresetId.Shoulders,
            "Bras" to PresetId.Arms,
            "Arms" to PresetId.Arms,
            "full body" to PresetId.FullBody,
            "Isolation" to PresetId.Isolation,
            "cardio" to PresetId.Cardio,
            // 🔄 D45 : nouveaux préréglages, espaces en trop compris.
            "Haut du corps" to PresetId.UpperBody,
            " haut   du CORPS " to PresetId.UpperBody,
            "Upper body" to PresetId.UpperBody,
            "Bas du corps" to PresetId.LowerBody,
            "lower body" to PresetId.LowerBody,
            "Abdos" to PresetId.Abs,
            "Abs" to PresetId.Abs,
            "Fessiers" to PresetId.Glutes,
            "glutes" to PresetId.Glutes,
            "Full\u00A0 Body" to PresetId.FullBody,
        )
        for ((name, expected) in cases) assertEquals("« $name »", expected, PresetId.fromLegacyName(name))
        assertNull(PresetId.fromLegacyName("Push day"))
        assertNull(PresetId.fromLegacyName("Hautducorps"))
        assertNull(PresetId.fromLegacyName(""))
        assertNull(PresetId.fromLegacyName("   "))
    }

    @Test
    fun `la normalisation de recherche retire accents casse et espaces`() {
        assertEquals("developpe couche", normalizeSearch("  Développé Couché "))
        assertEquals("epaules", normalizeSearch("épaules"))
    }

    @Test
    fun `un nom de séance normalisé réduit aussi les suites d'espaces`() {
        // 🔄 D45 (§ 15.19.2).
        assertEquals("bras / abdos", normalizeWorkoutName("  Bras  /\tAbdos "))
        assertEquals("epaules / mollets", normalizeWorkoutName("Épaules\u00A0/\u202FMollets"))
        assertEquals("", normalizeWorkoutName(" \u00A0 "))
    }

    // ─── Ressources ─────────────────────────────────────────────────────────────

    private companion object {
        val FR = strings("src/main/res/values/strings.xml")
        val EN = strings("src/main/res/values-en/strings.xml")
        val SPEC: List<String> = File("../../SPEC.md").readLines()

        /** Lignes du premier tableau qui suit la ligne commençant par [marker], en [columns] cellules. */
        fun specRows(marker: String, columns: Int): List<List<String>> {
            val start = SPEC.indexOfFirst { it.startsWith(marker) }
            check(start >= 0) { "« $marker » introuvable dans SPEC.md" }
            val table = SPEC.drop(start + 1).dropWhile { !it.startsWith("|") }.takeWhile { it.startsWith("|") }
            return table.drop(2).map { line ->
                val cells = line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() }
                check(cells.size == columns) { "ligne inattendue : $line" }
                cells
            }
        }

        /** `<string name="…">` → texte, échappements Android retirés. */
        fun strings(path: String): Map<String, String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).associate { i ->
                val node = nodes.item(i)
                node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'").replace("\\\"", "\"")
            }
        }

        fun resourceName(id: Int): String = R.string::class.java.fields.first { it.getInt(null) == id }.name
    }
}
