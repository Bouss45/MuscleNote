package com.musclenote.android.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Vérification et notification des mises à jour (§ 3.17, § 15.14) : versions et `versionCode`,
 * réponse de l'API GitHub, décision de notifier, réglage par défaut, prochaine vérification, notes.
 */
class UpdateRulesTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun paris(local: String): Instant = LocalDateTime.parse(local).atZone(paris).toInstant()

    // --- Versions -----------------------------------------------------------------------------

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
    fun `le tag v1 0 n donne le versionCode 1000 plus n de release yml`() {
        assertEquals(1011, UpdateRules.releaseVersionCode("1.0.11"))
        assertEquals(1000, UpdateRules.releaseVersionCode("1.0.0"))
        assertEquals(1142, UpdateRules.releaseVersionCode("1.0.142"))
        // Autre schéma : pas de versionCode déduit.
        assertNull(UpdateRules.releaseVersionCode("1.1.0"))
        assertNull(UpdateRules.releaseVersionCode("1.0"))
        assertNull(UpdateRules.releaseVersionCode("1.0.11-beta"))
        assertNull(UpdateRules.releaseVersionCode("2.0.3"))
    }

    @Test
    fun `une Release v1 0 n se compare au versionCode installé`() {
        assertTrue(UpdateRules.isNewerThanInstalled("1.0.12", installedName = "1.0.11", installedCode = 1011))
        assertFalse(UpdateRules.isNewerThanInstalled("1.0.11", installedName = "1.0.11", installedCode = 1011))
        assertFalse(UpdateRules.isNewerThanInstalled("1.0.10", installedName = "1.0.11", installedCode = 1011))
        // Le versionCode l'emporte sur le nom (un nom de build local ne dit rien).
        assertFalse(UpdateRules.isNewerThanInstalled("1.0.12", installedName = "0.1.0-dev", installedCode = 1012))
        // Build local (versionCode 1) : toute Release est plus récente.
        assertTrue(UpdateRules.isNewerThanInstalled("1.0.0", installedName = "0.1.0-dev-preview", installedCode = 1))
    }

    @Test
    fun `une Release d'un autre schéma se compare au nom de la version installée`() {
        assertTrue(UpdateRules.isNewerThanInstalled("1.1.0", installedName = "1.0.42", installedCode = 1042))
        assertFalse(UpdateRules.isNewerThanInstalled("1.1.0", installedName = "1.1.0", installedCode = 1042))
        assertFalse(UpdateRules.isNewerThanInstalled("latest", installedName = "1.0.42", installedCode = 1042))
    }

    @Test
    fun `une Release plus récente que l'app installée est proposée`() {
        val state = UpdateCheckState(checkedAt = 1, latestVersion = "1.0.5", pageUrl = "page", apkUrl = "apk")
        assertEquals(LatestRelease("1.0.5", "page", "apk"), UpdateRules.available(state, "1.0.4", 1004))
        assertNull(UpdateRules.available(state, "1.0.5", 1005))
        assertNull(UpdateRules.available(UpdateCheckState.DEFAULT, "1.0.4", 1004))
    }

    // --- Réponse de l'API ---------------------------------------------------------------------

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
    fun `les notes de la Release donnent la première ligne de la notification`() {
        val body = """
            {
              "html_url": "https://github.com/o/r/releases/tag/v1.0.11",
              "tag_name": "v1.0.11",
              "body": "MuscleNote 1.0.11 (versionCode 1011), commit f8dc39d.\r\n\r\n## Changements\r\n- Pesée facultative au début de séance et rappel de pesée (#10)\r\n- Autre chose\r\n\r\nTous les commits : https://github.com/o/r/compare/v1.0.10...v1.0.11"
            }
        """.trimIndent()
        assertEquals("Pesée facultative au début de séance et rappel de pesée (#10)", UpdateRules.parseLatest(body)?.notes)
        val noBody = """{ "html_url": "p", "tag_name": "v1.0.12", "body": null }"""
        assertNull(UpdateRules.parseLatest(noBody)?.notes)
        assertEquals("1.0.12", UpdateRules.parseLatest(noBody)?.version)
    }

    @Test
    fun `sans APK la page de la Release reste proposée`() {
        val body = """{ "html_url": "https://github.com/o/r/releases/tag/v1.0.8", "tag_name": "v1.0.8", "assets": [] }"""
        assertEquals(LatestRelease("1.0.8", "https://github.com/o/r/releases/tag/v1.0.8", null), UpdateRules.parseLatest(body))
    }

    @Test
    fun `une réponse invalide ne donne rien`() {
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "v1.0.9", "draft": true }"""))
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "v1.0.9", "prerelease": true }"""))
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p", "tag_name": "latest" }"""))
        assertNull(UpdateRules.parseLatest("""{ "tag_name": "v1.0.9" }"""))
        assertNull(UpdateRules.parseLatest("""{ "html_url": "p" }"""))
        assertNull(UpdateRules.parseLatest("""{ "message": "API rate limit exceeded" }"""))
        assertNull(UpdateRules.parseLatest("""[]"""))
        assertNull(UpdateRules.parseLatest("<html>"))
        assertNull(UpdateRules.parseLatest(""))
    }

    // --- Première ligne des notes -------------------------------------------------------------

    @Test
    fun `la première ligne des notes est le premier élément de liste`() {
        assertEquals("Premier", UpdateRules.notesFirstLine("MuscleNote 1.0.2.\n\n## Changements\n-   Premier  \n- Second"))
        assertEquals("Étoile", UpdateRules.notesFirstLine("Intro\n* Étoile\n- Tiret"))
        // Markdown laissé tel quel, élément vide sauté.
        assertEquals("**Gras** et `code`", UpdateRules.notesFirstLine("- \n- **Gras** et `code`"))
    }

    @Test
    fun `sans élément de liste ni texte, pas de première ligne`() {
        assertNull(UpdateRules.notesFirstLine("MuscleNote 1.0.3 (versionCode 1003), commit abc.\n\n## Changements\nAucun changement dans l'app : nouvelle compilation."))
        assertNull(UpdateRules.notesFirstLine(""))
        assertNull(UpdateRules.notesFirstLine(null))
        // Un tiret sans espace n'est pas un élément de liste.
        assertNull(UpdateRules.notesFirstLine("-pas une liste"))
    }

    @Test
    fun `une première ligne trop longue est coupée à 200 caractères sans couper un emoji`() {
        val long = "💪".repeat(250)
        val line = UpdateRules.notesFirstLine("- $long")!!
        assertEquals(200, line.codePointCount(0, line.length))
        assertTrue(line.endsWith("…"))
        assertEquals("💪".repeat(199) + "…", line)
        val exact = "a".repeat(200)
        assertEquals(exact, UpdateRules.notesFirstLine("- $exact"))
    }

    // --- Notifier ou non ----------------------------------------------------------------------

    private val installedName = "1.0.10"
    private val installedCode = 1010

    private fun notify(version: String, state: UpdateCheckState = UpdateCheckState.DEFAULT, enabled: Boolean = true) =
        UpdateRules.shouldNotify(version, state, installedName, installedCode, enabled)

    @Test
    fun `une nouvelle version est notifiée si le réglage est activé`() {
        assertTrue(notify("1.0.11"))
        assertFalse(notify("1.0.11", enabled = false))
    }

    @Test
    fun `une version qui n'est pas plus récente que l'app n'est pas notifiée`() {
        assertFalse(notify("1.0.10"))
        assertFalse(notify("1.0.9"))
    }

    @Test
    fun `une version ignorée n'est pas notifiée, la suivante oui`() {
        val ignored = UpdateCheckState(dismissedVersion = "1.0.11")
        assertFalse(notify("1.0.11", ignored))
        assertTrue(notify("1.0.12", ignored))
    }

    @Test
    fun `une version n'est notifiée qu'une fois`() {
        assertFalse(notify("1.0.11", UpdateCheckState(notifiedVersion = "1.0.11")))
    }

    @Test
    fun `une version intermédiaire n'est jamais notifiée après une plus récente`() {
        // 1.0.11 à 1.0.13 publiées depuis la 1.0.10 : seule la dernière, la 1.0.13, est notifiée…
        assertTrue(notify("1.0.13"))
        // … et une Release plus ancienne revue ensuite (Release retirée) ne l'est pas.
        assertFalse(notify("1.0.12", UpdateCheckState(notifiedVersion = "1.0.13")))
    }

    @Test
    fun `une version plus récente que la dernière notifiée l'est, et remplace l'ancienne`() {
        assertTrue(notify("1.0.13", UpdateCheckState(notifiedVersion = "1.0.12")))
    }

    @Test
    fun `la notification est retirée quand la version notifiée est installée`() {
        val state = UpdateCheckState(notifiedVersion = "1.0.12")
        assertTrue(UpdateRules.isNotificationStale(state, installedName = "1.0.12", installedCode = 1012))
        assertTrue(UpdateRules.isNotificationStale(state, installedName = "1.0.13", installedCode = 1013))
        assertFalse(UpdateRules.isNotificationStale(state, installedName = "1.0.11", installedCode = 1011))
        assertFalse(UpdateRules.isNotificationStale(UpdateCheckState.DEFAULT, installedName = "1.0.12", installedCode = 1012))
    }

    // --- Réglage par défaut et source d'installation ------------------------------------------

    @Test
    fun `Obtainium et le nom du Play Store comptent comme installation par Obtainium`() {
        assertTrue(UpdateRules.isInstalledByObtainium(listOf("dev.imranr.obtainium")))
        assertTrue(UpdateRules.isInstalledByObtainium(listOf("dev.imranr.obtainium.fdroid")))
        assertTrue(UpdateRules.isInstalledByObtainium(listOf("com.android.vending")))
        // Initiateur Obtainium, installateur système.
        assertTrue(UpdateRules.isInstalledByObtainium(listOf("com.google.android.packageinstaller", "dev.imranr.obtainium")))
        assertFalse(UpdateRules.isInstalledByObtainium(listOf("com.google.android.packageinstaller")))
        assertFalse(UpdateRules.isInstalledByObtainium(listOf("com.android.shell")))
        assertFalse(UpdateRules.isInstalledByObtainium(emptyList()))
    }

    @Test
    fun `par défaut la notification est coupée avec Obtainium et activée sinon`() {
        assertFalse(UpdateRules.notifyEnabled(stored = null, installedByObtainium = true))
        assertTrue(UpdateRules.notifyEnabled(stored = null, installedByObtainium = false))
    }

    @Test
    fun `un choix enregistré l'emporte sur la source d'installation`() {
        assertTrue(UpdateRules.notifyEnabled(stored = true, installedByObtainium = true))
        assertFalse(UpdateRules.notifyEnabled(stored = false, installedByObtainium = false))
    }

    @Test
    fun `Voir ouvre d'abord l'Obtainium qui a installé l'app`() {
        assertEquals(
            listOf("dev.imranr.obtainium.fdroid", "dev.imranr.obtainium"),
            UpdateRules.obtainiumPackagesToOpen(listOf("com.google.android.packageinstaller", "dev.imranr.obtainium.fdroid")),
        )
        assertEquals(listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid"), UpdateRules.obtainiumPackagesToOpen(listOf("com.android.vending")))
        // Installée sans Obtainium : page de la Release.
        assertEquals(emptyList<String>(), UpdateRules.obtainiumPackagesToOpen(listOf("com.google.android.packageinstaller")))
    }

    // --- Prochaine vérification ---------------------------------------------------------------

    @Test
    fun `la vérification quotidienne a lieu à la prochaine 18 h locale`() {
        assertEquals(paris("2026-11-15T18:00"), UpdateRules.nextCheckAt(paris("2026-11-15T09:30"), paris).toInstant())
        assertEquals(paris("2026-11-15T18:00"), UpdateRules.nextCheckAt(paris("2026-11-15T17:59:59"), paris).toInstant())
        // À 18 h pile ou après : le lendemain.
        assertEquals(paris("2026-11-16T18:00"), UpdateRules.nextCheckAt(paris("2026-11-15T18:00"), paris).toInstant())
        assertEquals(paris("2026-11-16T18:00"), UpdateRules.nextCheckAt(paris("2026-11-15T23:10"), paris).toInstant())
    }

    @Test
    fun `la vérification reste à 18 h à l'horloge après un changement d'heure`() {
        // Passage à l'heure d'été (28 mars 2027) : 18:00 CEST = 16:00 UTC, 23 h après la veille.
        val summer = UpdateRules.nextCheckAt(paris("2027-03-27T18:30"), paris)
        assertEquals(LocalDateTime.parse("2027-03-28T18:00"), summer.toLocalDateTime())
        assertEquals(Instant.parse("2027-03-28T16:00:00Z"), summer.toInstant())
        // Passage à l'heure d'hiver (25 octobre 2026) : 18:00 CET = 17:00 UTC.
        val winter = UpdateRules.nextCheckAt(paris("2026-10-24T19:00"), paris)
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), winter.toInstant())
    }
}
