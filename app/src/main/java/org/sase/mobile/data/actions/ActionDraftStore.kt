package org.sase.mobile.data.actions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileActionKindWire

interface ActionDraftStore {
    suspend fun read(key: ActionDraftKey): String?
    suspend fun write(key: ActionDraftKey, text: String)
    suspend fun clear(key: ActionDraftKey)
    suspend fun clearAll()
}

@Serializable
data class ActionDraftKey(
    val notificationId: String,
    val actionKind: MobileActionKindWire,
    val choiceKey: String,
)

private val Context.saseActionDraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sase_action_drafts",
)

class DataStoreActionDraftStore(
    context: Context,
) : ActionDraftStore {
    private val dataStore = context.applicationContext.saseActionDraftDataStore
    private val json = GatewayJson.format

    override suspend fun read(key: ActionDraftKey): String? {
        val prefs = dataStore.data.first()
        return draftsFrom(prefs)[key.storageKey()]
    }

    override suspend fun write(key: ActionDraftKey, text: String) {
        dataStore.edit { prefs ->
            val drafts = draftsFrom(prefs).toMutableMap()
            drafts[key.storageKey()] = text
            prefs[Keys.Drafts] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), drafts)
        }
    }

    override suspend fun clear(key: ActionDraftKey) {
        dataStore.edit { prefs ->
            val drafts = draftsFrom(prefs).toMutableMap()
            drafts.remove(key.storageKey())
            if (drafts.isEmpty()) {
                prefs.remove(Keys.Drafts)
            } else {
                prefs[Keys.Drafts] = json.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    drafts,
                )
            }
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.remove(Keys.Drafts) }
    }

    private fun draftsFrom(prefs: Preferences): Map<String, String> {
        return prefs[Keys.Drafts]?.let {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
        } ?: emptyMap()
    }

    private object Keys {
        val Drafts = stringPreferencesKey("drafts")
    }
}

class InMemoryActionDraftStore : ActionDraftStore {
    private val drafts = mutableMapOf<String, String>()

    override suspend fun read(key: ActionDraftKey): String? = drafts[key.storageKey()]

    override suspend fun write(key: ActionDraftKey, text: String) {
        drafts[key.storageKey()] = text
    }

    override suspend fun clear(key: ActionDraftKey) {
        drafts.remove(key.storageKey())
    }

    override suspend fun clearAll() {
        drafts.clear()
    }
}

private fun ActionDraftKey.storageKey(): String {
    return listOf(notificationId, actionKind.name, choiceKey)
        .joinToString("|") { it.escapeStoragePart() }
}

private fun String.escapeStoragePart(): String {
    return replace("%", "%25").replace("|", "%7C")
}
