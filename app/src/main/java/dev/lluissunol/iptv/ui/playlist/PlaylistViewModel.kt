package dev.lluissunol.iptv.ui.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lluissunol.iptv.OpenTvApplication
import dev.lluissunol.iptv.data.PlaylistCache
import dev.lluissunol.iptv.data.PlaylistException
import dev.lluissunol.iptv.data.PlaylistRepository
import dev.lluissunol.iptv.data.SearchNormalizer
import dev.lluissunol.iptv.data.SettingsStore
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.GroupCount
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.buildXtreamUrl
import dev.lluissunol.iptv.data.model.looksLikeFullXtreamUrl
import dev.lluissunol.iptv.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Top-level destination: the list manager or one loaded playlist. */
enum class Screen { LISTS, PLAYLIST }

/** Validation failures of the add/edit form, mapped to text in the UI. */
enum class ListFormError {
    NAME_REQUIRED,
    URL_INVALID,
    XTREAM_INCOMPLETE,
    SERVER_IS_FULL_URL
}

/**
 * State of the add/edit-list form. [listId] is null while creating a list and
 * [returnTo] remembers which screen to restore on cancel/save.
 */
data class ListFormState(
    val listId: String? = null,
    val returnTo: Screen = Screen.LISTS,
    val name: String = "",
    val kind: PlaylistKind = PlaylistKind.URL,
    val url: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val error: ListFormError? = null
) {
    val isNew: Boolean get() = listId == null
}

