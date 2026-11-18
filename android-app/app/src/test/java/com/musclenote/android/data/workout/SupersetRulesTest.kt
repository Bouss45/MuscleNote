package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Superséries (SPEC § 15.20, 🔄 D46), règles pures : liens entre voisins (lier, délier, remise en
 * forme, glisser-déposer, retrait), ordre d'enchaînement tour par tour, transition ou fin du tour, série
 * ouverte au pavé, prochaine série de la notification et place du repos.
 */
class SupersetRulesTest {

    private var generated = 0
    private val newId = { "new-${++generated}" }

    private fun set(id: String, completed: Boolean = false, warmup: Boolean = false) =
        WorkoutSet(id, 8, 80.0, completed, warmup = warmup)

    private fun ex(id: String, superset: String? = null, vararg sets: WorkoutSet) =
        WorkoutExercise(id, sets = sets.toList(), supersetId = superset)

    private fun workout(vararg exercises: WorkoutExercise) =
        Workout("w", "Push", PresetId.Push, "2026-11-12T17:00:00.000Z", 80.0, exercises.toList())

    private fun ids(list: List<WorkoutExercise>) = list.map { it.supersetId }

    private fun groups(list: List<WorkoutExercise>) = SupersetRules.groups(list).map { group -> group.map { it.exerciseId } }

    private fun chain(vararg exercises: WorkoutExercise) = SupersetRules.chainOrder(exercises.toList()).map { it.set.id }

    // ─── Lier ────────────────────────────────────────────────────────────────────

    @Test
    fun `lier un exercice au suivant les met dans la même supersérie`() {
        val linked = SupersetRules.link(listOf(ex("a"), ex("b"), ex("c")), "a", newId)
        assertEquals(listOf("new-1", "new-1", null), ids(linked))
        assertEquals(listOf(listOf("a", "b")), groups(linked))
        assertTrue(SupersetRules.isLinkedToNext(linked, "a"))
        assertFalse(SupersetRules.isLinkedToNext(linked, "b"))
        assertEquals(listOf("a", "b"), SupersetRules.groupOf(linked, "b")!!.map { it.exerciseId })
        assertNull(SupersetRules.groupOf(linked, "c"))
    }

    @Test
    fun `le dernier d'une supersérie lié au suivant forme un circuit, deux superséries se fondent`() {
        val circuit = SupersetRules.link(listOf(ex("a", "x"), ex("b", "x"), ex("c")), "b", newId)
        assertEquals(listOf("x", "x", "x"), ids(circuit))
        assertEquals(listOf(listOf("a", "b", "c")), groups(circuit))

        val merged = SupersetRules.link(listOf(ex("a", "x"), ex("b", "x"), ex("c", "y"), ex("d", "y")), "b", newId)
        assertEquals(listOf(listOf("a", "b", "c", "d")), groups(merged))

        // Un exercice seul lié au premier d'une supersérie la rejoint.
        val joined = SupersetRules.link(listOf(ex("z"), ex("c", "y"), ex("d", "y")), "z", newId)
        assertEquals(listOf("y", "y", "y"), ids(joined))
        assertEquals(0, generated)
    }

    @Test
    fun `le dernier exercice ne se lie à rien, deux exercices déjà liés ne changent pas`() {
        val list = listOf(ex("a", "x"), ex("b", "x"), ex("c"))
        assertSame(list, SupersetRules.link(list, "c", newId))
        assertSame(list, SupersetRules.link(list, "a", newId))
        assertSame(list, SupersetRules.link(list, "absent", newId))
        assertTrue(SupersetRules.canLinkToNext(list, "b"))
        assertFalse(SupersetRules.canLinkToNext(list, "a"))
        assertFalse(SupersetRules.canLinkToNext(list, "c"))
    }

    // ─── Délier ──────────────────────────────────────────────────────────────────

