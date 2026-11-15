package com.musclenote.android.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Place d'une ligne dans un groupe de liste paresseuse (DESIGN.md § 9.1, listes groupées v2). */
class GroupPositionTest {

    @Test
    fun `une seule ligne est arrondie en haut et en bas, sans filet`() {
        assertEquals(GroupPosition.Single, GroupPosition.of(0, 1))
        assertTrue(GroupPosition.Single.roundTop)
        assertTrue(GroupPosition.Single.isLast)
    }

    @Test
    fun `premier, milieu et dernier d'un groupe de trois`() {
        assertEquals(listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Last), (0..2).map { GroupPosition.of(it, 3) })
        assertTrue(GroupPosition.First.roundTop)
        assertFalse(GroupPosition.First.roundBottom)
        assertFalse(GroupPosition.Middle.roundTop)
        assertFalse(GroupPosition.Middle.isLast)
        assertTrue(GroupPosition.Last.roundBottom)
    }

    @Test
    fun `deux lignes, la première avec un filet et la seconde sans`() {
        assertEquals(GroupPosition.First, GroupPosition.of(0, 2))
        assertEquals(GroupPosition.Last, GroupPosition.of(1, 2))
        assertFalse(GroupPosition.of(0, 2).isLast)
        assertTrue(GroupPosition.of(1, 2).isLast)
    }
}
