package com.musclenote.android.data.health

import kotlinx.serialization.Serializable

/**
 * `health_connect.json` (SPEC § 15.3, 🔄 D29) : option, registre de ce qui a été écrit dans
 * Health Connect et file d'attente. Propre à l'appareil, hors sauvegarde.
 */
@Serializable
data class HealthSyncState(
    val enabled: Boolean = false,
    /** Enregistrements écrits : `clientRecordId` → empreinte des champs source au moment de l'envoi. */
    val sent: Map<String, String> = emptyMap(),
    /** `clientRecordId` à écrire ou à supprimer, dans l'ordre d'arrivée. */
    val pending: List<String> = emptyList(),
    /** Dernière `clientRecordVersion` utilisée : la suivante est toujours plus grande. */
    val lastVersion: Long = 0,
    /** Instant (ms) du dernier envoi réussi. */
    val lastSyncAt: Long? = null,
    /** Erreur du dernier essai, effacée par un envoi réussi. */
    val lastError: String? = null,
) {
    companion object {
        val DEFAULT = HealthSyncState()
    }
}
