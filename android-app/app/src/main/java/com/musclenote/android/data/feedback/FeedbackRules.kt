package com.musclenote.android.data.feedback

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.ceil

/** Type d'avis (§ 14.1) : titre et couleur de l'embed Discord (§ 14.2), toujours en français. */
enum class FeedbackType(val embedTitle: String, val embedColor: Int) {
    Idea("💡 Idée", 0xF59E0B),
    Bug("🐞 Bug", 0xEF4444),
    Other("💬 Autre", 0x3B82F6),
}

/** Brouillon du formulaire « Ton avis », gardé en mémoire seulement (§ 14.1). */
data class FeedbackDraft(
    val type: FeedbackType = FeedbackType.Idea,
    val message: String = "",
    val name: String = "",
) {
    companion object {
        val EMPTY = FeedbackDraft()
    }
}

/**
 * Informations techniques jointes à un avis et affichées sous le formulaire (§ 14.2) : mêmes
 * valeurs que le rapport de plantage (§ 10.9), plus la langue des textes de l'app.
 */
data class FeedbackEnvironment(
    /** `1.0.42 (1042)`. */
    val appVersion: String,
    /** `15 (API 35)`. */
    val androidVersion: String,
    /** `Google Pixel 8`. */
    val device: String,
    /** `fr-FR`. */
    val language: String,
)

/**
 * `feedback.json` (§ 14.3, § 4.2) : instants (ms) du dernier envoi réussi et de la fin du délai
 * demandé par une réponse 429 ; 0 = aucun. Propre à l'appareil, hors sauvegarde.
 */
@Serializable
data class FeedbackThrottle(
    val lastSentAt: Long = 0,
    val blockedUntil: Long = 0,
) {
    companion object {
        val DEFAULT = FeedbackThrottle()
    }
}

/** Réponse HTTP du webhook : code, et pour un 429 le corps et l'en-tête `Retry-After`. */
data class WebhookReply(val code: Int, val body: String? = null, val retryAfter: String? = null)

/** Issue d'un envoi (§ 14.4). */
sealed interface FeedbackOutcome {
    data object Sent : FeedbackOutcome

    /** Pas de réseau, délai dépassé, erreur TLS… */
    data object NoConnection : FeedbackOutcome

    /** 429 : plus d'envoi avant [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long) : FeedbackOutcome

    /** Autre code HTTP (4xx, 5xx…). */
    data class Failed(val code: Int) : FeedbackOutcome
}

/**
 * Envoi d'un avis vers le webhook Discord (SPEC § 14, 🔄 D26), en fonctions pures testables sur la
 * JVM : nettoyage et validation du formulaire, corps JSON, délais entre deux envois, lecture de la
 * réponse.
 */
object FeedbackRules {
    /** Longueur maximale du message, en caractères (points de code). */
    const val MESSAGE_MAX = 1_000

    /** Longueur maximale du pseudo, en caractères (points de code). */
    const val NAME_MAX = 32

    /** Un envoi par minute au plus. */
    const val MIN_INTERVAL_MS = 60_000L

    /** Délai d'un 429 sans indication. */
    const val DEFAULT_RETRY_AFTER_MS = 60_000L

    /** Plafond du délai d'un 429. */
    const val MAX_RETRY_AFTER_MS = 60L * 60 * 1000

    /** Pseudo envoyé quand le champ est vide (Discord refuse un champ vide). */
    const val ANONYMOUS = "anonyme"

    /** Nom affiché par Discord pour le message. */
    const val USERNAME = "MuscleNote"

    /** Plafond des informations jointes (Discord limite un champ à 1 024 caractères). */
    private const val ENVIRONMENT_MAX = 100

    private val WEBHOOK_URL = Regex("""https://((canary|ptb)\.)?discord(app)?\.com/api/webhooks/\d+/[A-Za-z0-9_-]+""")
    private val LINE_BREAKS = Regex("""[\r\n\u2028\u2029]+""")
    private val json = Json { ignoreUnknownKeys = true }

    /** URL de webhook Discord valable : sans elle, la section « Ton avis » est masquée (§ 14.5). */
    fun isWebhookUrl(url: String): Boolean = WEBHOOK_URL.matches(url)

    /** Nombre de caractères (points de code Unicode : un emoji simple compte pour 1). */
    fun length(text: String): Int = text.codePointCount(0, text.length)

    /** Les [max] premiers caractères de [text], sans jamais couper un emoji en deux. */
    fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        if (length(text) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }

