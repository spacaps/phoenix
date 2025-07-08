
package fr.acinq.phoenix.android.utils.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

class TrustedAppsRepository(
    private val data: DataStore<Preferences>
) {

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        val MSG_SET = stringSetPreferencesKey("TRUSTED_APPS_MESSAGES")
        private const val MAX_ENTRIES = 20
    }

    private val safePrefs: Flow<Preferences> = data.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val messages: Flow<List<String>> = safePrefs
        .map { it[MSG_SET]?.sorted() ?: emptyList() }

    /** Append one line; keeps at most MAX_ENTRIES. */
    suspend fun addMessage(msg: String) = data.edit { prefs ->
        val s = prefs[MSG_SET]?.toMutableSet() ?: mutableSetOf()
        s += msg
        if (s.size > MAX_ENTRIES) {
            val excess = s.size - MAX_ENTRIES
            s.sorted().take(excess).forEach { s.remove(it) }
        }
        prefs[MSG_SET] = s
    }

    suspend fun clearMessages() = data.edit { it.remove(MSG_SET) }


    @Serializable
    data class GrantInfo(
        val certSha256: String,
        val perDayMax: Long,
        val grantId: String
    )

    private object G {
        fun grant(pkg: String)      = stringPreferencesKey("GRANT_$pkg")
        fun tsDay(pkg: String)      = longPreferencesKey("USAGE_DAY_TS_$pkg")
        fun usedDay(pkg: String)    = longPreferencesKey("USAGE_DAY_SAT_$pkg")
    }

    suspend fun saveGrant(pkg: String, gi: GrantInfo) = data.edit {
        it[G.grant(pkg)] = json.encodeToString(gi)
        // reset usage counters
        it.remove(G.usedDay(pkg)); it.remove(G.tsDay(pkg))
    }

    suspend fun revokeGrant(pkg: String) = data.edit {
        it.remove(G.grant(pkg))
        it.remove(G.usedDay(pkg)); it.remove(G.tsDay(pkg))
    }

    suspend fun getGrant(pkg: String): GrantInfo? =
        safePrefs.first()[G.grant(pkg)]?.let { json.decodeFromString(it) }

    val grantsFlow: Flow<Map<String, GrantInfo>> = safePrefs.map { prefs ->
        prefs.asMap().filterKeys { it.name.startsWith("GRANT_") }.mapNotNull { (k, v) ->
            val pkg = k.name.removePrefix("GRANT_")
            val info = (v as? String)?.let {
                runCatching { json.decodeFromString<GrantInfo>(it) }.getOrNull()
            }
            if (info != null) pkg to info else null
        }.toMap()
    }

}