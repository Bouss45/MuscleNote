package com.musclenote.android.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérification des mises à jour (§ 3.17) : versions, échéance, réponse de l'API GitHub. */
class UpdateRulesTest {

    @Test
    fun `les nombres d'une version sont lus quel que soit le préfixe ou le suffixe`() {
        assertEquals(listOf(1, 0, 12), UpdateRules.versionParts("v1.0.12"))
        assertEquals(listOf(0, 1, 0), UpdateRules.versionParts("0.1.0-dev"))
        assertEquals(emptyList<Int>(), UpdateRules.versionParts("latest"))
    }

    @Test
    fun `une version est plus récente nombre par nombre`() {
        assertTrue(UpdateRules.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateRules.isNewer("1.1", "1.0.42"))
        assertTrue(UpdateRules.isNewer("1.0.1", "0.1.0-dev"))
        assertFalse(UpdateRules.isNewer("1.0.9", "1.0.10"))
        assertFalse(UpdateRules.isNewer("1.0.4", "1.0.4"))
        assertFalse(UpdateRules.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateRules.isNewer("latest", "1.0.4"))
    }

    @Test
    fun `la vérification automatique est due une fois par jour`() {
        val day = UpdateRules.CHECK_INTERVAL_MS
        assertTrue(UpdateRules.isDue(checkedAt = 0, now = 1_000))
        assertFalse(UpdateRules.isDue(checkedAt = 10_000, now = 10_000 + day - 1))
        assertTrue(UpdateRules.isDue(checkedAt = 10_000, now = 10_000 + day))
        assertTrue(UpdateRules.isDue(checkedAt = 10_000, now = 5_000)) // horloge reculée
    }

    @Test
    fun `une Release plus récente que la version installée est proposée`() {
        val state = UpdateCheckState(checkedAt = 1, latestVersion = "1.0.5", pageUrl = "page", apkUrl = "apk")
        assertEquals(LatestRelease("1.0.5", "page", "apk"), UpdateRules.available(state, "1.0.4"))
        assertNull(UpdateRules.available(state, "1.0.5"))
        assertNull(UpdateRules.available(UpdateCheckState.DEFAULT, "1.0.4"))
    }

    @Test
    fun `la réponse de l'API donne la version, la page et l'APK`() {
        val body = """
            {
              "url": "https://api.github.com/repos/o/r/releases/1",
              "html_url": "https://github.com/o/r/releases/tag/v1.0.7",
              "tag_name": "v1.0.7",
              "name": "MuscleNote v1.0.7",
              "draft": false,
              "prerelease": false,
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://example.test/notes.txt", "size": 3 },
                { "name": "MuscleNote-v1.0.7.apk", "browser_download_url": "https://example.test/MuscleNote-v1.0.7.apk", "size": 9 }
              ]
            }
        """.trimIndent()
        assertEquals(
            LatestRelease("1.0.7", "https://github.com/o/r/releases/tag/v1.0.7", "https://example.test/MuscleNote-v1.0.7.apk"),
            UpdateRules.parseLatest(body),
        )
    }

    @Test
    fun `sans APK la page de la Release reste proposée`() {
        val body = """{ "html_url": "https://github.com/o/r/releases/tag/v1.0.8", "tag_name": "v1.0.8", "assets": [] }"""
        assertEquals(LatestRelease("1.0.8", "https://github.com/o/r/releases/tag/v1.0.8", null), UpdateRules.parseLatest(body))
    }

    @Test
    fun `brouillon, pré-release, tag sans version ou réponse illisible ne donnent rien`() {
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "v1.0.9", "draft": true }"""))
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "v1.0.9", "prerelease": true }"""))
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "latest" }"""))
        assertNull(UpdateRules.parseLatest("""{ "message": "API rate limit exceeded" }"""))
        assertNull(UpdateRules.parseLatest("<html>"))
    }
}
