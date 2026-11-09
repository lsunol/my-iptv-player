package dev.lluissunol.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "opentv_settings")

/** Layout of the Movies/Series poster views; the grid is the feature default. */
enum class VodLayout { LIST, GRID }

/**
 * On-device settings: the configured lists, which one was used last, and the
 * global favorites. Everything else (channels, cache) lives in memory or in
 * `files/`; nothing here ever leaves the device.
 *
 * Lists are stored as a JSON array string built with `org.json` (bundled with
 * Android) so no new dependency is needed. Credentials are inside that string,
 * which is app-private and excluded from backups.
 */
class SettingsStore(private val context: Context) {
    private val listsKey = stringPreferencesKey("lists_json")
    private val activeListKey = stringPreferencesKey("active_list_id")
    private val favoriteKeysKey = stringSetPreferencesKey("favorite_keys")
    private val vodLayoutKey = stringPreferencesKey("v1_vod_layout")
    private val playerMutedKey = booleanPreferencesKey("v1_player_muted")

    // v1 stored a single playlist in these keys; kept only for migration.
    private val legacyUrlKey = stringPreferencesKey("playlist_url")
    private val legacyCacheUrlKey = stringPreferencesKey("cache_url")
    private val legacyCacheSavedAtKey = longPreferencesKey("cache_saved_at")

    val lists: Flow<List<PlaylistConfig>> = context.dataStore.data.map { prefs ->
        decodeLists(prefs[listsKey])
    }

    val activeListId: Flow<String?> = context.dataStore.data.map { it[activeListKey] }

    val favoriteKeys: Flow<Set<String>> = context.dataStore.data.map {
        it[favoriteKeysKey]?.toSet() ?: emptySet()
    }

    /** Persisted Movies/Series layout preference; unknown values fall back to GRID. */
    val vodLayout: Flow<VodLayout> = context.dataStore.data.map { prefs ->
        prefs[vodLayoutKey]
            ?.let { stored -> runCatching { VodLayout.valueOf(stored) }.getOrNull() }
            ?: VodLayout.GRID
    }

    suspend fun setVodLayout(layout: VodLayout) {
        context.dataStore.edit { it[vodLayoutKey] = layout.name }
    }

    /**
     * Persisted player mute. It survives changing channel and restarting the
     * app, so someone who watches muted to hear another app does not have to
     * mute again on every channel. No stored value means "with sound".
     */
    val playerMuted: Flow<Boolean> = context.dataStore.data.map { it[playerMutedKey] ?: false }

    suspend fun setPlayerMuted(muted: Boolean) {
        context.dataStore.edit { it[playerMutedKey] = muted }
    }

    /**
     * v1 had a single playlist in `playlist_url` with the raw cache in
     * `files/playlist.cache`. When no list has ever been saved, turn that URL
     * into one [PlaylistConfig] named "Mi lista" so the existing list, its cache
     * and its last-updated stamp keep working after the upgrade.
     *
     * Idempotent: it always writes a (possibly empty) `lists_json` marker, so a
     * user who deletes every list does not get the legacy list back.
     *
     * @return the migrated list, or null when there was nothing to migrate.
     */
    suspend fun migrateLegacyList(): PlaylistConfig? {
        val prefs = context.dataStore.data.first()
        if (prefs.contains(listsKey)) return null

        val legacyUrl = prefs[legacyUrlKey]?.trim().orEmpty()
        if (legacyUrl.isEmpty()) {
            context.dataStore.edit { it[listsKey] = encodeLists(emptyList()) }
            return null
        }

        val savedAt = prefs[legacyCacheSavedAtKey]
            ?.takeIf { prefs[legacyCacheUrlKey] == legacyUrl }
        val migrated = PlaylistConfig(
            id = "legacy-" + legacyUrl.hashCode().toUInt().toString(16),
            name = LEGACY_LIST_NAME,
            kind = PlaylistKind.URL,
            url = legacyUrl,
            lastLoadedAt = savedAt
        )
        context.dataStore.edit { prefs ->
            prefs[listsKey] = encodeLists(listOf(migrated))
            prefs[activeListKey] = migrated.id
        }
        return migrated
    }

