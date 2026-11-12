package com.musclenote.android.data.feedback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Envoi d'un avis (§ 14) : corps JSON, nettoyage du formulaire, délais, réponse du webhook. */
class FeedbackRulesTest {

    private val environment = FeedbackEnvironment(
        appVersion = "1.0.42 (1042)",
        androidVersion = "15 (API 35)",
        device = "Google Pixel 8",
        language = "fr-FR",
    )

    private fun parse(draft: FeedbackDraft): JsonObject = Json.parseToJsonElement(FeedbackRules.payload(draft, environment)).jsonObject

    private fun JsonObject.embed(): JsonObject = getValue("embeds").jsonArray.single().jsonObject

    private fun JsonObject.fields(): Map<String, String> =
        embed().getValue("fields").jsonArray.associate {
            val field = it.jsonObject
            field.getValue("name").jsonPrimitive.content to field.getValue("value").jsonPrimitive.content
        }

    // --- Corps JSON ---

    @Test
    fun `corps du message, un embed avec le type, le message, le pseudo et les infos jointes`() {
        val body = parse(FeedbackDraft(FeedbackType.Bug, "Le minuteur ne sonne pas", "Théo"))
        assertEquals("MuscleNote", body.getValue("username").jsonPrimitive.content)
        val embed = body.embed()
        assertEquals("🐞 Bug", embed.getValue("title").jsonPrimitive.content)
        assertEquals(0xEF4444, embed.getValue("color").jsonPrimitive.int)
        assertEquals("Le minuteur ne sonne pas", embed.getValue("description").jsonPrimitive.content)
        assertEquals(
            linkedMapOf(
                "Pseudo" to "Théo",
                "Version" to "1.0.42 (1042)",
                "Android" to "15 (API 35)",
                "Appareil" to "Google Pixel 8",
                "Langue" to "fr-FR",
            ).toList(),
            body.fields().toList(),
        )
        assertTrue(embed.getValue("fields").jsonArray.all { it.jsonObject.getValue("inline").jsonPrimitive.boolean })
        // Rien d'autre que ce qui est affiché sous le formulaire.
        assertEquals(setOf("username", "allowed_mentions", "embeds"), body.keys)
        assertEquals(setOf("title", "description", "color", "fields"), embed.keys)
    }

    @Test
    fun `titre et couleur de chaque type`() {
        assertEquals("💡 Idée", parse(FeedbackDraft(FeedbackType.Idea, "x")).embed().getValue("title").jsonPrimitive.content)
        assertEquals(0xF59E0B, parse(FeedbackDraft(FeedbackType.Idea, "x")).embed().getValue("color").jsonPrimitive.int)
        assertEquals("💬 Autre", parse(FeedbackDraft(FeedbackType.Other, "x")).embed().getValue("title").jsonPrimitive.content)
        assertEquals(0x3B82F6, parse(FeedbackDraft(FeedbackType.Other, "x")).embed().getValue("color").jsonPrimitive.int)
    }

    @Test
    fun `allowed_mentions vide, aucune mention ne peut notifier`() {
        val body = parse(FeedbackDraft(message = "@everyone @here <@123456789012345678> <@&42>", name = "@everyone"))
        val allowed = body.getValue("allowed_mentions").jsonObject
        assertEquals(setOf("parse"), allowed.keys)
        assertTrue(allowed.getValue("parse").jsonArray.isEmpty())
        assertTrue(FeedbackRules.payload(FeedbackDraft(message = "x"), environment).contains("\"allowed_mentions\":{\"parse\":[]}"))
    }

    @Test
    fun `guillemets, barres obliques inverses, retours a la ligne et caracteres de controle sont echappes`() {
        val message = "Il a dit \"salut\" \\o/\nDeuxième ligne\tet une tabulation \u0001 fin } ] 😀"
        val raw = FeedbackRules.payload(FeedbackDraft(message = message, name = "a\"b\\c"), environment)
        assertTrue(raw.contains("\\\"salut\\\""))
        assertTrue(raw.contains("\\\\o/"))
        assertTrue(raw.contains("\\n"))
        assertTrue(raw.contains("\\t"))
        assertTrue(raw.contains("\\u0001"))
        assertFalse(raw.contains("\n"))
        // Relu tel quel : le JSON est valide et le texte intact.
        val body = Json.parseToJsonElement(raw).jsonObject
        assertEquals(message, body.embed().getValue("description").jsonPrimitive.content)
        assertEquals("a\"b\\c", body.fields().getValue("Pseudo"))
    }