    @Test
    fun `délier sort l'exercice, les voisins restés liés deux à deux gardent leur lien`() {
        val circuit = listOf(ex("a", "x"), ex("b", "x"), ex("c", "x"))
        assertEquals(listOf("x", "x", null), ids(SupersetRules.unlink(circuit, "c", newId)))
        assertEquals(listOf(null, "x", "x"), ids(SupersetRules.unlink(circuit, "a", newId)))
        // Le milieu sorti, A et C ne sont plus voisins : plus aucun lien.
        assertEquals(listOf(null, null, null), ids(SupersetRules.unlink(circuit, "b", newId)))

        val four = listOf(ex("a", "x"), ex("b", "x"), ex("c", "x"), ex("d", "x"))
        assertEquals(listOf(null, null, "x", "x"), ids(SupersetRules.unlink(four, "b", newId)))

        val alone = listOf(ex("a"), ex("b"))
        assertSame(alone, SupersetRules.unlink(alone, "a", newId))
    }

    // ─── Remise en forme, glisser-déposer, retrait ───────────────────────────────

    @Test
    fun `un exercice sans voisin au même identifiant le perd, deux suites séparées sont distinguées`() {
        assertEquals(listOf(null, null, "x", "x"), ids(SupersetRules.normalized(listOf(ex("a", "x"), ex("b"), ex("c", "x"), ex("d", "x")), newId)))
        assertEquals(
            listOf("x", "x", null, "new-1", "new-1"),
            ids(SupersetRules.normalized(listOf(ex("a", "x"), ex("b", "x"), ex("c"), ex("d", "x"), ex("e", "x")), newId)),
        )
        assertEquals(listOf<String?>(null), ids(SupersetRules.normalized(listOf(ex("a", "x")), newId)))
        val clean = listOf(ex("a", "x"), ex("b", "x"), ex("c"))
        assertSame(clean, SupersetRules.normalized(clean, newId))
    }

    @Test
    fun `le lien suit les voisins quand on réordonne`() {
        val session = workout(ex("a", "x"), ex("b", "x"), ex("c", "x"), ex("d"))
        fun reordered(vararg order: String) = SupersetRules.normalized(WorkoutRules.reorder(session, order.toList()).exercises, newId)

        // À l'intérieur de la supersérie : elle reste entière.
        assertEquals(listOf(listOf("c", "a", "b")), groups(reordered("c", "a", "b", "d")))
        // B sorti en fin de liste : A et C, devenus voisins, restent liés.
        val out = reordered("a", "c", "d", "b")
        assertEquals(listOf(listOf("a", "c")), groups(out))
        assertNull(out.single { it.exerciseId == "b" }.supersetId)
        // D posé entre A et B : le lien est coupé à cet endroit.
        val cut = reordered("a", "d", "b", "c")
        assertEquals(listOf(listOf("b", "c")), groups(cut))
        assertNull(cut.single { it.exerciseId == "a" }.supersetId)
    }

    @Test
    fun `retirer un exercice lié referme la supersérie sans lui`() {
        val circuit = listOf(ex("a", "x"), ex("b", "x"), ex("c", "x"))
        assertEquals(listOf(listOf("a", "c")), groups(SupersetRules.normalized(circuit.filterNot { it.exerciseId == "b" }, newId)))
        val pair = listOf(ex("a", "x"), ex("b", "x"), ex("c"))
        assertEquals(listOf(null, null), ids(SupersetRules.normalized(pair.filterNot { it.exerciseId == "b" }, newId)))
    }

    @Test
    fun `un exercice masqué ne compte pas comme voisin`() {
        val shown = { id: String -> id != "ghost" }
        val linked = SupersetRules.link(listOf(ex("a"), ex("ghost"), ex("b")), "a", newId, shown)
        assertEquals(listOf("new-1", null, "new-1"), ids(linked))
        assertEquals(listOf(listOf("a", "b")), SupersetRules.groups(linked.filter { shown(it.exerciseId) }).map { g -> g.map { it.exerciseId } })
        // La remise en forme ne le touche pas et ne coupe pas le lien.
        assertSame(linked, SupersetRules.normalized(linked, newId, shown))
    }