    /** Message envoyé : espaces de début et de fin retirés, 1 000 caractères ; `null` s'il est vide. */
    fun cleanMessage(message: String): String? = truncate(message.trim(), MESSAGE_MAX).trim().ifEmpty { null }

    /** Pseudo envoyé : sur une ligne, sans espaces autour, 32 caractères ; `null` s'il est vide. */
    fun cleanName(name: String): String? = truncate(name.replace(LINE_BREAKS, " ").trim(), NAME_MAX).trim().ifEmpty { null }

    /** Le formulaire peut être envoyé : le message contient autre chose que des espaces. */
    fun isSendable(draft: FeedbackDraft): Boolean = cleanMessage(draft.message) != null

    /**
     * Corps JSON du message Discord (§ 14.2) : un embed (type, message, pseudo, informations
     * jointes) et `allowed_mentions` vide, pour qu'aucune mention ne notifie qui que ce soit.
     * L'échappement JSON est fait par kotlinx.serialization.
     */
    fun payload(draft: FeedbackDraft, environment: FeedbackEnvironment): String {
        val message = cleanMessage(draft.message) ?: ""
        val name = cleanName(draft.name) ?: ANONYMOUS
        val body: JsonObject = buildJsonObject {
            put("username", USERNAME)
            putJsonObject("allowed_mentions") { putJsonArray("parse") {} }
            putJsonArray("embeds") {
                addJsonObject {
                    put("title", draft.type.embedTitle)
                    put("description", message)
                    put("color", draft.type.embedColor)
                    putJsonArray("fields") {
                        field("Pseudo", name)
                        field("Version", environmentValue(environment.appVersion))
                        field("Android", environmentValue(environment.androidVersion))
                        field("Appareil", environmentValue(environment.device))
                        field("Langue", environmentValue(environment.language))
                    }
                }
            }
        }
        return body.toString()
    }

    private fun JsonArrayBuilder.field(name: String, value: String) {
        addJsonObject {
            put("name", name)
            put("value", value)
            put("inline", true)
        }
    }

    private fun environmentValue(value: String): String = truncate(value.replace(LINE_BREAKS, " ").trim(), ENVIRONMENT_MAX).ifBlank { "?" }

    /** Issue d'une réponse du webhook (§ 14.4). */
    fun outcome(reply: WebhookReply): FeedbackOutcome = when (reply.code) {
        in 200..299 -> FeedbackOutcome.Sent
        429 -> FeedbackOutcome.RateLimited(retryAfterMs(reply.body, reply.retryAfter))
        else -> FeedbackOutcome.Failed(reply.code)
    }

    /**
     * Délai d'un 429 (§ 14.3), en ms : `retry_after` du corps JSON (secondes, décimales
     * possibles), à défaut l'en-tête `Retry-After` (secondes), à défaut 60 s ; arrondi à la
     * seconde supérieure, entre 1 s et 1 h.
     */
    fun retryAfterMs(body: String?, header: String?): Long {
        val seconds = body?.let(::retryAfterFromBody)
            ?: header?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            ?: return DEFAULT_RETRY_AFTER_MS
        val maxSeconds = MAX_RETRY_AFTER_MS / 1000
        val rounded = ceil(seconds.coerceAtMost(maxSeconds.toDouble())).toLong()
        return rounded.coerceIn(1, maxSeconds) * 1000
    }

    private fun retryAfterFromBody(body: String): Double? = try {
        val element = json.parseToJsonElement(body)
        (element as? JsonObject)?.get("retry_after")?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Attente avant le prochain envoi (ms), 0 s'il est permis (§ 14.3) : 60 s après un envoi
     * réussi, jusqu'à la fin du délai d'un 429. Si l'horloge recule, l'attente reste plafonnée
     * (60 s, 1 h).
     */
    fun remainingWait(throttle: FeedbackThrottle, now: Long): Long {
        val afterSend = if (throttle.lastSentAt == 0L) 0L else (throttle.lastSentAt + MIN_INTERVAL_MS - now).coerceIn(0L, MIN_INTERVAL_MS)
        val afterRateLimit = if (throttle.blockedUntil == 0L) 0L else (throttle.blockedUntil - now).coerceIn(0L, MAX_RETRY_AFTER_MS)
        return maxOf(afterSend, afterRateLimit)
    }

    /** Attente affichée, en secondes entières arrondies au-dessus (« 42 s »). */
    fun waitSeconds(waitMs: Long): Long = (waitMs.coerceAtLeast(0) + 999) / 1000
}
