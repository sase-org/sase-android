package org.sase.mobile.data.notifications.foreground

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ForegroundConnectedModeStore {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}

private val Context.saseForegroundConnectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sase_foreground_connection",
)

class DataStoreForegroundConnectedModeStore(
    context: Context,
) : ForegroundConnectedModeStore {
    private val dataStore = context.applicationContext.saseForegroundConnectionDataStore

    override val enabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.Enabled] ?: false
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.Enabled] = enabled
        }
    }

    private object Keys {
        val Enabled = booleanPreferencesKey("enabled")
    }
}

class InMemoryForegroundConnectedModeStore(
    initialEnabled: Boolean = false,
) : ForegroundConnectedModeStore {
    private val mutableEnabled = kotlinx.coroutines.flow.MutableStateFlow(initialEnabled)

    override val enabled: Flow<Boolean> = mutableEnabled

    override suspend fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
    }
}
