package com.musclenote.android.data.repository

import com.musclenote.android.data.feedback.FeedbackDraft
import com.musclenote.android.data.feedback.FeedbackEnvironment
import com.musclenote.android.data.feedback.FeedbackOutcome
import com.musclenote.android.data.feedback.FeedbackRules
import com.musclenote.android.data.feedback.FeedbackType
import com.musclenote.android.data.feedback.WebhookReply
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** Dépôt de l'envoi d'un avis (§ 14) : brouillon, issues, délai entre deux envois. */
class FeedbackRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock: Instant = Instant.parse("2026-11-12T08:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = clock
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }

    private val environment = FeedbackEnvironment("1.0.42 (1042)", "15 (API 35)", "Google Pixel 8", "fr-FR")
    private val dataDir get() = File(tmp.root, "data")
    private val posted = mutableListOf<String>()
    private var reply: () -> WebhookReply = { WebhookReply(204) }
    private val sender = object : FeedbackSender {
        override suspend fun post(json: String): WebhookReply {
            posted += json
            return reply()
        }
    }

    private suspend fun CoroutineScope.repository(withSender: Boolean = true): FeedbackRepository {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        return FeedbackRepository(files.feedback, time, if (withSender) sender else null).also { it.load() }
    }

    private fun FeedbackRepository.fill(message: String = "Pouvoir réordonner les compléments", name: String = "Théo") {
        setType(FeedbackType.Idea)
        setMessage(message)
        setName(name)
    }

    @Test
    fun `envoi reussi, formulaire vide et une minute d'attente enregistree`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            repo.setType(FeedbackType.Bug)
            assertEquals(FeedbackOutcome.Sent, repo.send(environment))
            assertEquals(1, posted.size)
            assertTrue(posted.single().contains("Pouvoir réordonner les compléments"))
            assertTrue(posted.single().contains("🐞 Bug"))
            assertEquals(FeedbackDraft.EMPTY, repo.draft.value)
            assertEquals(1, repo.resets.value)
            assertEquals(FeedbackOutcome.Sent, repo.result.value)
            assertFalse(repo.sending.value)
            assertEquals(clock.toEpochMilli(), repo.throttle.value.lastSentAt)
            assertEquals(FeedbackRules.MIN_INTERVAL_MS, repo.remainingWait())
        }
        // Relu depuis le disque : fermer l'app ne remet pas le délai à zéro.
        coroutineScope { assertEquals(FeedbackRules.MIN_INTERVAL_MS, repository().remainingWait()) }
    }

    @Test
    fun `pas de deuxieme envoi avant une minute`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            repo.send(environment)
            repo.fill(message = "Deuxième avis")
            clock = clock.plusSeconds(59)
            assertNull(repo.send(environment))
            assertEquals(1, posted.size)
            assertEquals("Deuxième avis", repo.draft.value.message)
            clock = clock.plusSeconds(1)
            assertEquals(FeedbackOutcome.Sent, repo.send(environment))
            assertEquals(2, posted.size)
        }
    }

    @Test
    fun `pas de reseau, texte conserve et nouvel essai permis tout de suite`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            reply = { throw IOException("Unable to resolve host") }
            assertEquals(FeedbackOutcome.NoConnection, repo.send(environment))
            assertEquals("Pouvoir réordonner les compléments", repo.draft.value.message)
            assertEquals("Théo", repo.draft.value.name)
            assertEquals(0, repo.resets.value)
            assertEquals(0, repo.remainingWait())
            reply = { WebhookReply(204) }
            assertEquals(FeedbackOutcome.Sent, repo.send(environment))
            assertEquals(2, posted.size)
        }
    }

    @Test
    fun `erreur 4xx ou 5xx, texte conserve et code rapporte`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            reply = { WebhookReply(500) }
            assertEquals(FeedbackOutcome.Failed(500), repo.send(environment))
            reply = { WebhookReply(404) }
            assertEquals(FeedbackOutcome.Failed(404), repo.send(environment))
            assertEquals("Pouvoir réordonner les compléments", repo.draft.value.message)
            assertEquals(FeedbackOutcome.Failed(404), repo.result.value)
            assertEquals(0, repo.remainingWait())
        }
    }

    @Test
    fun `reponse 429, texte conserve et envoi bloque pendant le delai demande`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            reply = { WebhookReply(429, """{"retry_after": 120}""") }
            assertEquals(FeedbackOutcome.RateLimited(120_000), repo.send(environment))
            assertEquals("Pouvoir réordonner les compléments", repo.draft.value.message)
            assertEquals(120_000, repo.remainingWait())
            reply = { WebhookReply(204) }
            clock = clock.plusSeconds(119)
            assertNull(repo.send(environment))
            assertEquals(1, posted.size)
            clock = clock.plusSeconds(1)
            assertEquals(FeedbackOutcome.Sent, repo.send(environment))
        }
        coroutineScope { assertEquals(clock.toEpochMilli(), repository().throttle.value.lastSentAt) }
    }

    @Test
    fun `message vide, rien n'est envoye`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill(message = "  \n ")
            assertNull(repo.send(environment))
            assertTrue(posted.isEmpty())
            assertNull(repo.result.value)
        }
    }

    @Test
    fun `modifier le formulaire efface l'issue affichee`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.fill()
            reply = { WebhookReply(500) }
            repo.send(environment)
            repo.setType(FeedbackType.Idea)
            assertEquals(FeedbackOutcome.Failed(500), repo.result.value)
            repo.setMessage("Pouvoir réordonner les compléments !")
            assertNull(repo.result.value)
        }
    }

    @Test
    fun `saisie limitee a 1000 caracteres pour le message et 32 pour le pseudo`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository()
            repo.setMessage("a".repeat(1200))
            repo.setName("b".repeat(40))
            assertEquals(1000, repo.draft.value.message.length)
            assertEquals(32, repo.draft.value.name.length)
        }
    }

    @Test
    fun `sans URL de webhook, section masquee et aucun envoi`() = runBlocking<Unit> {
        coroutineScope {
            val repo = repository(withSender = false)
            assertFalse(repo.enabled)
            repo.fill()
            assertNull(repo.send(environment))
            assertTrue(posted.isEmpty())
        }
    }
}