    // ─── Ordre d'enchaînement ────────────────────────────────────────────────────

    @Test
    fun `deux exercices liés s'enchaînent tour par tour`() {
        val order = SupersetRules.chainOrder(
            listOf(ex("a", "x", set("a1"), set("a2"), set("a3")), ex("b", "x", set("b1"), set("b2"), set("b3"))),
        )
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3", "b3"), order.map { it.set.id })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), order.map { it.number })
        assertEquals(listOf("a", "b", "a", "b", "a", "b"), order.map { it.exercise.exerciseId })
    }

    @Test
    fun `un circuit de trois puis un exercice seul`() {
        assertEquals(
            listOf("a1", "b1", "c1", "a2", "b2", "c2", "d1", "d2"),
            chain(
                ex("a", "x", set("a1"), set("a2")),
                ex("b", "x", set("b1"), set("b2")),
                ex("c", "x", set("c1"), set("c2")),
                ex("d", null, set("d1"), set("d2")),
            ),
        )
    }

    @Test
    fun `avec des nombres de séries différents, un rang absent est sauté`() {
        assertEquals(
            listOf("a1", "b1", "c1", "a2", "c2", "a3"),
            chain(ex("a", "x", set("a1"), set("a2"), set("a3")), ex("b", "x", set("b1")), ex("c", "x", set("c1"), set("c2"))),
        )
    }

    @Test
    fun `sans supersérie, c'est l'ordre de la séance`() {
        assertEquals(listOf("a1", "a2", "b1", "b2"), chain(ex("a", null, set("a1"), set("a2")), ex("b", null, set("b1"), set("b2"))))
    }

    @Test
    fun `les séries de chauffe passent juste avant la première série de travail de leur exercice`() {
        val order = SupersetRules.chainOrder(
            listOf(
                ex("a", "x", set("aw", warmup = true), set("a1"), set("a2")),
                ex("b", "x", set("bw", warmup = true), set("b1"), set("b2")),
            ),
        )
        assertEquals(listOf("aw", "a1", "bw", "b1", "a2", "b2"), order.map { it.set.id })
        assertEquals(listOf(true, false, true, false, false, false), order.map { it.warmup })
        assertEquals(listOf(1, 1, 1, 1, 2, 2), order.map { it.number })
        // Un exercice qui n'a que de la chauffe garde sa place au premier tour.
        assertEquals(listOf("aw", "a1", "b1"), chain(ex("a", "x", set("aw", warmup = true), set("a1")), ex("b", "x", set("b1"))))
        assertEquals(listOf("aw", "b1"), chain(ex("a", "x", set("aw", warmup = true)), ex("b", "x", set("b1"))))
    }

    // ─── Après une validation ────────────────────────────────────────────────────

    @Test
    fun `la série d'un exercice qui n'est pas le dernier - transition vers la même série du suivant`() {
        val list = listOf(ex("a", "x", set("a1", true), set("a2")), ex("b", "x", set("b1"), set("b2")))
        val after = SupersetRules.afterCompleting(list, "a", "a1")
        assertTrue(after.transition)
        assertEquals("b1", after.next!!.set.id)
        assertEquals("b", after.next!!.exercise.exerciseId)
    }

    @Test
    fun `la série du dernier exercice - fin du tour, la série suivante du premier`() {
        val list = listOf(ex("a", "x", set("a1", true), set("a2")), ex("b", "x", set("b1", true), set("b2")))
        val after = SupersetRules.afterCompleting(list, "b", "b1")
        assertFalse(after.transition)
        assertEquals("a2", after.next!!.set.id)
    }

