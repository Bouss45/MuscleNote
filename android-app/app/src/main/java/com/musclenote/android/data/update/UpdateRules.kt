package com.musclenote.android.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Dernière Release publiée : version (`1.0.4`), page de la Release et APK à télécharger. */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?)

/**
 * `update_check.json` (SPEC § 3.17, 🔄 D25) : dernière vérification réussie et ce qu'elle a
 * trouvé ; version dont l'utilisateur a masqué l'annonce. Propre à l'appareil, hors sauvegarde.
 */
@Serializable
data class UpdateCheckState(
    /** Instant (ms) de la dernière vérification réussie ; 0 = jamais. */
    val checkedAt: Long = 0,
    val latestVersion: String? = null,
    val pageUrl: String? = null,
    val apkUrl: String? = null,
    /** Annonce de l'Accueil masquée (✕) pour cette version. */
    val dismissedVersion: String? = null,
) {
    companion object {
        val DEFAULT = UpdateCheckState()
    }
}

/**
 * Vérification des mises à jour (SPEC § 3.17, 🔄 D25), en fonctions pures testables sur la JVM :
 * comparaison des versions, échéance de la vérification, lecture de la réponse de l'API GitHub.
 */
object UpdateRules {
    /** Dépôt dont les Releases portent l'APK de l'app. */
    const val REPOSITORY = "Bouss45/MuscleNote"

    /** Dernière Release non brouillon et non pré-release. */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    /** Au plus une vérification automatique par jour. */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Après un échec (pas de réseau…), pas de nouvel essai automatique avant une heure. */
    const val RETRY_INTERVAL_MS = 60L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }

    /** Nombres d'une version : `v1.0.12` → [1, 0, 12] ; `0.1.0-dev` → [0, 1, 0]. */
    fun versionParts(version: String): List<Int> =
        Regex("""\d+""").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()

    /**
     * [candidate] est-elle plus récente que [current] ? Comparaison nombre par nombre (`1.0.10` >
     * `1.0.9`), les nombres manquants valant 0. Une version sans nombre n'est jamais plus récente.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = versionParts(candidate)
        if (a.isEmpty()) return false
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Vérification automatique due : jamais faite, faite il y a 24 h, ou horloge reculée. */
    fun isDue(checkedAt: Long, now: Long): Boolean = checkedAt == 0L || now - checkedAt >= CHECK_INTERVAL_MS || now < checkedAt

    /**
     * Mise à jour à proposer : la dernière Release vue, si elle est plus récente que [current].
     * `null` sinon.
     */
    fun available(state: UpdateCheckState, current: String): LatestRelease? {
        val version = state.latestVersion ?: return null
        val page = state.pageUrl ?: return null
        return if (isNewer(version, current)) LatestRelease(version, page, state.apkUrl) else null
    }

    /**
     * Réponse de `releases/latest` → [LatestRelease] ; `null` si elle est illisible, sans tag, ou
     * s'il s'agit d'un brouillon ou d'une pré-release. L'APK est le premier fichier `.apk`.
     */
    fun parseLatest(body: String): LatestRelease? {
        val release = try {
            json.decodeFromString(GitHubRelease.serializer(), body)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (release.draft || release.prerelease) return null
        val version = release.tagName.removePrefix("v").takeIf { versionParts(it).isNotEmpty() } ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl
        return LatestRelease(version, release.pageUrl, apk)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val pageUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