    @Test
    fun `pseudo vide ou fait d'espaces, envoye comme anonyme`() {
        assertEquals("anonyme", parse(FeedbackDraft(message = "x")).fields().getValue("Pseudo"))
        assertEquals("anonyme", parse(FeedbackDraft(message = "x", name = "  \n ")).fields().getValue("Pseudo"))
    }

    @Test
    fun `message tronque a 1000 caracteres et nettoye de ses espaces`() {
        val long = "  " + "a".repeat(1500)
        val description = parse(FeedbackDraft(message = long)).embed().getValue("description").jsonPrimitive.content
        assertEquals("a".repeat(1000), description)
        assertEquals("Salut", parse(FeedbackDraft(message = "\n  Salut \n")).embed().getValue("description").jsonPrimitive.content)
    }

    @Test
    fun `la troncature ne coupe jamais un emoji en deux`() {
        val message = "a".repeat(999) + "😀😀"
        val description = parse(FeedbackDraft(message = message)).embed().getValue("description").jsonPrimitive.content
        assertEquals("a".repeat(999) + "😀", description)
        assertEquals(1000, FeedbackRules.length(description))
        assertEquals(1001, description.length)
        assertEquals("😀😀", FeedbackRules.truncate("😀😀😀", 2))
        assertEquals("abc", FeedbackRules.truncate("abc", 5))
    }

    @Test
    fun `pseudo sur une ligne et tronque a 32 caracteres`() {
        assertEquals("Théo le costaud", parse(FeedbackDraft(message = "x", name = " Théo\nle\r\ncostaud ")).fields().getValue("Pseudo"))
        assertEquals("b".repeat(32), parse(FeedbackDraft(message = "x", name = "b".repeat(50))).fields().getValue("Pseudo"))
    }

    @Test
    fun `informations jointes vides ou trop longues`() {
        val odd = FeedbackEnvironment(appVersion = "", androidVersion = "15\n(API 35)", device = "x".repeat(300), language = " ")
        val fields = Json.parseToJsonElement(FeedbackRules.payload(FeedbackDraft(message = "x"), odd)).jsonObject.fields()
        assertEquals("?", fields.getValue("Version"))
        assertEquals("15 (API 35)", fields.getValue("Android"))
        assertEquals(100, fields.getValue("Appareil").length)
        assertEquals("?", fields.getValue("Langue"))
    }

    // --- Validation du formulaire ---

    @Test
    fun `message vide ou fait d'espaces, envoi impossible`() {
        assertFalse(FeedbackRules.isSendable(FeedbackDraft()))
        assertFalse(FeedbackRules.isSendable(FeedbackDraft(message = "   \n\t ")))
        assertFalse(FeedbackRules.isSendable(FeedbackDraft(message = "", name = "Théo")))
        assertTrue(FeedbackRules.isSendable(FeedbackDraft(message = " x ")))
        assertNull(FeedbackRules.cleanMessage(" \n "))
        assertEquals("x", FeedbackRules.cleanMessage(" x "))
        assertNull(FeedbackRules.cleanName("   "))
    }

    @Test
    fun `longueur comptee en caracteres, un emoji compte pour 1`() {
        assertEquals(0, FeedbackRules.length(""))
        assertEquals(3, FeedbackRules.length("abc"))
        assertEquals(2, FeedbackRules.length("😀é"))
    }

    @Test
    fun `seules les URL de webhook Discord sont acceptees`() {
        assertTrue(FeedbackRules.isWebhookUrl("https://discord.com/api/webhooks/123456789012345678/AbC-d_e"))
        assertTrue(FeedbackRules.isWebhookUrl("https://discordapp.com/api/webhooks/1/x"))
        assertFalse(FeedbackRules.isWebhookUrl(""))
        assertFalse(FeedbackRules.isWebhookUrl("http://discord.com/api/webhooks/1/x"))
        assertFalse(FeedbackRules.isWebhookUrl("https://evil.example/api/webhooks/1/x"))
        assertFalse(FeedbackRules.isWebhookUrl("https://discord.com/api/webhooks/1/x?wait=true"))
        assertFalse(FeedbackRules.isWebhookUrl("https://discord.com/api/webhooks/abc/x"))
    }

