package com.musclenote.android.data.repository

import com.musclenote.android.data.model.LocalPreferences
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.storage.JsonDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dépôt des préférences (SPEC § 3.14) : durée du minuteur et thème, sauvegardés ; et préférences
 * locales à l'appareil (carte de transfert masquée), jamais exportées ni touchées par un import.
 */
class PreferencesRepository(
    private val preferencesDoc: JsonDocument<Preferences>,
    private val localPreferencesDoc: JsonDocument<LocalPreferences>,
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
            _preferences.value = preferencesDoc.read() ?: Preferences.DEFAULT
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
     * ✕ de la carte « Tu viens de l'ancienne MuscleNote ? » (§ 2.2), mémorisé sur l'appareil.
     * Écriture attendue sous le verrou : un rechargement (après un import) ne peut pas relire le
     * fichier avant elle.
     */
    suspend fun dismissMigrateCard() {
        mutex.withLock {
            val next = _localPreferences.value.copy(migrateCardDismissed = true)
            if (next == _localPreferences.value) return
            _localPreferences.value = next
            localPreferencesDoc.saveNow(next)
        }
    }
}
