package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.WeighInReminder
import java.time.LocalDate

/**
 * Alarme du rappel de pesée (SPEC § 15.13.4), séparée du dépôt pour que sa logique reste testable sur
 * la JVM. Même mécanisme que les rappels de compléments ([SupplementReminders]) : une file sérielle,
 * chaque opération relisant le réglage le plus récent ([latest]).
 */
interface WeighInReminders {
    /**
     * Met l'alarme en accord avec le réglage : la prochaine occurrence d'un jour coché, ou aucune
     * (désactivé, aucun jour). Changement du réglage, démarrage, import, redémarrage du téléphone,
     * changement d'heure ou de fuseau.
     */
    suspend fun sync(latest: () -> WeighInReminder)

    /**
     * Sonnerie : [decide] relit les données le jour local de la sonnerie ; notification seulement pour
     * [WeighInReminderDecision.Notify], puis l'occurrence suivante (sauf rappel désactivé).
     */
    suspend fun onAlarm(latest: () -> WeighInReminder, decide: (WeighInReminder, LocalDate) -> WeighInReminderDecision)

    /** Aucune alarme (tests, et tant que l'app n'a pas fourni la sienne). */
    object None : WeighInReminders {
        override suspend fun sync(latest: () -> WeighInReminder) = Unit
        override suspend fun onAlarm(latest: () -> WeighInReminder, decide: (WeighInReminder, LocalDate) -> WeighInReminderDecision) = Unit
    }
}
