package com.musclenote.android.data.repository

import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.storage.JsonDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dépôt des préférences : durée du minuteur et thème (SPEC § 3.14). */
class PreferencesRepository(private val preferencesDoc: JsonDocument<Preferences>) {
    private val mutex = Mutex()

    private val _preferences = MutableStateFlow(Preferences.DEFAULT)
    val preferences: StateFlow<Preferences> = _preferences.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            _preferences.value = preferencesDoc.read() ?: Preferences.DEFAULT
        }
    }

    /** Import (§ 5.4) : remplace les préférences si le fichier en contient, écriture attendue. */
    suspend fun replaceFromBackup(preferences: Preferences?) {
        mutex.withLock {
            preferences?.let { preferencesDoc.saveNow(it) }
        }
    }
}
