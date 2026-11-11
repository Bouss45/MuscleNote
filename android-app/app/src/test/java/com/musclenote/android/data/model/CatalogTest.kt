package com.musclenote.android.data.model

import com.musclenote.android.R
import com.musclenote.android.data.backup.AppJson
import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.util.normalizeSearch
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
    fun `le catalogue contient 42 exercices aux ids uniques dans l'ordre de la SPEC`() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals(42, ids.size)
        assertEquals(42, ids.toSet().size)
        assertEquals("deadlift", ids.first())
        assertEquals("farmerwalk", ids.last())
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
                MuscleGroup.Jambes to 9, MuscleGroup.Epaules to 3, MuscleGroup.Abdos to 3, MuscleGroup.Cardio to 2,
                MuscleGroup.AvantBras to 5,
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
    fun `les préréglages ont les ids de la SPEC`() {
        assertEquals(
            listOf("push", "pull", "legs", "pecs", "dos", "shoulders", "legsSplit", "arms", "fullBody", "isolation", "cardio"),
            PresetId.entries.map { it.id },
        )
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
            "Legs" to PresetId.Legs, // libellé FR de legs, avant le libellé EN de legsSplit
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
        )
        for ((name, expected) in cases) assertEquals("« $name »", expected, PresetId.fromLegacyName(name))
        assertNull(PresetId.fromLegacyName("Haut du corps"))
        assertNull(PresetId.fromLegacyName("Push day"))
        assertNull(PresetId.fromLegacyName(""))
    }

    @Test
    fun `la normalisation de recherche retire accents casse et espaces`() {
        assertEquals("developpe couche", normalizeSearch("  Développé Couché "))
        assertEquals("epaules", normalizeSearch("épaules"))
    }

    // ─── Ressources ─────────────────────────────────────────────────────────────

    private companion object {
        val FR = strings("src/main/res/values/strings.xml")
        val EN = strings("src/main/res/values-en/strings.xml")

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
