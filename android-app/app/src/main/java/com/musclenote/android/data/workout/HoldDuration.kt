package com.musclenote.android.data.workout

/**
 * Durée d'une série au temps (SPEC § 15.18.3, 🔄 D44), en secondes entières, et sa saisie au pavé
 * numérique : au plus 4 chiffres lus **depuis la droite** comme sur un minuteur (les deux derniers
 * sont les secondes), des secondes au-delà de 59 comptées comme des secondes, valeur bornée à 99:59.
 */
object HoldDuration {
    /** 99:59. */
    const val MAX_SECONDS = 5_999

    /** Chiffres au plus dans le champ. */
    const val MAX_DIGITS = 4

    /** Pas des touches − / + du pavé. */
    const val STEP_SECONDS = 5

    /** Nouvelle série sans série précédente : 0:30 (§ 15.18.2). */
    const val DEFAULT_SECONDS = 30

    /** Suggestion quand la durée a été tenue (§ 15.18.2). */
    const val SUGGESTION_SECONDS = 5

    /** Chiffres saisis → secondes : minutes × 60 + secondes, bornées à [MAX_SECONDS] ; vide → 0. */
    fun secondsOf(digits: String): Int {
        val clean = digits.filter { it.isDigit() }.takeLast(MAX_DIGITS)
        if (clean.isEmpty()) return 0
        val seconds = clean.takeLast(2).toInt()
        val minutes = clean.dropLast(2).ifEmpty { "0" }.toInt()
        return (minutes * 60 + seconds).coerceAtMost(MAX_SECONDS)
    }

    /** Chiffres d'un champ rouvert : la valeur relue sans « : » ni zéro en tête (90 → « 130 », 0 → « 0 »). */
    fun digitsOf(seconds: Int): String = format(seconds.coerceIn(0, MAX_SECONDS)).replace(":", "").trimStart('0').ifEmpty { "0" }

    /** Chiffres pendant la saisie, tels quels en `m:ss` : « 90 » → `0:90`, « 130 » → `1:30`, vide → `0:00`. */
    fun display(digits: String): String {
        val padded = digits.filter { it.isDigit() }.padStart(3, '0')
        val minutes = padded.dropLast(2).trimStart('0').ifEmpty { "0" }
        return "$minutes:${padded.takeLast(2)}"
    }

    /** Valeur relue en `m:ss`, minutes non bornées (`1:30`, `100:00`) ; une valeur négative vaut 0. */
    fun format(seconds: Int): String {
        val value = seconds.coerceAtLeast(0)
        return "${value / 60}:${(value % 60).toString().padStart(2, '0')}"
    }

    /** Temps sous tension (§ 15.18.7) : `m:ss`, `h:mm:ss` à partir d'une heure. */
    fun formatLong(seconds: Int): String {
        val value = seconds.coerceAtLeast(0)
        if (value < 3_600) return format(value)
        val minutes = (value % 3_600) / 60
        return "${value / 3_600}:${minutes.toString().padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}"
    }

    /** Pas de ±[STEP_SECONDS] ([direction] = +1 ou −1), jamais sous 0 ni au-delà de [MAX_SECONDS]. */
    fun step(seconds: Int, direction: Int): Int = (seconds + direction * STEP_SECONDS).coerceIn(0, MAX_SECONDS)

    /** Borne d'une durée écrite dans une série. */
    fun clamp(seconds: Int): Int = seconds.coerceIn(0, MAX_SECONDS)
}
