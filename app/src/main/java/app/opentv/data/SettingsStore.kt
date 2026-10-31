package app.opentv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opentv_settings")

data class CacheInfo(val url: String?, val savedAt: Long?)

class SettingsStore(private val context: Context) {
    private val playlistUrlKey = stringPreferencesKey("playlist_url")
    private val cacheUrlKey = stringPreferencesKey("cache_url")
    private val cacheSavedAtKey = longPreferencesKey("cache_saved_at")

    val playlistUrl: Flow<String?> = context.dataStore.data.map { it[playlistUrlKey] }

    val cacheInfo: Flow<CacheInfo> = context.dataStore.data.map {
        CacheInfo(it[cacheUrlKey], it[cacheSavedAtKey])
    }

    suspend fun savePlaylistUrl(url: String) {
        context.dataStore.edit { it[playlistUrlKey] = url }
    }

    suspend fun saveCacheInfo(url: String, savedAt: Long) {
        context.dataStore.edit {
            it[cacheUrlKey] = url
            it[cacheSavedAtKey] = savedAt
        }
    }
}