data class UiState(
    val screen: Screen = Screen.LISTS,
    val lists: List<PlaylistConfig> = emptyList(),
    val activeListId: String? = null,
    val form: ListFormState? = null,
    val playlist: Playlist? = null,
    val filteredChannels: List<Channel> = emptyList(),
    val query: String = "",
    val tab: SourceType? = SourceType.LIVE,
    val searchAll: Boolean = false,
    val selectedGroup: String? = null,
    val isLoading: Boolean = false,
    val isReadingCache: Boolean = false,
    val progressBytes: Long = 0,
    val totalBytes: Long = -1,
    val parsedEntries: Int = 0,
    val lastUpdated: Long? = null,
    val error: String? = null,
    val selectedChannel: Channel? = null,
    val showFavorites: Boolean = false,
    val favoriteKeys: Set<String> = emptySet(),
    /**
     * Favorite channels, derived once by [PlaylistViewModel] whenever the
     * playlist or [favoriteKeys] change. It is a stored list on purpose: reading
     * it never filters tens of thousands of channels on the main thread.
     */
    val favorites: List<Channel> = emptyList()
) {
    val counts: Map<SourceType, Int> get() = playlist?.counts ?: emptyMap()
    val groups: List<GroupCount>
        get() = playlist?.groupsFor(if (searchAll) null else tab).orEmpty()
    val activeList: PlaylistConfig?
        get() = lists.firstOrNull { it.id == activeListId }
}

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenTvApplication
    private val settings: SettingsStore = app.settingsStore
    private val repository: PlaylistRepository = app.playlistRepository
    private val cache = PlaylistCache(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Parsed playlists kept per list id so switching back to a list is instant.
     * Owned by this ViewModel only; never shared across screens. A parsed large
     * list costs tens of MB, so only the two most recently used are retained;
     * anything older is re-read from its cache file when opened again.
     */
    private val loadedPlaylists = object : LinkedHashMap<String, Playlist>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Playlist>): Boolean =
            size > MAX_LOADED_LISTS
    }

    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            settings.favoriteKeys.collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
        viewModelScope.launch { deriveFavorites() }
        viewModelScope.launch {
            // A failed read (corrupt prefs) degrades to "no lists", never a crash.
            val loaded = runCatching {
                // Upgrades an existing single-playlist install before anything else.
                val migrated = settings.migrateLegacyList()
                if (migrated != null) {
                    runCatching { cache.migrateLegacy(migrated.id) }
                }
                val lists = settings.lists.first()
                val active = settings.activeListId.first()
                    ?.takeIf { id -> lists.any { it.id == id } }
                lists to (active ?: lists.firstOrNull()?.id)
            }.getOrNull()

            val lists = loaded?.first.orEmpty()
            _state.update {
                it.copy(
                    screen = Screen.LISTS,
                    lists = lists,
                    activeListId = loaded?.second,
                    form = if (lists.isEmpty()) ListFormState() else null
                )
            }
        }
    }

    // ---------------------------------------------------------------- lists

    /** Opens a list: in-memory copy, then cached copy, then the network. */
    fun openList(listId: String) {
        val list = _state.value.lists.firstOrNull { it.id == listId } ?: return
        val url = list.effectiveUrl
        if (url == null || !isHttpUrl(url)) {
            editList(listId, ListFormError.URL_INVALID)
            return
        }

        loadJob?.cancel()
        filterJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.PLAYLIST,
                activeListId = list.id,
                form = null,
                playlist = null,
                filteredChannels = emptyList(),
                query = "",
                selectedGroup = null,
                tab = SourceType.LIVE,
                searchAll = false,
                isLoading = false,
                isReadingCache = false,
                progressBytes = 0,
                totalBytes = -1,
                parsedEntries = 0,
                lastUpdated = list.lastLoadedAt,
                error = null
            )
        }
        viewModelScope.launch { runCatching { settings.setActiveList(list.id) } }

        val inMemory = loadedPlaylists[list.id]
        if (inMemory != null) {
            loadJob = viewModelScope.launch { applyPlaylist(list, inMemory) }
        } else {
            loadFromCache(list, url)
        }
    }

    fun backToLists() {
        loadJob?.cancel()
        _state.update { it.copy(screen = Screen.LISTS, form = null, error = null) }
    }

    fun startAddList() {
        _state.update { it.copy(screen = Screen.LISTS, form = ListFormState()) }
    }

    fun editList(listId: String, error: ListFormError? = null) {
        val list = _state.value.lists.firstOrNull { it.id == listId } ?: return
        val returnTo = _state.value.screen
        loadJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.LISTS,
                form = ListFormState(
                    listId = list.id,
                    returnTo = returnTo,
                    name = list.name,
                    kind = list.kind,
                    url = list.url.orEmpty(),
                    server = list.server.orEmpty(),
                    username = list.username.orEmpty(),
                    password = list.password.orEmpty(),
                    error = error
                )
            )
        }
    }

    fun editActiveList() {
        _state.value.activeListId?.let { editList(it) }
    }

    fun cancelForm() {
        val form = _state.value.form ?: return
        _state.update { current ->
            if (current.lists.isEmpty()) {
                // Fresh install: the form is the only screen, just drop the error.
                current.copy(form = form.copy(error = null))
            } else {
                current.copy(screen = form.returnTo, form = null)
            }
        }
    }

    fun setFormName(value: String) = updateForm { it.copy(name = value, error = null) }

    fun setFormKind(kind: PlaylistKind) = updateForm { it.copy(kind = kind, error = null) }

    fun setFormUrl(value: String) = updateForm { it.copy(url = value, error = null) }

    fun setFormServer(value: String) = updateForm { it.copy(server = value, error = null) }

    fun setFormUsername(value: String) = updateForm { it.copy(username = value, error = null) }

    fun setFormPassword(value: String) = updateForm { it.copy(password = value, error = null) }

    /** Validates and persists the form, then opens/refreshes the list. */
    fun submitForm() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        if (name.isEmpty()) {
            updateForm { it.copy(error = ListFormError.NAME_REQUIRED) }
            return
        }

        val candidate = when (form.kind) {
            PlaylistKind.URL -> {
                val url = form.url.trim()
                if (!isHttpUrl(url)) {
                    updateForm { it.copy(error = ListFormError.URL_INVALID) }
                    return
                }
                PlaylistConfig(
                    id = form.listId ?: PlaylistConfig.newId(),
                    name = name,
                    kind = PlaylistKind.URL,
                    url = url
                )
            }
            PlaylistKind.XTREAM -> {
                val server = form.server.trim()
                if (looksLikeFullXtreamUrl(server)) {
                    updateForm { it.copy(error = ListFormError.SERVER_IS_FULL_URL) }
                    return
                }
                val username = form.username.trim()
                if (server.isEmpty() || username.isEmpty() || form.password.isEmpty() ||
                    buildXtreamUrl(server, username, form.password) == null
                ) {
                    updateForm { it.copy(error = ListFormError.XTREAM_INCOMPLETE) }
                    return
                }
                PlaylistConfig(
                    id = form.listId ?: PlaylistConfig.newId(),
                    name = name,
                    kind = PlaylistKind.XTREAM,
                    server = server,
                    username = username,
                    password = form.password
                )
            }
        }

        viewModelScope.launch {
            val previous = _state.value.lists.firstOrNull { it.id == candidate.id }
            val urlChanged = previous == null || previous.effectiveUrl != candidate.effectiveUrl
            // Only new lists, or an edited list that was open and changed its URL,
            // should become the active list; a mere rename must not switch to it.
            // [form.returnTo] is where the form was opened from, which is what
            // decides whether a save must reopen the list: `_state.screen` is
            // always LISTS while the form is on screen. A list edited from the
            // playlist header (returnTo == PLAYLIST) that changed its URL is
            // reloaded instead of leaving the old channels and error on screen.
            val willOpen = previous == null ||
                (urlChanged && form.returnTo == Screen.PLAYLIST)
            var saved = candidate
            if (previous != null) {
                saved = if (urlChanged) {
                    // The old cache belongs to another URL; drop it.
                    loadedPlaylists.remove(saved.id)
                    runCatching { cache.delete(saved.id) }
                    saved.copy(lastLoadedAt = null)
                } else {
                    saved.copy(lastLoadedAt = previous.lastLoadedAt)
                }
            }

            runCatching { settings.upsertList(saved) }
            if (willOpen) {
                runCatching { settings.setActiveList(saved.id) }
            }
            val lists = runCatching { settings.lists.first() }.getOrDefault(_state.value.lists)
            _state.update {
                it.copy(
                    lists = lists,
                    activeListId = if (willOpen) saved.id else it.activeListId,
                    form = null,
                    screen = form.returnTo,
                    // A reload is about to start; never revive the previous error.
                    error = if (willOpen) null else it.error
                )
            }

            if (willOpen) {
                openList(saved.id)
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            loadedPlaylists.remove(listId)
            runCatching { cache.delete(listId) }
            runCatching { settings.deleteList(listId) }
            val lists = runCatching { settings.lists.first() }.getOrDefault(emptyList())
            val wasActive = _state.value.activeListId == listId
            if (wasActive) loadJob?.cancel()
            _state.update { current ->
                current.copy(
                    lists = lists,
                    activeListId = if (wasActive) lists.firstOrNull()?.id else current.activeListId,
                    screen = if (wasActive) Screen.LISTS else current.screen,
                    form = if (lists.isEmpty()) ListFormState() else null,
                    playlist = if (wasActive) null else current.playlist,
                    filteredChannels = if (wasActive) emptyList() else current.filteredChannels,
                    selectedChannel = null,
                    error = null
                )
            }
        }
    }

    // ----------------------------------------------------------- one list

    /**
     * Re-reads the active list from the network. The current list stays visible
     * and is only replaced on success, so a failed refresh never destroys it.
     */
    fun refresh() {
        val list = _state.value.activeList ?: return
        val url = list.effectiveUrl ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(list, url) }
    }

    fun retry() = refresh()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        scheduleFilter(debounce = true)
    }

    fun setTab(tab: SourceType?) {
        _state.update { it.copy(tab = tab, selectedGroup = null) }
        scheduleFilter(debounce = false)
    }

    fun setSearchAll(enabled: Boolean) {
        _state.update { it.copy(searchAll = enabled) }
        scheduleFilter(debounce = false)
    }

    fun setGroup(group: String?) {
        _state.update { it.copy(selectedGroup = group) }
        scheduleFilter(debounce = false)
    }

    fun play(channel: Channel) {
        _state.update { it.copy(selectedChannel = channel) }
    }

    fun closePlayer() {
        _state.update { it.copy(selectedChannel = null) }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            settings.toggleFavorite(channel.favoriteKey)
        }
    }

    fun showFavorites() {
        _state.update { current ->
            val favorites = current.favorites
            val tab = when {
                favorites.isEmpty() -> current.tab
                current.tab == null -> null
                favorites.any { it.sourceType == current.tab } -> current.tab
                else -> null
            }
            current.copy(showFavorites = true, tab = tab)
        }
    }

    fun hideFavorites() {
        _state.update { it.copy(showFavorites = false) }
    }

    // ----------------------------------------------------------- internals

    /** Inputs that can change the favorite list; identity-cheap to compare. */
    private data class FavoritesInput(val playlist: Playlist?, val keys: Set<String>)

    /**
     * Keeps [UiState.favorites] in sync with the playlist and the favorite keys.
     * The filter runs off the main thread and only when one of the two inputs
     * changes, so a 40k-channel playlist costs one pass, not one per
     * recomposition. Progress ticks and search keystrokes are filtered out here.
     */
    private suspend fun deriveFavorites() {
        _state
            .map { FavoritesInput(it.playlist, it.favoriteKeys) }
            .distinctUntilChanged { old, next ->
                old.playlist === next.playlist && old.keys == next.keys
            }
            .collect { input ->
                val favorites = withContext(Dispatchers.Default) {
                    input.playlist?.channels
                        ?.filter { it.favoriteKey in input.keys }
                        .orEmpty()
                }
                _state.update { current ->
                    if (current.playlist === input.playlist && current.favoriteKeys == input.keys) {
                        current.copy(favorites = favorites)
                    } else {
                        current
                    }
                }
            }
    }

    private fun updateForm(transform: (ListFormState) -> ListFormState) {
        _state.update { current ->
            val form = current.form ?: return@update current
            current.copy(form = transform(form))
        }
    }

    private fun loadFromCache(list: PlaylistConfig, url: String) {
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isReadingCache = true, error = null) }
            var bytes = try {
                cache.read(list.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // A v1 cache move may not have finished in a previous run; retry it
            // lazily so the cached list is still available without the network.
            if (bytes == null && list.id.startsWith("legacy-")) {
                try {
                    cache.migrateLegacy(list.id)
                    bytes = cache.read(list.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore: fall through to the network fetch.
                }
            }
            if (bytes != null) {
                val playlist = try {
                    withContext(Dispatchers.Default) { repository.parseCached(bytes, url) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (playlist != null && playlist.channels.isNotEmpty()) {
                    loadedPlaylists[list.id] = playlist
                    applyPlaylist(list, playlist)
                    return@launch
                }
            }
            fetch(list, url)
        }
    }

    private suspend fun fetch(list: PlaylistConfig, url: String) {
        _state.update {
            it.copy(
                isLoading = true,
                isReadingCache = false,
                error = null,
                progressBytes = 0,
                totalBytes = -1,
                parsedEntries = 0
            )
        }
        val ctx = coroutineContext
        try {
            val loaded = repository.load(url) { bytes, total, entries ->
                // Ignore progress from a download that was cancelled by a list switch.
                if (ctx.isActive) {
                    _state.update {
                        it.copy(
                            progressBytes = bytes,
                            totalBytes = total,
                            parsedEntries = entries
                        )
                    }
                }
            }

            // Persist the download even if the user already switched away, so the
            // cache is warm when they come back (avoid re-downloading).
            val now = System.currentTimeMillis()
            runCatching { cache.write(list.id, loaded.rawBytes) }
            loadedPlaylists[list.id] = loaded.playlist
            runCatching { settings.markLoaded(list.id, now) }

            if (_state.value.activeListId != list.id) return
            applyPlaylist(list.copy(lastLoadedAt = now), loaded.playlist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaylistException) {
            if (_state.value.activeListId == list.id) {
                _state.update {
                    it.copy(isLoading = false, isReadingCache = false, error = e.userMessage())
                }
            }
        } catch (e: Exception) {
            if (_state.value.activeListId == list.id) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isReadingCache = false,
                        error = "No se pudo cargar la lista."
                    )
                }
            }
        }
    }

    private suspend fun applyPlaylist(list: PlaylistConfig, playlist: Playlist) {
        val tab = defaultTab(playlist, _state.value.tab)
        val filtered = withContext(Dispatchers.Default) {
            filter(playlist, _state.value.query, tab, null, false)
        }
        val lastUpdated = list.lastLoadedAt
        _state.update { current ->
            if (current.activeListId != list.id) {
                current
            } else {
                current.copy(
                    lists = current.lists.map {
                        if (it.id == list.id) it.copy(lastLoadedAt = lastUpdated) else it
                    },
                    playlist = playlist,
                    filteredChannels = filtered,
                    tab = tab,
                    selectedGroup = null,
                    isLoading = false,
                    isReadingCache = false,
                    progressBytes = playlist.bytes,
                    totalBytes = playlist.bytes,
                    parsedEntries = playlist.channels.size,
                    lastUpdated = lastUpdated,
                    error = null
                )
            }
        }
    }

    private fun scheduleFilter(debounce: Boolean) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounce) delay(150)
            val snapshot = _state.value
            val result = withContext(Dispatchers.Default) {
                filter(
                    snapshot.playlist,
                    snapshot.query,
                    snapshot.tab,
                    snapshot.selectedGroup,
                    snapshot.searchAll
                )
            }
            _state.update { current ->
                val unchanged = current.playlist === snapshot.playlist &&
                    current.query == snapshot.query &&
                    current.tab == snapshot.tab &&
                    current.selectedGroup == snapshot.selectedGroup &&
                    current.searchAll == snapshot.searchAll
                if (unchanged) current.copy(filteredChannels = result) else current
            }
        }
    }

    private fun filter(
        playlist: Playlist?,
        query: String,
        tab: SourceType?,
        group: String?,
        searchAll: Boolean
    ): List<Channel> {
        val source = playlist ?: return emptyList()
        val normalized = SearchNormalizer.query(query)
        return source.channels.asSequence()
            .filter { searchAll || tab == null || it.sourceType == tab }
            .filter { group == null || it.group == group }
            .filter { normalized.isEmpty() || it.searchKey.contains(normalized) }
            .toList()
    }

    /** Keeps the current tab when it has entries, otherwise picks the first non-empty one. */
    private fun defaultTab(playlist: Playlist, current: SourceType?): SourceType? = when {
        current != null && playlist.count(current) > 0 -> current
        playlist.count(SourceType.LIVE) > 0 -> SourceType.LIVE
        playlist.count(SourceType.MOVIE) > 0 -> SourceType.MOVIE
        playlist.count(SourceType.SERIES) > 0 -> SourceType.SERIES
        else -> null
    }

    private fun isHttpUrl(value: String): Boolean {
        val trimmed = value.trim()
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        return hasScheme && trimmed.substringAfter("://").substringBefore('/').isNotBlank()
    }

    override fun onCleared() {
        loadJob?.cancel()
        filterJob?.cancel()
        loadedPlaylists.clear()
        super.onCleared()
    }

    private companion object {
        /** Parsed lists kept in RAM (the active one plus the previously used one). */
        const val MAX_LOADED_LISTS = 2
    }
}
