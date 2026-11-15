package com.musclenote.android.data.update

import com.musclenote.android.data.nutrition.ReminderSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Dernière Release publiée : version (`1.0.4`, le tag sans son `v`), page de la Release, APK à
 * télécharger et première ligne de ses notes (§ 15.14.3), si elle en a une.
 */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String? = null)

/**
 * `update_check.json` (SPEC § 3.17, § 15.14.6) : dernière vérification réussie et ce qu'elle a
 * trouvé ; version ignorée ; réglage de la notification et dernière version notifiée. Propre à
 * l'appareil, hors sauvegarde.
 */
@Serializable
data class UpdateCheckState(
    /** Instant (ms) de la dernière vérification réussie ; 0 = jamais. */
    val checkedAt: Long = 0,
    val latestVersion: String? = null,
    val pageUrl: String? = null,
    val apkUrl: String? = null,
    /** Version ignorée : « Plus tard » de l'Accueil ou « Ignorer cette version » de la notification. */
    val dismissedVersion: String? = null,
    /** Réglage « Nouvelle version disponible » choisi par l'utilisateur ; `null` = le défaut (§ 15.14.1). */
    val notifyEnabled: Boolean? = null,
    /** Dernière version notifiée (une notification par version, § 15.14.4). */
    val notifiedVersion: String? = null,
) {
    companion object {
        val DEFAULT = UpdateCheckState()
    }
}

/**
 * Vérification et notification des mises à jour (SPEC § 3.17, § 15.14, 🔄 D25, D39), en fonctions
 * pures testables sur la JVM : versions et `versionCode`, réponse de l'API GitHub, décision de
 * notifier, réglage par défaut, prochaine vérification, première ligne des notes.
 */
object UpdateRules {
    /** Dépôt dont les Releases portent l'APK de l'app. */
    const val REPOSITORY = "Bouss45/MuscleNote"

    /** Dernière Release non brouillon et non pré-release. */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    /** Heure locale (murale) de la vérification quotidienne (§ 15.14.2). */
    const val CHECK_HOUR = 18

    /** Longueur maximale de la première ligne des notes, en points de code (« … » compris). */
    const val NOTES_MAX_LENGTH = 200

    /** Obtainium, versions GitHub et F-Droid (§ 3.17). */
    val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    /** Nom du Play Store, qu'Obtainium emprunte quand il installe par Shizuku (l'app n'y est pas). */
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    private val json = Json { ignoreUnknownKeys = true }

    /** Version d'une Release de `release.yml` : `1.0.<n>` (tag `v1.0.<n>`). */
    private val releaseVersion = Regex("""1\.0\.(\d+)""")

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

    /**
     * `versionCode` d'une Release publiée par `release.yml` : tag `v1.0.n` (version `1.0.n`) →
     * `1000 + n`. `null` pour une autre forme de version.
     */
    fun releaseVersionCode(version: String): Int? =
        releaseVersion.matchEntire(version)?.groupValues?.get(1)?.toIntOrNull()?.let { 1000 + it }

    /**
     * La Release [version] est-elle plus récente que l'app installée (§ 15.14.3) ? Par le
     * `versionCode` pour une version `1.0.n`, sinon nombre par nombre contre [installedName].
     */
    fun isNewerThanInstalled(version: String, installedName: String, installedCode: Int): Boolean {
        val code = releaseVersionCode(version) ?: return isNewer(version, installedName)
        return code > installedCode
    }

    /**
     * Mise à jour à proposer : la dernière Release vue, si elle est plus récente que l'app
     * installée. `null` sinon.
     */
    fun available(state: UpdateCheckState, installedName: String, installedCode: Int): LatestRelease? {
        val version = state.latestVersion ?: return null
        val page = state.pageUrl ?: return null
        return if (isNewerThanInstalled(version, installedName, installedCode)) LatestRelease(version, page, state.apkUrl) else null
    }

    /**
     * Notifier la Release [version] après une vérification quotidienne (§ 15.14.4) : réglage
     * activé, plus récente que l'app installée, pas ignorée, et plus récente que la dernière
     * notifiée (une seule fois par version ; une Release plus ancienne n'est jamais notifiée après).
     */
    fun shouldNotify(version: String, state: UpdateCheckState, installedName: String, installedCode: Int, enabled: Boolean): Boolean {
        if (!enabled) return false
        if (!isNewerThanInstalled(version, installedName, installedCode)) return false
        if (version == state.dismissedVersion) return false
        val notified = state.notifiedVersion ?: return true
        return isNewer(version, notified)
    }

    /** La notification affichée n'a plus lieu d'être : sa version n'est plus plus récente que l'app. */
    fun isNotificationStale(state: UpdateCheckState, installedName: String, installedCode: Int): Boolean {
        val notified = state.notifiedVersion ?: return false
        return !isNewerThanInstalled(notified, installedName, installedCode)
    }

    /**
     * Installée (ou mise à jour) par Obtainium (§ 3.17) : l'installateur ou l'initiateur est
     * Obtainium, ou le nom du Play Store qu'il emprunte.
     */
    fun isInstalledByObtainium(installers: List<String>): Boolean =
        installers.any { it in OBTAINIUM_PACKAGES || it == PLAY_STORE_PACKAGE }

    /** Réglage effectif « Nouvelle version disponible » : le choix enregistré, sinon le défaut (§ 15.14.1). */
    fun notifyEnabled(stored: Boolean?, installedByObtainium: Boolean): Boolean = stored ?: !installedByObtainium

    /**
     * Obtainium à ouvrir pour « Voir » (§ 15.14.4), dans l'ordre d'essai : celui qui a installé
     * l'app, puis l'autre variante. Vide si l'app n'a pas été installée par Obtainium.
     */
    fun obtainiumPackagesToOpen(installers: List<String>): List<String> {
        if (!isInstalledByObtainium(installers)) return emptyList()
        val installer = installers.filter { it in OBTAINIUM_PACKAGES }
        return (installer + OBTAINIUM_PACKAGES).distinct()
    }

    /** Prochaine vérification quotidienne : 18:00 locale strictement après [now] (heure murale). */
    fun nextCheckAt(now: Instant, zone: ZoneId): ZonedDateTime = ReminderSchedule.nextOccurrence(CHECK_HOUR, 0, now, zone)

    /**
     * Première ligne des notes (§ 15.14.3) : le premier élément de liste non vide (`- …`, `* …`),
     * sans le tiret ni les espaces autour, coupé à [NOTES_MAX_LENGTH] points de code. `null` sans
     * élément de liste.
     */
    fun notesFirstLine(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val line = body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.substring(2).trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        val length = line.codePointCount(0, line.length)
        if (length <= NOTES_MAX_LENGTH) return line
        return line.substring(0, line.offsetByCodePoints(0, NOTES_MAX_LENGTH - 1)) + "…"
    }

    /**
     * Réponse de `releases/latest` → [LatestRelease] ; `null` si elle est illisible, sans tag ni
     * page, ou s'il s'agit d'un brouillon ou d'une pré-release. L'APK est le premier fichier `.apk`.
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
        return LatestRelease(version, release.pageUrl, apk, notesFirstLine(release.body))
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val pageUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
