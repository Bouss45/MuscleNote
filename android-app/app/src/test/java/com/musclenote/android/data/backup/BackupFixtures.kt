package com.musclenote.android.data.backup

import java.time.LocalDate
import java.time.ZoneId

/** Contexte commun des tests : « aujourd'hui » = 14 mars 2026 à Paris, ids prévisibles. */
internal object BackupFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 3, 14)
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    fun reader(today: LocalDate = TODAY): BackupReader {
        var next = 0
        return BackupReader(today, ZONE) { "gen-${++next}" }
    }

    fun resource(name: String): String =
        checkNotNull(BackupFixtures::class.java.getResource("/backup/$name")) { "ressource $name absente" }.readText()

    fun success(result: BackupReadResult): BackupReadResult.Success {
        check(result is BackupReadResult.Success) { "import refusé : $result" }
        return result
    }
}
