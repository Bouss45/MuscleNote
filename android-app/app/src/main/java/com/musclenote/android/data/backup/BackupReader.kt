package com.musclenote.android.data.backup

import com.musclenote.android.data.util.newId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lecture d'un fichier de sauvegarde v1 (app Expo) ou v2 (SPEC § 5.1, § 5.3) :
 * JSON → version → validation de forme → migrations jusqu'à la version courante →
 * normalisation. Rien n'est écrit ici ; en cas d'échec, aucune donnée n'est touchée.
 *
 * [today] et [zone] : jour local et fuseau du téléphone (entrées « pris aujourd'hui »,
 * objectifs datés, dates sans fuseau) ; injectés pour les tests.
 */
class BackupReader(
    today: LocalDate,
    private val zone: ZoneId,
    newId: () -> String = ::newId,
) {
    private val ctx = MigrationContext(today, newId)

    fun read(text: String): BackupReadResult {
        val root: JsonElement = try {
            AppJson.storage.parseToJsonElement(text.removePrefix(BYTE_ORDER_MARK))
        } catch (e: SerializationException) {
            return BackupReadResult.InvalidFormat
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.InvalidFormat
        }
        if (root !is JsonObject) return BackupReadResult.InvalidFormat

        val version = when (val v = root["version"]) {
            null -> 1
            else -> if (v.isAbsentOrNull()) 1 else v.integralNumberOrNull() ?: return BackupReadResult.InvalidFormat
        }
        if (version > CURRENT_BACKUP_VERSION) return BackupReadResult.NewerVersion
        if (version < 1) return BackupReadResult.InvalidFormat

        val presentKeys = BackupKeys.ALL.filter { it in root }
        if (presentKeys.isEmpty()) return BackupReadResult.NoData
        val validator = BackupValidator(zone)
        presentKeys.firstOrNull { !validator.isValid(it, root.getValue(it)) }?.let {
            return BackupReadResult.InvalidKey(it)
        }

        val migrated = BackupMigrations.migrate(root, version, ctx)
        return BackupReadResult.Success(BackupNormalizer(ctx, zone).normalize(migrated), version)
    }

    /** `2` ou `2.0` → 2 ; `"2"`, `2.5`, `true` → `null`. */
    private fun JsonElement.integralNumberOrNull(): Int? {
        val n = numberOrNull() ?: return null
        return if (n % 1.0 == 0.0) n.roundToIntOrNull() else null
    }

    private companion object {
        const val BYTE_ORDER_MARK = "﻿"
    }
}
