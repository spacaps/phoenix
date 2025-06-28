package fr.acinq.phoenix.android.utils.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException

class TrustedAppsRepository(private val data: DataStore<Preferences>) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        val MSG_SET = stringSetPreferencesKey("TRUSTED_APPS_MESSAGES")
    }

    /** Safe reader identical to other Phoenix repos. */
    private val safeData: Flow<Preferences> = data.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Flow of persisted messages (oldest → newest). */
    val messages: Flow<List<String>> = safeData.map {
        (it[MSG_SET] ?: emptySet()).sorted()
    }

    /** Append new message. */
    suspend fun addMessage(msg: String) = data.edit { prefs ->
        val s = prefs[MSG_SET]?.toMutableSet() ?: mutableSetOf()
        s += msg
        prefs[MSG_SET] = s
    }
}
