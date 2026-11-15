package com.musclenote.android.data.repository

import com.musclenote.android.data.model.LocalPreferences
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.data.nutrition.WeighInReminderRules
import com.musclenote.android.data.nutrition.WeighInReminders
import com.musclenote.android.data.storage.JsonDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dépôt des préférences (SPEC § 3.14) : durée du minuteur, thème et rappel de pesée (§ 15.13),
 * sauvegardés ; et préférences locales à l'appareil (carte de transfert masquée), jamais exportées ni
 * touchées par un import. Chaque changement du rappel de pesée met son alarme à jour.
 */
class PreferencesRepository(
    private val preferencesDoc: JsonDocument<Preferences>,
    private val localPreferencesDoc: JsonDocument<LocalPreferences>,
    private val weighInReminders: WeighInReminders = WeighInReminders.None,
) {
    private val mutex = Mutex()

    private val _preferences = MutableStateFlow(Preferences.DEFAULT)
    val preferences: StateFlow<Preferences> = _preferences.asStateFlow()

    private val _localPreferences = MutableStateFlow(LocalPreferences.DEFAULT)
    val localPreferences: StateFlow<LocalPreferences> = _localPreferences.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** `true` une fois les préférences lues : le premier rendu attend le bon thème. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            // Le rappel de pesée est remis en forme (heure, jours) : le fichier peut venir d'un import.
            _preferences.value = (preferencesDoc.read() ?: Preferences.DEFAULT).let {
                it.copy(weighInReminder = WeighInReminderRules.normalized(it.weighInReminder))
            }
            _localPreferences.value = localPreferencesDoc.read() ?: LocalPreferences.DEFAULT
            _loaded.value = true
        }
    }

    /** Import (§ 5.4) : remplace les préférences si le fichier en contient, écriture attendue. */
    suspend fun replaceFromBackup(preferences: Preferences?) {
        mutex.withLock {
            preferences?.let { preferencesDoc.saveNow(it) }
        }
    }

    /** Durée du repos entre les séries (§ 2.12, § 3.14) : entier > 0, sinon ignorée. */
    suspend fun setRestSeconds(seconds: Int) {
        if (seconds <= 0) return
        mutex.withLock {
            val next = _preferences.value.copy(restSeconds = seconds)
            if (next == _preferences.value) return
            _preferences.value = next
            preferencesDoc.save(next)
        }
    }

    /** Thème Système / Clair / Sombre (🔄 D10, § 2.12), effet immédiat. */
    suspend fun setTheme(theme: ThemeMode) {
        mutex.withLock {
            val next = _preferences.value.copy(theme = theme)
            if (next == _preferences.value) return
            _preferences.value = next
            preferencesDoc.save(next)
        }
    }

    /**
     * Rappel de pesée (§ 15.13.3) : [transform] part du réglage actuel ; l'alarme suit (prochaine
     * occurrence, ou annulée).
     */
    suspend fun updateWeighInReminder(transform: (WeighInReminder) -> WeighInReminder) {
        mutex.withLock {
            val next = _preferences.value.let { it.copy(weighInReminder = WeighInReminderRules.normalized(transform(it.weighInReminder))) }
            if (next == _preferences.value) return
            _preferences.value = next
            preferencesDoc.save(next)
        }
        weighInReminders.sync { _preferences.value.weighInReminder }
    }

    /**
     * ✕ de la carte « Tu viens de l'ancienne MuscleNote ? » (§ 2.2), mémorisé sur l'appareil.
     * Écriture attendue sous le verrou : un rechargement (après un import) ne peut pas relire le
     * fichier avant elle.
     */
    suspend fun dismissMigrateCard() = updateLocal { it.copy(migrateCardDismissed = true) }

    /**
     * Copie de la langue choisie dans Paramètres (`"fr"`, `"en"`, `null` = système), pour les
     * textes de notification d'un processus lancé par une alarme sous Android 8 – 12 (§ 9.4).
     */
    suspend fun setAppLanguage(tag: String?) = updateLocal { it.copy(appLanguage = tag) }

    private suspend fun updateLocal(transform: (LocalPreferences) -> LocalPreferences) {
        mutex.withLock {
            val next = transform(_localPreferences.value)
            if (next == _localPreferences.value) return
            _localPreferences.value = next
            localPreferencesDoc.saveNow(next)
        }
    }
}