    // --- Délai entre deux envois ---

    private val t0 = 1_800_000_000_000L

    @Test
    fun `premier envoi, aucune attente`() {
        assertEquals(0, FeedbackRules.remainingWait(FeedbackThrottle.DEFAULT, t0))
    }

    @Test
    fun `une minute entre deux envois reussis`() {
        val throttle = FeedbackThrottle(lastSentAt = t0)
        assertEquals(60_000, FeedbackRules.remainingWait(throttle, t0))
        assertEquals(1_000, FeedbackRules.remainingWait(throttle, t0 + 59_000))
        assertEquals(0, FeedbackRules.remainingWait(throttle, t0 + 60_000))
        assertEquals(0, FeedbackRules.remainingWait(throttle, t0 + 3_600_000))
    }

    @Test
    fun `apres un 429, attente jusqu'a la fin du delai demande`() {
        val throttle = FeedbackThrottle(lastSentAt = t0, blockedUntil = t0 + 5 * 60_000)
        assertEquals(5 * 60_000L, FeedbackRules.remainingWait(throttle, t0))
        assertEquals(4 * 60_000L, FeedbackRules.remainingWait(throttle, t0 + 60_000))
        assertEquals(0, FeedbackRules.remainingWait(throttle, t0 + 5 * 60_000))
    }

    @Test
    fun `horloge reculee, attente plafonnee`() {
        assertEquals(60_000, FeedbackRules.remainingWait(FeedbackThrottle(lastSentAt = t0), t0 - 86_400_000))
        assertEquals(3_600_000, FeedbackRules.remainingWait(FeedbackThrottle(blockedUntil = t0), t0 - 86_400_000))
    }

    @Test
    fun `attente affichee en secondes arrondies au-dessus`() {
        assertEquals(0, FeedbackRules.waitSeconds(0))
        assertEquals(1, FeedbackRules.waitSeconds(1))
        assertEquals(42, FeedbackRules.waitSeconds(41_001))
        assertEquals(60, FeedbackRules.waitSeconds(60_000))
    }

    // --- Réponse du webhook ---

    @Test
    fun `reponses 2xx, avis envoye`() {
        assertEquals(FeedbackOutcome.Sent, FeedbackRules.outcome(WebhookReply(204)))
        assertEquals(FeedbackOutcome.Sent, FeedbackRules.outcome(WebhookReply(200)))
    }

    @Test
    fun `reponses 4xx et 5xx, echec avec le code`() {
        assertEquals(FeedbackOutcome.Failed(400), FeedbackRules.outcome(WebhookReply(400)))
        assertEquals(FeedbackOutcome.Failed(404), FeedbackRules.outcome(WebhookReply(404)))
        assertEquals(FeedbackOutcome.Failed(500), FeedbackRules.outcome(WebhookReply(500)))
        assertEquals(FeedbackOutcome.Failed(301), FeedbackRules.outcome(WebhookReply(301)))
    }

    @Test
    fun `reponse 429, delai lu dans retry_after du corps`() {
        val body = """{"message": "You are being rate limited.", "retry_after": 1.234, "global": false}"""
        assertEquals(FeedbackOutcome.RateLimited(2_000), FeedbackRules.outcome(WebhookReply(429, body, "1")))
    }

    @Test
    fun `reponse 429 sans corps lisible, delai de l'en-tete Retry-After puis 60 s`() {
        assertEquals(3_000, FeedbackRules.retryAfterMs("<html>", "3"))
        assertEquals(3_000, FeedbackRules.retryAfterMs(null, " 3 "))
        assertEquals(60_000, FeedbackRules.retryAfterMs(null, null))
        assertEquals(60_000, FeedbackRules.retryAfterMs("{}", "Wed, 21 Oct 2026 07 28 00 GMT"))
        assertEquals(60_000, FeedbackRules.retryAfterMs("[1, 2]", "-5"))
    }

    @Test
    fun `delai d'un 429 entre 1 s et 1 h`() {
        assertEquals(1_000, FeedbackRules.retryAfterMs("""{"retry_after": 0}""", null))
        assertEquals(3_600_000, FeedbackRules.retryAfterMs("""{"retry_after": 999999999}""", null))
        assertEquals(3_600_000, FeedbackRules.retryAfterMs(null, "1e300"))
    }
}
