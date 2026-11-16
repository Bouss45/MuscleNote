package com.musclenote.android.data.model

import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Muscles (§ 15.17.1) et muscles des exercices intégrés (§ 15.17.2, 🔄 D43). Les tableaux relus dans
 * `SPEC.md` font foi : la donnée embarquée doit leur être identique.
 */
class MuscleCatalogTest {

    // ─── Liste des muscles ──────────────────────────────────────────────────────

    @Test
    fun `les 17 muscles ont les ids du SVG, dans l'ordre et avec la vue de la SPEC`() {
        val rows = specRows("#### 15.17.1", columns = 7)
        assertEquals(17, rows.size)
        assertEquals(rows.map { it[1].ticked().single() }, Muscle.entries.map { it.id })
        for ((row, muscle) in rows.zip(Muscle.entries)) {
            val view = if (row[6] == "face") BodyView.Front else BodyView.Back
            assertEquals(muscle.id, view, muscle.view)
        }
        assertEquals(17, Muscle.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `un muscle se retrouve par son id, un id inconnu ne donne rien`() {
        for (muscle in Muscle.entries) assertEquals(muscle, Muscle.fromId(muscle.id))
        assertNull(Muscle.fromId("pecs"))
        assertNull(Muscle.fromId(""))
        assertNull(Muscle.fromId("Pectoraux"))
    }

    @Test
    fun `chaque muscle a son nom, son nom savant et son rôle en français et en anglais, comme la SPEC`() {
        val rows = specRows("#### 15.17.1", columns = 7)
        val rolesFr = specRows("**Rôle**", columns = 2).associate { it[0].ticked().single() to it[1] }
        val rolesEn = specRows("Rôles en anglais", columns = 2).associate { it[0].ticked().single() to it[1] }
        for ((row, muscle) in rows.zip(Muscle.entries)) {
            val key = muscle.id.replace('-', '_')
            assertEquals("muscle_name_$key", resourceName(muscle.nameRes))
            assertEquals("muscle_scientific_$key", resourceName(muscle.scientificRes))
            assertEquals("muscle_role_$key", resourceName(muscle.roleRes))
            assertEquals(row[2], FR.getValue("muscle_name_$key"))
            assertEquals(row[3], EN.getValue("muscle_name_$key"))
            assertEquals(row[4], FR.getValue("muscle_scientific_$key"))
            assertEquals(row[5], EN.getValue("muscle_scientific_$key"))
            assertEquals(rolesFr.getValue(muscle.id), FR.getValue("muscle_role_$key"))
            assertEquals(rolesEn.getValue(muscle.id), EN.getValue("muscle_role_$key"))
        }
    }

    // ─── Muscles des exercices intégrés ─────────────────────────────────────────

    @Test
    fun `les muscles des exercices intégrés sont ceux du tableau de la SPEC`() {
        val rows = specRows("#### 15.17.2", columns = 4)
        assertEquals(ExerciseCatalog.all.map { it.id }, rows.map { it[1].ticked().single() })
        for ((row, exercise) in rows.zip(ExerciseCatalog.all)) {
            assertEquals(exercise.id, row[2].ticked(), exercise.primaryMuscles.map { it.id })
            assertEquals(exercise.id, row[3].ticked(), exercise.secondaryMuscles.map { it.id })
        }
    }

    @Test
    fun `chaque exercice intégré a au moins un muscle principal`() {
        for (exercise in ExerciseCatalog.all) {
            assertTrue("${exercise.id} sans principal", exercise.primaryMuscles.isNotEmpty())
        }
    }

    @Test
    fun `aucun muscle n'est à la fois principal et secondaire, ni deux fois dans une liste`() {
        for (exercise in ExerciseCatalog.all) {
            assertTrue(exercise.id, exercise.primaryMuscles.intersect(exercise.secondaryMuscles.toSet()).isEmpty())
            assertEquals(exercise.id, exercise.primaryMuscles.distinct(), exercise.primaryMuscles)
            assertEquals(exercise.id, exercise.secondaryMuscles.distinct(), exercise.secondaryMuscles)
        }
    }

    @Test
    fun `chaque id de muscle d'un exercice existe dans la liste des muscles, dans son ordre`() {
        val known = Muscle.entries.map { it.id }.toSet()
        for (row in specRows("#### 15.17.2", columns = 4)) {
            for (id in row[2].ticked() + row[3].ticked()) assertTrue("${row[1]} : $id", id in known)
        }
        for (exercise in ExerciseCatalog.all) {
            assertEquals(exercise.id, exercise.primaryMuscles.sortedBy { it.ordinal }, exercise.primaryMuscles)
            assertEquals(exercise.id, exercise.secondaryMuscles.sortedBy { it.ordinal }, exercise.secondaryMuscles)
        }
    }

    @Test
    fun `quelques exercices relus en clair`() {
        val bench = ExerciseCatalog.find("bench")!!
        assertEquals(listOf(Muscle.Pectoraux), bench.primaryMuscles)
        assertEquals(listOf(Muscle.EpaulesAvant, Muscle.Triceps), bench.secondaryMuscles)
        val dips = ExerciseCatalog.find("dips")!!
        assertEquals(listOf(Muscle.Pectoraux, Muscle.Triceps), dips.primaryMuscles)
        val pushdown = ExerciseCatalog.find("pushdown")!!
        assertEquals(emptyList<Muscle>(), pushdown.secondaryMuscles)
        assertFalse(Muscle.Pectoraux in ExerciseCatalog.find("squat")!!.let { it.primaryMuscles + it.secondaryMuscles })
    }

    // ─── Lecture de la SPEC et des ressources ───────────────────────────────────

    private companion object {
        val SPEC: List<String> = File("../../SPEC.md").readLines()
        val FR = strings("src/main/res/values/strings.xml")
        val EN = strings("src/main/res/values-en/strings.xml")

        /**
         * Lignes du premier tableau qui suit la ligne commençant par [marker] (en-tête et séparateur
         * retirés), découpées en [columns] cellules.
         */
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

        /** Les valeurs entre backticks d'une cellule (« — » : aucune). */
        fun String.ticked(): List<String> = Regex("`([^`]+)`").findAll(this).map { it.groupValues[1] }.toList()

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
