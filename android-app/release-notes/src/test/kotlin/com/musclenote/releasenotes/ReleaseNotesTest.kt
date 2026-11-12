package com.musclenote.releasenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notes de version (SPEC § 15.1) : lecture de `git log`, sélection des commits, texte. */
class ReleaseNotesTest {

    private fun commit(subject: String, vararg files: String, parents: Int = 1) = Commit(
        hash = "h-$subject",
        parents = List(parents) { "p$it" },
        subject = subject,
        files = files.toList(),
    )

    private val header = ReleaseNotes.header("1.0.12", "1012", "1a2b3c4d5e6f")

    @Test
    fun `la sortie de git log est lue commit par commit`() {
        val log = "\u001eaaa\u001fppp\u001fSauvegarde automatique : choix du dossier\n\n" +
            "android-app/app/src/main/A.kt\nSPEC.md\n" +
            "\u001ebbb\u001fppp qqq\u001fMerge pull request #4 from Bouss45/feature/lot1\n" +
            "\u001eccc\u001fbbb\u001fNoms : « É » et \"guillemets\"\n\n\"docs/é t.md\"\n"
        val commits = ReleaseNotes.parseGitLog(log)
        assertEquals(3, commits.size)
        assertEquals(Commit("aaa", listOf("ppp"), "Sauvegarde automatique : choix du dossier", listOf("android-app/app/src/main/A.kt", "SPEC.md")), commits[0])
        assertEquals(listOf("ppp", "qqq"), commits[1].parents)
        assertEquals(emptyList<String>(), commits[1].files)
        assertEquals("Noms : « É » et \"guillemets\"", commits[2].subject)
        assertEquals(listOf("docs/é t.md"), commits[2].files)
    }

    @Test
    fun `une sortie vide ne donne aucun commit`() {
        assertEquals(emptyList<Commit>(), ReleaseNotes.parseGitLog(""))
        assertEquals(emptyList<Commit>(), ReleaseNotes.parseGitLog("\n"))
    }

    @Test
    fun `la documentation et la CI sont reconnues`() {
        assertTrue(ReleaseNotes.isDocOrCi("SPEC.md"))
        assertTrue(ReleaseNotes.isDocOrCi("android-app/README.MD"))
        assertTrue(ReleaseNotes.isDocOrCi(".github/workflows/release.yml"))
        assertFalse(ReleaseNotes.isDocOrCi("android-app/app/src/main/res/values/strings.xml"))
        assertFalse(ReleaseNotes.isDocOrCi("android-app/.github-like/x"))
        assertFalse(ReleaseNotes.isDocOrCi("android-app/app/src/main/java/Markdown.kt"))
    }

    @Test
    fun `les fusions et les commits de doc ou de CI seuls ne sont pas listés`() {
        val app = commit("Health Connect : envoi des séances", "android-app/app/src/main/H.kt")
        val mixed = commit("Notes de version", "android-app/release-notes/R.kt", ".github/workflows/release.yml", "CLAUDE.md")
        val docs = commit("SPEC : lot 1", "SPEC.md", "README.md")
        val ci = commit("CI : cache Gradle", ".github/workflows/release.yml")
        val docsAndCi = commit("Doc et CI", "CLAUDE.md", ".github/workflows/checks.yml")
        val merge = commit("Merge pull request #4", "android-app/app/src/main/H.kt", parents = 2)
        val empty = commit("Commit vide")
        val root = commit("Premier commit", "android-app/build.gradle.kts", parents = 0)

        assertEquals(
            listOf(app, mixed, root),
            ReleaseNotes.select(listOf(app, mixed, docs, ci, docsAndCi, merge, empty, root)),
        )
    }

    @Test
    fun `le texte liste les titres du plus ancien au plus récent avec le lien de comparaison`() {
        val text = ReleaseNotes.render(
            header,
            listOf(
                commit("Sauvegarde automatique : choix du dossier", "android-app/a.kt"),
                commit("SPEC : lot 1", "SPEC.md"),
                commit("Health Connect : envoi des séances et du poids", "android-app/b.kt"),
            ),
            "https://github.com/Bouss45/MuscleNote/compare/v1.0.11...v1.0.12",
        )
        assertEquals(
            """
            MuscleNote 1.0.12 (versionCode 1012), commit 1a2b3c4.

            ## Changements
            - Sauvegarde automatique : choix du dossier
            - Health Connect : envoi des séances et du poids

            Tous les commits : https://github.com/Bouss45/MuscleNote/compare/v1.0.11...v1.0.12

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `sans commit listé le texte le dit`() {
        val text = ReleaseNotes.render(header, listOf(commit("CI", ".github/workflows/release.yml")), null)
        assertEquals("MuscleNote 1.0.12 (versionCode 1012), commit 1a2b3c4.\n\nAucun changement dans l'app : nouvelle compilation.\n", text)
    }

    @Test
    fun `sans Release précédente la liste est indisponible`() {
        val text = ReleaseNotes.render(header, null, "https://example.invalid/compare")
        assertEquals("MuscleNote 1.0.12 (versionCode 1012), commit 1a2b3c4.\n\nListe des changements indisponible.\n", text)
    }

    @Test
    fun `au-delà de 50 commits les suivants sont comptés`() {
        val many = (1..53).map { commit("Changement $it", "android-app/f$it.kt") }
        val lines = ReleaseNotes.render(header, many, null).lines()
        assertTrue("- Changement 50" in lines)
        assertFalse("- Changement 51" in lines)
        assertTrue("- … et 3 autres changements" in lines)

        val oneMore = ReleaseNotes.render(header, many.take(51), null).lines()
        assertTrue("- … et 1 autre changement" in oneMore)
        val exact = ReleaseNotes.render(header, many.take(50), null)
        assertFalse("autre" in exact)
    }
}
