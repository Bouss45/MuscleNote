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

/**
 * Date-heure locale saisie par l'utilisateur (SPEC § 10.3), ou `null` si elle est invalide :
 * valeur non entière (`null`, cf. [parseIntegerOrNull]), année hors de [2000, 2100], heure hors
 * de [0, 23], minute hors de [0, 59], ou date inexistante (31/02, 31/04…). Jamais de report sur
 * le jour ou le mois suivant, contrairement au constructeur `Date` de JS. [month] va de 1 à 12.
 */
fun buildLocalDate(year: Int?, month: Int?, day: Int?, hour: Int? = 12, minute: Int? = 0): LocalDateTime? {
    if (year == null || month == null || day == null || hour == null || minute == null) return null
    if (year !in 2000..2100 || hour !in 0..23 || minute !in 0..59) return null
    return try {
        LocalDateTime.of(year, month, day, hour, minute)
    } catch (ignored: DateTimeException) {
        null
    }
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
