package fr.acinq.phoenix.android.utils.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException

class TrustedAppsRepository(private val data: DataStore<Preferences>) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        val MSG_SET = stringSetPreferencesKey("TRUSTED_APPS_MESSAGES")
        private const val MAX_ENTRIES = 20
    }

    /* read safely */
    private val safeData: Flow<Preferences> = data.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Flow of messages (oldest → newest). */
    val messages: Flow<List<String>> = safeData.map {
        (it[MSG_SET] ?: emptySet()).sorted()
    }

    /** Append one line; keeps at most MAX_ENTRIES. */
    suspend fun addMessage(msg: String) = data.edit { prefs ->
        val s = prefs[MSG_SET]?.toMutableSet() ?: mutableSetOf()
        s += msg
        if (s.size > MAX_ENTRIES) {
            val excess = s.size - MAX_ENTRIES
            s.sorted().take(excess).forEach { s.remove(it) }   // drop oldest
        }
        prefs[MSG_SET] = s
    }

    /** Wipe entire log. */
    suspend fun clear() = data.edit { it.remove(MSG_SET) }
}
