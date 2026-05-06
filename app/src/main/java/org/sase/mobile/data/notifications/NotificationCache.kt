package org.sase.mobile.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileNotificationCardWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.session.PairedHostSession

interface NotificationCache {
    suspend fun read(): NotificationCacheSnapshot
    suspend fun writeCards(cards: List<MobileNotificationCardWire>)
    suspend fun writeDetail(detail: MobileNotificationDetailResponseWire)
    suspend fun updateCardState(id: String, read: Boolean, dismissed: Boolean)
    suspend fun writeSyncState(syncState: NotificationSyncState)
    suspend fun writeSessionSummary(session: PairedHostSession?)
    suspend fun clear()
}

@Serializable
data class NotificationCacheSnapshot(
    val cards: List<MobileNotificationCardWire> = emptyList(),
    val details: Map<String, MobileNotificationDetailResponseWire> = emptyMap(),
    val syncState: NotificationSyncState = NotificationSyncState(),
    val sessionSummary: CachedSessionSummary? = null,
)

@Serializable
data class NotificationSyncState(
    val lastEventId: String? = null,
    val lastFullRefreshAt: String? = null,
)

@Serializable
data class CachedSessionSummary(
    val hostLabel: String,
    val baseUrl: String,
    val deviceId: String,
    val deviceDisplayName: String,
    val pairedAt: String,
    val lastSessionCheckedAt: String?,
) {
    companion object {
        fun from(session: PairedHostSession): CachedSessionSummary {
            return CachedSessionSummary(
                hostLabel = session.hostLabel,
                baseUrl = session.baseUrl,
                deviceId = session.deviceId,
                deviceDisplayName = session.deviceDisplayName,
                pairedAt = session.pairedAt,
                lastSessionCheckedAt = session.lastSessionCheckedAt,
            )
        }
    }
}

private val Context.saseNotificationCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sase_notification_cache",
)

class DataStoreNotificationCache(
    context: Context,
) : NotificationCache {
    private val dataStore = context.applicationContext.saseNotificationCacheDataStore
    private val json = GatewayJson.format

    override suspend fun read(): NotificationCacheSnapshot {
        val prefs = dataStore.data.first()
        return snapshotFrom(prefs)
    }

    private fun snapshotFrom(prefs: Preferences): NotificationCacheSnapshot {
        return NotificationCacheSnapshot(
            cards = prefs[Keys.Cards]?.let {
                json.decodeFromString(ListSerializer(MobileNotificationCardWire.serializer()), it)
            } ?: emptyList(),
            details = prefs[Keys.Details]?.let {
                json.decodeFromString(
                    MapSerializer(String.serializer(), MobileNotificationDetailResponseWire.serializer()),
                    it,
                )
            } ?: emptyMap(),
            syncState = prefs[Keys.SyncState]?.let {
                json.decodeFromString(NotificationSyncState.serializer(), it)
            } ?: NotificationSyncState(),
            sessionSummary = prefs[Keys.SessionSummary]?.let {
                json.decodeFromString(CachedSessionSummary.serializer(), it)
            },
        )
    }

    override suspend fun writeCards(cards: List<MobileNotificationCardWire>) {
        dataStore.edit { prefs ->
            prefs[Keys.Cards] = json.encodeToString(ListSerializer(MobileNotificationCardWire.serializer()), cards)
        }
    }

    override suspend fun writeDetail(detail: MobileNotificationDetailResponseWire) {
        dataStore.edit { prefs ->
            val existing = snapshotFrom(prefs).details.toMutableMap()
            existing[detail.notification.id] = detail
            prefs[Keys.Details] = json.encodeToString(
                MapSerializer(String.serializer(), MobileNotificationDetailResponseWire.serializer()),
                existing,
            )
        }
    }

    override suspend fun updateCardState(id: String, read: Boolean, dismissed: Boolean) {
        dataStore.edit { prefs ->
            val cards = snapshotFrom(prefs).cards.map { card ->
                if (card.id == id) {
                    card.copy(read = read, dismissed = dismissed)
                } else {
                    card
                }
            }
            prefs[Keys.Cards] = json.encodeToString(ListSerializer(MobileNotificationCardWire.serializer()), cards)
        }
    }

    override suspend fun writeSyncState(syncState: NotificationSyncState) {
        dataStore.edit { prefs ->
            prefs[Keys.SyncState] = json.encodeToString(NotificationSyncState.serializer(), syncState)
        }
    }

    override suspend fun writeSessionSummary(session: PairedHostSession?) {
        dataStore.edit { prefs ->
            if (session == null) {
                prefs.remove(Keys.SessionSummary)
            } else {
                prefs[Keys.SessionSummary] = json.encodeToString(
                    CachedSessionSummary.serializer(),
                    CachedSessionSummary.from(session),
                )
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.Cards)
            prefs.remove(Keys.Details)
            prefs.remove(Keys.SyncState)
            prefs.remove(Keys.SessionSummary)
        }
    }

    private object Keys {
        val Cards = stringPreferencesKey("cards")
        val Details = stringPreferencesKey("details")
        val SyncState = stringPreferencesKey("sync_state")
        val SessionSummary = stringPreferencesKey("session_summary")
    }
}

class InMemoryNotificationCache(
    initialSnapshot: NotificationCacheSnapshot = NotificationCacheSnapshot(),
) : NotificationCache {
    private var snapshot = initialSnapshot

    override suspend fun read(): NotificationCacheSnapshot = snapshot

    override suspend fun writeCards(cards: List<MobileNotificationCardWire>) {
        snapshot = snapshot.copy(cards = cards)
    }

    override suspend fun writeDetail(detail: MobileNotificationDetailResponseWire) {
        snapshot = snapshot.copy(details = snapshot.details + (detail.notification.id to detail))
    }

    override suspend fun updateCardState(id: String, read: Boolean, dismissed: Boolean) {
        snapshot = snapshot.copy(
            cards = snapshot.cards.map { card ->
                if (card.id == id) card.copy(read = read, dismissed = dismissed) else card
            },
        )
    }

    override suspend fun writeSyncState(syncState: NotificationSyncState) {
        snapshot = snapshot.copy(syncState = syncState)
    }

    override suspend fun writeSessionSummary(session: PairedHostSession?) {
        snapshot = snapshot.copy(sessionSummary = session?.let(CachedSessionSummary::from))
    }

    override suspend fun clear() {
        snapshot = NotificationCacheSnapshot()
    }
}
