package com.musclenote.android.ui.designsystem.components

import com.musclenote.android.data.model.BodyView
import com.musclenote.android.data.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Carte du corps (§ 15.17.4, 🔄 D43) : formes du SVG, toucher par muscle, zones TalkBack. */
class BodyMapGeometryTest {

    // ─── Formes ─────────────────────────────────────────────────────────────────

    @Test
    fun `les formes de la carte sont exactement celles du SVG de la maquette`() {
        val svg = File("../../design/claude-design/corps/carte-du-corps.svg").readText()
        val paths = Regex("""<path id="([^"]+)" d="([^"]+)"""").findAll(svg).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(paths.getValue("silhouette-face"), BodyMapShapes.silhouettes.getValue(BodyView.Front))
        assertEquals(paths.getValue("silhouette-dos"), BodyMapShapes.silhouettes.getValue(BodyView.Back))
        val muscleIds = paths.keys.filterNot { it.startsWith("silhouette") }
        // Les ids de la liste des muscles sont exactement les formes de la carte, dans le même ordre.
        assertEquals(muscleIds, Muscle.entries.map { it.id })
        for (muscle in Muscle.entries) assertEquals(muscle.id, paths.getValue(muscle.id), BodyMapShapes.muscles.getValue(muscle))
    }

    @Test
    fun `la carte garde les proportions de la maquette`() {
        assertEquals(420f / 460f, BodyMapGeometry.aspectRatio(view = null), 1e-6f)
        assertEquals(200f / 460f, BodyMapGeometry.aspectRatio(BodyView.Front), 1e-6f)
        assertEquals(200f / 460f, BodyMapGeometry.aspectRatio(BodyView.Back), 1e-6f)
    }

    // ─── Toucher ────────────────────────────────────────────────────────────────

    @Test
    fun `un toucher dans une forme donne son muscle, à toute taille`() {
        // Carte des deux vues à sa taille d'origine (460 de haut) : 1 unité du SVG = 1 px.
        assertEquals(Muscle.Pectoraux, BodyMapGeometry.muscleAt(85f, 113f, height = 460f, view = null, tolerance = 12f))
        assertEquals(Muscle.Dorsaux, BodyMapGeometry.muscleAt(302f, 150f, height = 460f, view = null, tolerance = 12f))
        assertEquals(Muscle.AvantBras, BodyMapGeometry.muscleAt(160f, 215f, height = 460f, view = null, tolerance = 12f))
        // Moitié de la taille.
        assertEquals(Muscle.Pectoraux, BodyMapGeometry.muscleAt(42.5f, 56.5f, height = 230f, view = null, tolerance = 12f))
    }

    @Test
    fun `juste à côté d'une forme, le muscle le plus proche à 12 dp au plus`() {
        // À 5 unités du biceps, hors du corps.
        assertEquals(Muscle.Biceps, BodyMapGeometry.muscleAt(38f, 160f, height = 460f, view = null, tolerance = 12f))
        // Entre les deux pectoraux, dans l'écart.
        assertEquals(Muscle.Pectoraux, BodyMapGeometry.muscleAt(100f, 113f, height = 460f, view = null, tolerance = 12f))
        // La tolérance est en px de la carte : à mi-taille, les 5 unités font 2,6 px.
        assertEquals(Muscle.Biceps, BodyMapGeometry.muscleAt(19f, 80f, height = 230f, view = null, tolerance = 12f))
    }

    @Test
    fun `loin de toute forme, rien`() {
        // La tête (39 unités des trapèzes), l'espace entre les deux vues.
        assertNull(BodyMapGeometry.muscleAt(100f, 30f, height = 460f, view = null, tolerance = 12f))
        assertNull(BodyMapGeometry.muscleAt(210f, 200f, height = 460f, view = null, tolerance = 12f))
        // À mi-taille, les 39 unités font 19,5 px : au-delà des 12.
        assertNull(BodyMapGeometry.muscleAt(50f, 15f, height = 230f, view = null, tolerance = 12f))
        // Hors de la carte.
        assertNull(BodyMapGeometry.muscleAt(-40f, 113f, height = 460f, view = null, tolerance = 12f))
    }

    @Test
    fun `une vue seule ne touche que les muscles de sa moitié`() {
        // Le même point est un oblique de face, un dorsal de dos (la vue de dos part de x = 220).
        assertEquals(Muscle.Obliques, BodyMapGeometry.muscleAt(82f, 150f, height = 460f, view = BodyView.Front, tolerance = 12f))
        assertEquals(Muscle.Dorsaux, BodyMapGeometry.muscleAt(82f, 150f, height = 460f, view = BodyView.Back, tolerance = 12f))
        // Le dos d'une carte de face est hors de la carte.
        assertNull(BodyMapGeometry.muscleAt(302f, 150f, height = 460f, view = BodyView.Front, tolerance = 12f))
    }

    // ─── Zones TalkBack ─────────────────────────────────────────────────────────

    @Test
    fun `la zone d'un muscle est sa forme dans sa vue`() {
        val pecs = BodyMapGeometry.bounds(Muscle.Pectoraux, height = 460f, view = null)!!
        assertBounds(70f, 90f, 130f, 134f, pecs)
        // Les trapèzes se voient de face et de dos : leur zone est dans leur vue, le dos.
        assertBounds(288f, 60f, 352f, 154f, BodyMapGeometry.bounds(Muscle.Trapezes, height = 460f, view = null)!!)
        // Une vue seule : la forme dans cette vue, recalée à gauche ; à mi-taille.
        assertBounds(34.5f, 172f, 65.5f, 200.5f, BodyMapGeometry.bounds(Muscle.Mollets, height = 230f, view = BodyView.Front)!!)
        assertBounds(68.9f, 97f, 131.1f, 199f, BodyMapGeometry.bounds(Muscle.Dorsaux, height = 460f, view = BodyView.Back)!!)
        // Un muscle absent de la vue n'a pas de zone.
        assertNull(BodyMapGeometry.bounds(Muscle.Dorsaux, height = 460f, view = BodyView.Front))
        assertNotNull(BodyMapGeometry.bounds(Muscle.Dorsaux, height = 460f, view = null))
    }

    private fun assertBounds(left: Float, top: Float, right: Float, bottom: Float, actual: BodyMapBounds) {
        assertEquals("gauche", left, actual.left, 0.6f)
        assertEquals("haut", top, actual.top, 0.6f)
        assertEquals("droite", right, actual.right, 0.6f)
        assertEquals("bas", bottom, actual.bottom, 0.6f)
    }
}
