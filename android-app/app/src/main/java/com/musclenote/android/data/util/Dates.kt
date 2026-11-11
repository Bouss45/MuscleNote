package com.musclenote.android.data.util

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Format d'écriture des instants : ISO 8601 UTC avec millisecondes, comme `toISOString()`. */
private val isoInstantMillis: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

private val dayKeyPattern = Regex("""\d{4}-\d{2}-\d{2}""")

fun formatInstant(instant: Instant): String = isoInstantMillis.format(instant.truncatedTo(ChronoUnit.MILLIS))

/**
 * Lecture tolérante d'un instant, dans l'esprit de `Date.parse` de l'app Expo : instant UTC
 * (`…Z`), date-heure avec décalage, date-heure locale (fuseau [zone]) ou date seule (minuit UTC).
 * `null` si la chaîne n'est pas une date.
 */
fun parseInstantOrNull(s: String, zone: ZoneId): Instant? {
    val t = s.trim()
    try {
        return Instant.parse(t)
    } catch (ignored: DateTimeException) {
    }
    try {
        return OffsetDateTime.parse(t).toInstant()
    } catch (ignored: DateTimeException) {
    }
    try {
        return LocalDateTime.parse(t).atZone(zone).toInstant()
    } catch (ignored: DateTimeException) {
    }
    try {
        return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant()
    } catch (ignored: DateTimeException) {
    }
    return null
}

/** Clé de jour local `"AAAA-MM-JJ"` (SPEC § 10.2 : jamais le jour UTC). */
fun dayKey(date: LocalDate): String = date.toString()

/** `true` si [s] est une clé de jour `"AAAA-MM-JJ"` valide (y compris `"0000-01-01"`). */
fun isDayKey(s: String): Boolean {
    if (!dayKeyPattern.matches(s)) return false
    return try {
        LocalDate.parse(s)
        true
    } catch (ignored: DateTimeException) {
        false
    }
}

/** Horloge et fuseau courants, injectables dans les tests. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    /** Fuseau relu à chaque appel : un changement de fuseau du téléphone est pris en compte. */
    object System : TimeSource {
        override fun now(): Instant = Instant.now()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