    @Test
    fun `un circuit de trois enchaîne jusqu'au dernier exercice`() {
        fun circuit(vararg done: String) = listOf("a", "b", "c").map { e ->
            ex(e, "x", set("${e}1", "${e}1" in done), set("${e}2", "${e}2" in done))
        }
        val first = SupersetRules.afterCompleting(circuit("a1"), "a", "a1")
        assertTrue(first.transition)
        assertEquals("b1", first.next!!.set.id)
        val second = SupersetRules.afterCompleting(circuit("a1", "b1"), "b", "b1")
        assertTrue(second.transition)
        assertEquals("c1", second.next!!.set.id)
        val last = SupersetRules.afterCompleting(circuit("a1", "b1", "c1"), "c", "c1")
        assertFalse(last.transition)
        assertEquals("a2", last.next!!.set.id)
    }

    @Test
    fun `fin du tour quand les exercices suivants n'ont pas ce rang ou l'ont déjà validé`() {
        val missing = listOf(ex("a", "x", set("a1", true), set("a2", true), set("a3", true)), ex("b", "x", set("b1", true), set("b2", true)))
        val noRank = SupersetRules.afterCompleting(missing, "a", "a3")
        assertFalse(noRank.transition)
        assertNull(noRank.next)

        val done = listOf(ex("a", "x", set("a1", true), set("a2")), ex("b", "x", set("b1", true), set("b2")))
        val validated = SupersetRules.afterCompleting(done, "a", "a1")
        assertFalse(validated.transition)
        assertEquals("a2", validated.next!!.set.id)
    }

    @Test
    fun `une série de chauffe validée n'enchaîne pas, le pavé reste sur son exercice`() {
        val list = listOf(
            ex("a", "x", set("aw", true, warmup = true), set("a1")),
            ex("b", "x", set("bw", warmup = true), set("b1")),
        )
        val after = SupersetRules.afterCompleting(list, "a", "aw")
        assertFalse(after.transition)
        assertEquals("a1", after.next!!.set.id)
        // La série de travail qui suit enchaîne sur la chauffe du suivant.
        val work = SupersetRules.afterCompleting(list.map { e -> e.copy(sets = e.sets.map { if (it.id == "a1") it.copy(completed = true) else it }) }, "a", "a1")
        assertTrue(work.transition)
        assertEquals("bw", work.next!!.set.id)
    }

    @Test
    fun `hors supersérie, pas de transition et la série suivante du même exercice`() {
        val list = listOf(ex("a", null, set("a1", true), set("a2")), ex("b", null, set("b1")))
        val after = SupersetRules.afterCompleting(list, "a", "a1")
        assertFalse(after.transition)
        assertEquals("a2", after.next!!.set.id)
        assertNull(SupersetRules.afterCompleting(list, "a", "a2").next)
        assertNull(SupersetRules.afterCompleting(list, "a", "absente").next)
        assertFalse(SupersetRules.afterCompleting(list, "absent", "a1").transition)
    }

    // ─── Notification et repos ───────────────────────────────────────────────────

    @Test
    fun `la prochaine série de la notification suit l'ordre d'enchaînement`() {
        val afterA1 = workout(ex("a", "x", set("a1", true), set("a2")), ex("b", "x", set("b1"), set("b2")))
        assertEquals("b1", WorkoutNotificationRules.nextSet(afterA1)!!.set.id)
        val afterB1 = workout(ex("a", "x", set("a1", true), set("a2")), ex("b", "x", set("b1", true), set("b2")))
        assertEquals("a2", WorkoutNotificationRules.nextSet(afterB1)!!.set.id)
        // B1 sautée : après A2, on continue par B2.
        val skipped = workout(ex("a", "x", set("a1", true), set("a2", true)), ex("b", "x", set("b1"), set("b2")))
        assertEquals("b2", WorkoutNotificationRules.nextSet(skipped)!!.set.id)
    }

    @Test
    fun `le repos se place après la dernière série validée dans l'ordre d'enchaînement`() {
        val session = workout(ex("a", "x", set("a1", true), set("a2", true)), ex("b", "x", set("b1", true), set("b2")))
        assertEquals("a2", SessionInsights.restAnchor(session))
        val plain = workout(ex("a", null, set("a1", true), set("a2", true)), ex("b", null, set("b1", true), set("b2")))
        assertEquals("b1", SessionInsights.restAnchor(plain))
    }
}
