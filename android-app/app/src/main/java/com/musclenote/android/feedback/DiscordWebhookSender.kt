package com.musclenote.android.feedback

import com.musclenote.android.data.feedback.WebhookReply
import com.musclenote.android.data.repository.FeedbackSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envoi d'un avis au webhook Discord (SPEC § 14.2, 🔄 D26) : un `POST` JSON en HTTPS, seulement
 * quand l'utilisateur appuie sur « Envoyer ». `HttpURLConnection` sur le dispatcher IO, délais
 * courts, redirections non suivies. Pour un 429, le corps et l'en-tête `Retry-After` sont rendus
 * pour calculer le délai (§ 14.3).
 */
class DiscordWebhookSender(private val webhookUrl: String, appVersion: String) : FeedbackSender {
    private val userAgent = "MuscleNote-Android/$appVersion"

    override suspend fun post(json: String): WebhookReply = withContext(Dispatchers.IO) {
        val bytes = json.encodeToByteArray()
        val connection = URL(webhookUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", userAgent)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code == HTTP_TOO_MANY_REQUESTS) {
                WebhookReply(code, connection.errorStream?.let(::readLimited), connection.getHeaderField("Retry-After"))
            } else {
                WebhookReply(code)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Début du corps de la réponse (assez pour lire `retry_after`). */
    private fun readLimited(stream: InputStream): String = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (out.size() < MAX_BODY_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toByteArray().decodeToString()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_BODY_BYTES = 16 * 1024
    }
}
