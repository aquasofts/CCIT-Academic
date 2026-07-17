package edu.ccit.webvpn.feature.tieba.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import edu.ccit.webvpn.feature.tieba.FloorSort
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.SignOutcome
import edu.ccit.webvpn.feature.tieba.TiebaPreferences
import edu.ccit.webvpn.feature.tieba.TiebaReadingPreferences
import edu.ccit.webvpn.feature.tieba.TiebaSignSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.tiebaDataStore by preferencesDataStore("tieba_settings")

class TiebaSettingsRepository(private val context: Context) {
    private val store = context.tiebaDataStore

    val preferences: Flow<TiebaPreferences> = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { values ->
            TiebaPreferences(
                reading = TiebaReadingPreferences(
                    forumSort = values[ForumSortKey].enumOr(ForumSort.BY_REPLY),
                    floorSort = values[FloorSortKey].enumOr(FloorSort.ASCENDING),
                    onlyOriginalPoster = values[OnlyOpKey] ?: false,
                    showBothNames = values[ShowBothNamesKey] ?: false,
                    stickyFloorHeader = values[StickyHeaderKey] ?: false,
                ),
                sign = TiebaSignSettings(
                    enabled = values[SignEnabledKey] ?: false,
                    lastRunAt = values[LastSignAtKey],
                    lastOutcome = values[LastSignOutcomeKey]?.enumOrNull(),
                    lastMessage = values[LastSignMessageKey],
                ),
            )
        }

    suspend fun setForumSort(value: ForumSort) = store.edit { it[ForumSortKey] = value.name }
    suspend fun setFloorSort(value: FloorSort) = store.edit { it[FloorSortKey] = value.name }
    suspend fun setOnlyOriginalPoster(value: Boolean) = store.edit { it[OnlyOpKey] = value }
    suspend fun setShowBothNames(value: Boolean) = store.edit { it[ShowBothNamesKey] = value }
    suspend fun setStickyFloorHeader(value: Boolean) = store.edit { it[StickyHeaderKey] = value }
    suspend fun setSignEnabled(value: Boolean) = store.edit { it[SignEnabledKey] = value }

    suspend fun recordSign(outcome: SignOutcome, message: String, timestamp: Long = System.currentTimeMillis()) {
        store.edit {
            it[LastSignAtKey] = timestamp
            it[LastSignOutcomeKey] = outcome.name
            it[LastSignMessageKey] = message
        }
    }

    suspend fun disableSign() = store.edit { it[SignEnabledKey] = false }

    suspend fun clientConfig(baiduId: String? = null): TiebaClientConfig {
        val values = store.data.first()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val uuid = values[ClientUuidKey]?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
        val activeTimestamp = values[ClientActiveTimestampKey] ?: System.currentTimeMillis()
        val firstInstallTime = values[ClientFirstInstallTimeKey] ?: packageInfo.firstInstallTime
        val lastUpdateTime = values[ClientLastUpdateTimeKey] ?: packageInfo.lastUpdateTime
        val resolvedBaiduId = baiduId?.takeIf(String::isNotBlank) ?: values[ClientBaiduIdKey]
        if (
            values[ClientUuidKey] != uuid ||
            values[ClientActiveTimestampKey] == null ||
            values[ClientFirstInstallTimeKey] == null ||
            values[ClientLastUpdateTimeKey] == null ||
            resolvedBaiduId != values[ClientBaiduIdKey]
        ) {
            store.edit {
                it[ClientUuidKey] = uuid
                it[ClientActiveTimestampKey] = activeTimestamp
                it[ClientFirstInstallTimeKey] = firstInstallTime
                it[ClientLastUpdateTimeKey] = lastUpdateTime
                resolvedBaiduId?.let { value -> it[ClientBaiduIdKey] = value }
            }
        }
        return TiebaClientConfig(
            uuid = uuid,
            clientId = values[ClientIdKey],
            sampleId = values[ClientSampleIdKey],
            baiduId = resolvedBaiduId,
            activeTimestamp = activeTimestamp,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
        )
    }

    suspend fun updateClientSync(clientId: String, sampleId: String) {
        store.edit {
            it[ClientIdKey] = clientId
            it[ClientSampleIdKey] = sampleId
        }
    }

    suspend fun refreshClientActiveTimestamp(timestamp: Long = System.currentTimeMillis()) {
        store.edit { it[ClientActiveTimestampKey] = timestamp }
    }

    private companion object {
        val ForumSortKey = stringPreferencesKey("forum_sort")
        val FloorSortKey = stringPreferencesKey("floor_sort")
        val OnlyOpKey = booleanPreferencesKey("only_original_poster")
        val ShowBothNamesKey = booleanPreferencesKey("show_both_names")
        val StickyHeaderKey = booleanPreferencesKey("sticky_floor_header")
        val SignEnabledKey = booleanPreferencesKey("auto_sign_enabled")
        val LastSignAtKey = longPreferencesKey("last_sign_at")
        val LastSignOutcomeKey = stringPreferencesKey("last_sign_outcome")
        val LastSignMessageKey = stringPreferencesKey("last_sign_message")
        val ClientUuidKey = stringPreferencesKey("client_uuid")
        val ClientIdKey = stringPreferencesKey("client_id")
        val ClientSampleIdKey = stringPreferencesKey("sample_id")
        val ClientBaiduIdKey = stringPreferencesKey("baidu_id")
        val ClientActiveTimestampKey = longPreferencesKey("active_timestamp")
        val ClientFirstInstallTimeKey = longPreferencesKey("client_first_install_time")
        val ClientLastUpdateTimeKey = longPreferencesKey("client_last_update_time")
    }
}

data class TiebaClientConfig(
    val uuid: String,
    val clientId: String?,
    val sampleId: String?,
    val baiduId: String?,
    val activeTimestamp: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }
