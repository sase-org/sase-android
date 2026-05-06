package org.sase.mobile.data.helpers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileUpdateJobWire

interface UpdateJobCache {
    suspend fun read(): MobileUpdateJobWire?
    suspend fun write(job: MobileUpdateJobWire?)
    suspend fun clear()
}

private val Context.saseUpdateJobDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sase_update_job_cache",
)

class DataStoreUpdateJobCache(
    context: Context,
) : UpdateJobCache {
    private val dataStore = context.applicationContext.saseUpdateJobDataStore
    private val json = GatewayJson.format

    override suspend fun read(): MobileUpdateJobWire? {
        val prefs = dataStore.data.first()
        return prefs[Keys.Job]?.let {
            json.decodeFromString(MobileUpdateJobWire.serializer(), it)
        }
    }

    override suspend fun write(job: MobileUpdateJobWire?) {
        dataStore.edit { prefs ->
            if (job == null) {
                prefs.remove(Keys.Job)
            } else {
                prefs[Keys.Job] = json.encodeToString(MobileUpdateJobWire.serializer(), job)
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(Keys.Job) }
    }

    private object Keys {
        val Job = stringPreferencesKey("job")
    }
}

class InMemoryUpdateJobCache(
    initialJob: MobileUpdateJobWire? = null,
) : UpdateJobCache {
    private var job = initialJob

    override suspend fun read(): MobileUpdateJobWire? = job

    override suspend fun write(job: MobileUpdateJobWire?) {
        this.job = job
    }

    override suspend fun clear() {
        job = null
    }
}
