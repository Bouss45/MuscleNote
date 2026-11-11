package com.musclenote.android.update

import com.musclenote.android.data.repository.ReleaseSource
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.data.update.UpdateRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dernière Release lue sur l'API GitHub (SPEC § 3.17, 🔄 D25) : une requête `GET` anonyme en
 * HTTPS, sans aucune donnée de l'app ; l'en-tête `User-Agent` (exigé par GitHub) ne contient que
 * le nom et la version de l'app.
 */
class GitHubReleaseSource(appVersion: String) : ReleaseSource {
    private val userAgent = "MuscleNote-Android/$appVersion"

    override suspend fun latest(): LatestRelease = withContext(Dispatchers.IO) {
        val connection = URL(UpdateRules.LATEST_RELEASE_API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val body = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BODY_BYTES) throw IOException("Réponse trop volumineuse")
                }
                out.toByteArray().decodeToString()
            }
            UpdateRules.parseLatest(body) ?: throw IOException("Réponse inattendue")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}
