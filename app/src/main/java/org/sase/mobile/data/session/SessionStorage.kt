package org.sase.mobile.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

interface HostSessionStorage {
    suspend fun read(): PairedHostSession?
    suspend fun write(session: PairedHostSession)
    suspend fun clear()
}

interface TokenVault {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String)
    suspend fun clearToken()
}

private val Context.saseSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sase_session",
)

class DataStoreHostSessionStorage(
    context: Context,
) : HostSessionStorage {
    private val dataStore = context.applicationContext.saseSessionDataStore

    override suspend fun read(): PairedHostSession? {
        val prefs = dataStore.data.first()
        val hostLabel = prefs[Keys.HostLabel] ?: return null
        val baseUrl = prefs[Keys.BaseUrl] ?: return null
        val deviceId = prefs[Keys.DeviceId] ?: return null
        val deviceDisplayName = prefs[Keys.DeviceDisplayName] ?: return null
        val pairedAt = prefs[Keys.PairedAt] ?: return null
        return PairedHostSession(
            hostLabel = hostLabel,
            baseUrl = baseUrl,
            deviceId = deviceId,
            deviceDisplayName = deviceDisplayName,
            pairedAt = pairedAt,
            lastSessionCheckedAt = prefs[Keys.LastSessionCheckedAt],
        )
    }

    override suspend fun write(session: PairedHostSession) {
        dataStore.edit { prefs ->
            prefs[Keys.HostLabel] = session.hostLabel
            prefs[Keys.BaseUrl] = session.baseUrl
            prefs[Keys.DeviceId] = session.deviceId
            prefs[Keys.DeviceDisplayName] = session.deviceDisplayName
            prefs[Keys.PairedAt] = session.pairedAt
            session.lastSessionCheckedAt?.let { prefs[Keys.LastSessionCheckedAt] = it }
                ?: prefs.remove(Keys.LastSessionCheckedAt)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.HostLabel)
            prefs.remove(Keys.BaseUrl)
            prefs.remove(Keys.DeviceId)
            prefs.remove(Keys.DeviceDisplayName)
            prefs.remove(Keys.PairedAt)
            prefs.remove(Keys.LastSessionCheckedAt)
        }
    }

    private object Keys {
        val HostLabel = stringPreferencesKey("host_label")
        val BaseUrl = stringPreferencesKey("base_url")
        val DeviceId = stringPreferencesKey("device_id")
        val DeviceDisplayName = stringPreferencesKey("device_display_name")
        val PairedAt = stringPreferencesKey("paired_at")
        val LastSessionCheckedAt = stringPreferencesKey("last_session_checked_at")
    }
}

class InMemoryHostSessionStorage(
    initialSession: PairedHostSession? = null,
) : HostSessionStorage {
    private var session = initialSession

    override suspend fun read(): PairedHostSession? = session

    override suspend fun write(session: PairedHostSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

class InMemoryTokenVault(
    initialToken: String? = null,
) : TokenVault {
    private var token = initialToken

    override suspend fun readToken(): String? = token

    override suspend fun writeToken(token: String) {
        this.token = token
    }

    override suspend fun clearToken() {
        token = null
    }
}

