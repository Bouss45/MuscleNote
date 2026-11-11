package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.Supplement

/**
 * Alarmes des rappels de compléments (SPEC § 7.3), séparées du dépôt pour que sa logique reste
 * testable sur la JVM. L'implémentation Android passe toutes les opérations par une **file
 * sérielle** (une à la fois) et relit la liste la plus récente ([latest]) au moment de les faire :
 * aucune alarme ni notification orpheline possible.
 */
interface SupplementReminders {
    /**
     * Met les alarmes en accord avec la liste actuelle : annule celles des rappels de [previous]
     * qui n'ont plus lieu d'être (supprimés, désactivés), puis programme chaque rappel attendu à
     * sa prochaine occurrence. `previous` vide = resynchronisation complète (démarrage,
     * redémarrage du téléphone, changement d'heure ou de fuseau).
     */
    suspend fun sync(previous: List<Supplement>, latest: () -> List<Supplement>)

    /**
     * Sonnerie du rappel [ref] : complément ou rappel disparu, ou rappels désactivés → rien (et pas
     * de reprogrammation) ; sinon notification, puis l'occurrence du lendemain.
     */
    suspend fun onAlarm(ref: ReminderRef, latest: () -> List<Supplement>)

    /** Aucune alarme (tests, et tant que l'app n'a pas fourni les siennes). */
    object None : SupplementReminders {
        override suspend fun sync(previous: List<Supplement>, latest: () -> List<Supplement>) = Unit
        override suspend fun onAlarm(ref: ReminderRef, latest: () -> List<Supplement>) = Unit
    }
}
