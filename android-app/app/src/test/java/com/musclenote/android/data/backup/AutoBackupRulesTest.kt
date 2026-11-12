package com.musclenote.android.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Sauvegarde automatique (§ 15.2) : nom des fichiers et rotation des 14 plus récents. */
class AutoBackupRulesTest {

    private fun name(day: String) = "musclenote_backup_$day.json"

    @Test
    fun `le nom est celui de l'export manuel`() {
        assertEquals("musclenote_backup_2026-11-12.json", AutoBackupRules.fileName(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun `seuls les noms exacts avec une date valide sont des sauvegardes`() {
        assertEquals(LocalDate.of(2026, 11, 12), AutoBackupRules.dayOf("musclenote_backup_2026-11-12.json"))
        assertNull(AutoBackupRules.dayOf("musclenote_backup_2026-11-12 (1).json"))
        assertNull(AutoBackupRules.dayOf("musclenote_backup_2026-02-30.json"))
        assertNull(AutoBackupRules.dayOf("musclenote_backup_2026-11-12.json.bak"))
        assertNull(AutoBackupRules.dayOf("old_musclenote_backup_2026-11-12.json"))
        assertNull(AutoBackupRules.dayOf("musclenote_backup_26-11-12.json"))
        assertNull(AutoBackupRules.dayOf("notes.txt"))
    }

    @Test
    fun `au plus 14 sauvegardes, les plus anciennes sont supprimées`() {
        val days = (1..20).map { LocalDate.of(2026, 10, 25).plusDays(it.toLong()) }
        val names = days.shuffled(kotlin.random.Random(42)).map { AutoBackupRules.fileName(it) }
        val deleted = AutoBackupRules.filesToDelete(names)
        assertEquals(days.take(6).map { AutoBackupRules.fileName(it) }.sorted(), deleted.sorted())
    }

    @Test
    fun `14 sauvegardes ou moins, rien n'est supprimé`() {
        val names = (1..14).map { name("2026-11-%02d".format(it)) }
        assertEquals(emptyList<String>(), AutoBackupRules.filesToDelete(names))
        assertEquals(emptyList<String>(), AutoBackupRules.filesToDelete(emptyList()))
    }

    @Test
    fun `les autres fichiers ne sont jamais supprimés ni comptés`() {
        val backups = (1..15).map { name("2026-11-%02d".format(it)) }
        val others = listOf("musclenote_backup_2025-01-01 (1).json", "photo.jpg", "musclenote_backup_2025-13-01.json", "Musclenote_backup_2025-01-01.json")
        assertEquals(listOf(name("2026-11-01")), AutoBackupRules.filesToDelete(others + backups))
    }

    @Test
    fun `la date du nom décide, pas l'ordre du dossier`() {
        val names = listOf(name("2024-12-31"), name("2026-01-01"), name("2025-06-15"))
        assertEquals(listOf(name("2025-06-15"), name("2024-12-31")), AutoBackupRules.filesToDelete(names, keep = 1))
    }
}
