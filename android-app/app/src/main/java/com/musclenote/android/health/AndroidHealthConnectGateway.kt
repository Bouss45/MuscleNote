package com.musclenote.android.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.musclenote.android.data.health.HealthRecordSpec
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.repository.HealthConnectGateway
import com.musclenote.android.notifications.withAppLocale
import com.musclenote.android.ui.components.appFormattingLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Health Connect sur l'appareil (SPEC § 15.3) : disponibilité, autorisations, écriture et
 * suppression par `clientRecordId`. Le client est obtenu à chaque opération : Health Connect peut
 * être installé, mis à jour ou désactivé pendant que l'app tourne.
 */
class AndroidHealthConnectGateway(context: Context) : HealthConnectGateway {
    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(appContext)

    override suspend fun grantedTypes(): Set<HealthRecordType> =
        HealthConnectRecords.typesOf(client().permissionController.getGrantedPermissions())

    override suspend fun upsert(records: List<HealthRecordSpec>, version: Long) {
        val title = sessionTitles()
        client().insertRecords(records.map { HealthConnectRecords.toRecord(it, version, title) })
    }

    override suspend fun delete(type: HealthRecordType, clientRecordIds: List<String>) {
        client().deleteRecords(
            HealthConnectRecords.recordClass(type),
            recordIdsList = emptyList(),
            clientRecordIdsList = clientRecordIds,
        )
    }

    /**
     * Nom affiché d'une séance (§ 3.1) dans la langue de l'app au moment de l'envoi : préréglage
     * traduit, sinon nom saisi, sinon date.
     */
    private fun sessionTitles(): (HealthRecordSpec.Session) -> String {
        val localized = appContext.withAppLocale()
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(localized.resources.configuration.appFormattingLocale())
        return { session ->
            session.preset?.let { localized.getString(it.labelRes) }
                ?: session.name.trim().ifEmpty { session.start.atOffset(session.startOffset).format(dateFormat) }
        }
    }
}
