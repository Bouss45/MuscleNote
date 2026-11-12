package com.musclenote.releasenotes

/** Un commit lu dans la sortie de `git log` : empreinte, parents, titre et fichiers modifiés. */
data class Commit(
    val hash: String,
    val parents: List<String>,
    val subject: String,
    val files: List<String>,
)

/**
 * Notes de version des Releases (SPEC § 15.1, 🔄 D27), en fonctions pures testables sur la JVM :
 * commits depuis la Release précédente, sans les fusions ni ceux qui ne touchent que la
 * documentation ou la CI.
 */
object ReleaseNotes {
    /** Au-delà, une dernière ligne « … et N autres changements ». */
    const val MAX_LINES = 50

    /** Séparateur de commits (`%x1e`) et de champs (`%x1f`) du format passé à `git log`. */
    private const val RECORD_SEPARATOR = '\u001e'
    private const val FIELD_SEPARATOR = '\u001f'

    /**
     * Format attendu : `git -c core.quotePath=false log --reverse --name-only
     * --format=%x1e%H%x1f%P%x1f%s <tag>..<commit>` — pour chaque commit, une ligne
     * « empreinte, parents, titre », puis ses fichiers modifiés, un par ligne.
     */
    const val GIT_LOG_FORMAT = "%x1e%H%x1f%P%x1f%s"

    fun parseGitLog(text: String): List<Commit> =
        text.split(RECORD_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { record ->
                val lines = record.lines()
                val fields = lines.first().split(FIELD_SEPARATOR, limit = 3)
                Commit(
                    hash = fields[0].trim(),
                    parents = fields.getOrElse(1) { "" }.split(' ').filter { it.isNotBlank() },
                    subject = fields.getOrElse(2) { "" }.trim(),
                    files = lines.drop(1).map { unquote(it.trim()) }.filter { it.isNotEmpty() },
                )
            }

    /** Git entoure de guillemets un chemin aux caractères spéciaux. */
    private fun unquote(path: String): String =
        if (path.length >= 2 && path.startsWith('"') && path.endsWith('"')) path.substring(1, path.length - 1) else path

    /** Documentation (tout fichier `*.md`) ou CI (tout fichier sous `.github/`). */
    fun isDocOrCi(path: String): Boolean =
        path.endsWith(".md", ignoreCase = true) || path.startsWith(".github/")

    /**
     * Commit listé : pas une fusion (au plus un parent) et au moins un fichier modifié hors de la
     * documentation et de la CI (un commit sans fichier n'est donc pas listé).
     */
    fun isListed(commit: Commit): Boolean =
        commit.parents.size <= 1 && commit.files.any { !isDocOrCi(it) }

    /** Commits listés, dans l'ordre reçu (du plus ancien au plus récent). */
    fun select(commits: List<Commit>): List<Commit> = commits.filter(::isListed)

    /** Première ligne du texte : version, `versionCode` et commit construit. */
    fun header(versionName: String, versionCode: String, commit: String): String =
        "MuscleNote $versionName (versionCode $versionCode), commit ${commit.take(7)}."

    /**
     * Texte de la Release. [commits] = commits depuis la Release précédente, `null` s'il n'y en a
     * pas ou si son tag est introuvable ; [compareUrl] = page de comparaison des deux tags.
     */
    fun render(header: String, commits: List<Commit>?, compareUrl: String?): String = buildString {
        append(header).append("\n\n")
        if (commits == null) {
            append("Liste des changements indisponible.\n")
            return@buildString
        }
        val listed = select(commits)
        if (listed.isEmpty()) {
            append("Aucun changement dans l'app : nouvelle compilation.\n")
        } else {
            append("## Changements\n")
            listed.take(MAX_LINES).forEach { append("- ").append(it.subject).append('\n') }
            val more = listed.size - MAX_LINES
            if (more > 0) {
                append(if (more == 1) "- … et 1 autre changement\n" else "- … et $more autres changements\n")
            }
        }
        if (compareUrl != null) append("\nTous les commits : ").append(compareUrl).append('\n')
    }
}
