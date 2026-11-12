package com.musclenote.releasenotes

import java.io.File
import kotlin.system.exitProcess

/**
 * Écrit le texte d'une Release (SPEC § 15.1), appelé par `release.yml` :
 *
 * `--version 1.0.12 --code 1012 --commit <sha> --out notes.md [--log commits.log --compare <url>]`
 *
 * `--log` = sortie de `git log` au format [ReleaseNotes.GIT_LOG_FORMAT] depuis le tag de la
 * Release précédente ; absent = pas de Release précédente dans l'historique.
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    fun required(name: String): String = options[name] ?: usage("--$name manquant")

    val header = ReleaseNotes.header(required("version"), required("code"), required("commit"))
    val commits = options["log"]?.let { ReleaseNotes.parseGitLog(File(it).readText(Charsets.UTF_8)) }
    val text = ReleaseNotes.render(header, commits, options["compare"]?.takeIf { commits != null })
    File(required("out")).writeText(text, Charsets.UTF_8)
}

private fun parseOptions(args: Array<String>): Map<String, String> {
    if (args.size % 2 != 0) usage("arguments par paires attendus")
    return args.toList().chunked(2).associate { (name, value) ->
        if (!name.startsWith("--")) usage("option inconnue : $name")
        name.removePrefix("--") to value
    }
}

private fun usage(message: String): Nothing {
    System.err.println("release-notes : $message")
    System.err.println("usage : --version V --code C --commit SHA --out FICHIER [--log FICHIER --compare URL]")
    exitProcess(2)
}