    suspend fun upsertList(config: PlaylistConfig) {
        context.dataStore.edit { prefs ->
            val current = decodeLists(prefs[listsKey]).toMutableList()
            val index = current.indexOfFirst { it.id == config.id }
            if (index >= 0) current[index] = config else current.add(config)
            prefs[listsKey] = encodeLists(current)
            if (prefs[activeListKey] == null) prefs[activeListKey] = config.id
        }
    }

    suspend fun deleteList(id: String) {
        context.dataStore.edit { prefs ->
            val remaining = decodeLists(prefs[listsKey]).filterNot { it.id == id }
            prefs[listsKey] = encodeLists(remaining)
            if (prefs[activeListKey] == id) {
                val next = remaining.firstOrNull()?.id
                if (next == null) prefs.remove(activeListKey) else prefs[activeListKey] = next
            }
        }
    }

    suspend fun setActiveList(id: String) {
        context.dataStore.edit { it[activeListKey] = id }
    }

    /** Records a successful load for one list, without touching the others. */
    suspend fun markLoaded(id: String, at: Long) {
        context.dataStore.edit { prefs ->
            val current = decodeLists(prefs[listsKey]).toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(lastLoadedAt = at)
                prefs[listsKey] = encodeLists(current)
            }
        }
    }

    suspend fun toggleFavorite(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoriteKeysKey]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(key)) current.remove(key)
            prefs[favoriteKeysKey] = current
        }
    }

    companion object {
        const val LEGACY_LIST_NAME = "Mi lista"
    }
}

internal fun encodeLists(lists: List<PlaylistConfig>): String {
    val array = JSONArray()
    lists.forEach { list ->
        val obj = JSONObject()
        obj.put("id", list.id)
        obj.put("name", list.name)
        obj.put("kind", list.kind.name)
        obj.put("url", list.url ?: JSONObject.NULL)
        obj.put("server", list.server ?: JSONObject.NULL)
        obj.put("username", list.username ?: JSONObject.NULL)
        obj.put("password", list.password ?: JSONObject.NULL)
        obj.put("lastLoadedAt", list.lastLoadedAt ?: JSONObject.NULL)
        array.put(obj)
    }
    return array.toString()
}

internal fun decodeLists(json: String?): List<PlaylistConfig> {
    if (json.isNullOrBlank()) return emptyList()
    // Corrupt or foreign data must degrade to "no lists", never crash startup.
    return runCatching {
        val array = JSONArray(json)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val fields = obj.keys().asSequence().associateWith { key -> obj.opt(key) }
                decodeList(fields)?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * Turns the raw fields of one stored list into a [PlaylistConfig].
 *
 * Every field is read defensively: a missing or wrongly typed value falls back
 * to its default instead of throwing, because a single bad field (for example a
 * non-numeric `lastLoadedAt` from an interrupted write) used to abort the whole
 * decode and hide every list the user had. `null` is returned only when there is
 * nothing to identify the list with.
 *
 * The raw values are passed in as a map on purpose: `org.json` is not available
 * in plain JVM unit tests, so this keeps the decoding policy testable.
 */
internal fun decodeList(fields: Map<String, Any?>): PlaylistConfig? {
    val id = fields.string("id") ?: return null
    return PlaylistConfig(
        id = id,
        name = fields.string("name") ?: id,
        kind = fields.string("kind")
            ?.let { name -> runCatching { PlaylistKind.valueOf(name) }.getOrNull() }
            ?: PlaylistKind.URL,
        url = fields.string("url"),
        server = fields.string("server"),
        username = fields.string("username"),
        password = fields.string("password"),
        // Only a positive stamp counts as "loaded": anything else means the
        // list was never loaded, and never a reason to drop the list itself.
        lastLoadedAt = (fields["lastLoadedAt"] as? Number)?.toLong()?.takeIf { it > 0 }
    )
}

private fun Map<String, Any?>.string(key: String): String? =
    (this[key] as? String)?.takeIf { it.isNotEmpty() }
