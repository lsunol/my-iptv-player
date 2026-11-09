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
import dev.lluissunol.iptv.data.VodLayout
import dev.lluissunol.iptv.data.XtreamCache
import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Episode
import dev.lluissunol.iptv.data.model.GroupCount
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SeriesInfo
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.model.buildXtreamUrl
import dev.lluissunol.iptv.data.model.looksLikeFullXtreamUrl
import dev.lluissunol.iptv.data.model.toChannel
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

/** One list kept in memory: its parsed channels and (for Xtream) its catalogue. */
private data class LoadedInMemoryList(
    val playlist: Playlist,
    val catalog: VodCatalog?,
    val catalogWarning: CatalogWarning? = null
)

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
    /** Merged Xtream catalogue (movies/series/categories), null for plain M3U. */
    val catalog: VodCatalog? = null,
    /** Catalogue problem under the header: the degradation when the API failed
     *  but the M3U works, or the first decode warning (e.g. skipped catalogue
     *  items) when it loaded imperfectly. Typed; the UI owns the wording and
     *  decides whether the notice carries an action (see `catalogNotice`). */
    val catalogWarning: CatalogWarning? = null,
    val filteredChannels: List<Channel> = emptyList(),
    /** Movies of the active tab, filtered by query and provider category. */
    val filteredMovies: List<VodItem> = emptyList(),
    /** Series of the active tab, filtered by query and provider category. */
    val filteredSeries: List<SeriesCard> = emptyList(),
    /** Movies/Series layout; persisted, defaults to the poster grid. */
    val vodLayout: VodLayout = VodLayout.GRID,
    /**
     * Player muted. App-local: it silences the player's own output only, never
     * the phone's volume and never another app's audio. Persisted.
     */
    val playerMuted: Boolean = false,
    /** Selected `player_api.php` category id on the Movies/Series tabs. */
    val selectedVodCategory: String? = null,
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
     * Favorites, derived once by [PlaylistViewModel] whenever the playlist, the
     * catalogue or [favoriteKeys] change. It is a stored list on purpose:
     * reading it never filters tens of thousands of channels on the main thread.
     * It holds catalogue-only favourites too (series, unmatched movies), which
     * no channel row would otherwise show.
     */
    val favorites: List<FavoriteEntry> = emptyList(),
    /** Open series drill-down (seasons then episodes), null when closed. */
    val selectedSeries: SeriesCard? = null,
    val seriesInfo: SeriesInfo? = null,
    val seriesLoading: Boolean = false,
    val seriesError: String? = null,
    val selectedSeason: Int? = null
) {
    val counts: Map<SourceType, Int> get() = playlist?.counts ?: emptyMap()
    val groups: List<GroupCount>
        get() = playlist?.groupsFor(if (searchAll) null else tab).orEmpty()
    val activeList: PlaylistConfig?
        get() = lists.firstOrNull { it.id == activeListId }

    /**
     * True when the Xtream catalogue drives the current tab (Movies or Series
     * with a loaded catalogue and no cross-type search). Both layouts render
     * the catalogue then; the flat M3U channel list is not used.
     */
    val isCatalogTab: Boolean
        get() = catalog != null && !searchAll &&
            (tab == SourceType.MOVIE || tab == SourceType.SERIES)

    /** Provider categories of the current tab (`player_api.php`), if any. */
    val vodCategories: List<VodCategory>
        get() = when (tab) {
            SourceType.MOVIE -> catalog?.movieCategories.orEmpty()
            SourceType.SERIES -> catalog?.seriesCategories.orEmpty()
            else -> emptyList()
        }

    /** Number of items the visible content is showing (grid, catalogue or list). */
    val displayedCount: Int
        get() = when {
            isCatalogTab && tab == SourceType.MOVIE -> filteredMovies.size
            isCatalogTab -> filteredSeries.size
            else -> filteredChannels.size
        }

    val contentIsEmpty: Boolean get() = displayedCount == 0

    /**
     * Count shown by the tab pill: the catalogue size on Movies/Series when the
     * API catalogue drives the tab, the parsed M3U count otherwise. Keeping the
     * tabs honest matters: a flat "12.000 canales" next to a 900-title grid is
     * a lie the user notices immediately.
     */
    fun tabCount(type: SourceType?): Int = when {
        type == null -> playlist?.channels?.size ?: 0
        type == SourceType.MOVIE && catalog != null -> catalog.movies.size
        type == SourceType.SERIES && catalog != null -> catalog.series.size
        else -> playlist?.count(type) ?: 0
    }
}

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenTvApplication
    private val settings: SettingsStore = app.settingsStore
    private val repository: PlaylistRepository = app.playlistRepository
    private val cache = PlaylistCache(application)
    private val xtreamCache = XtreamCache(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Parsed playlists kept per list id so switching back to a list is instant.
     * Owned by this ViewModel only; never shared across screens. A parsed large
     * list costs tens of MB, so only the two most recently used are retained;
     * anything older is re-read from its cache file when opened again.
     */
    private val loadedPlaylists = object : LinkedHashMap<String, LoadedInMemoryList>(4, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LoadedInMemoryList>
        ): Boolean = size > MAX_LOADED_LISTS
    }

    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var seriesJob: Job? = null

    /**
     * Series detail of the current session, keyed by series id. The repository
     * cache already avoids the second network call; this keeps the second open
     * instant (no spinner flash) and bounds what one drill-down keeps in RAM.
     */
    private val loadedSeriesInfo = object : LinkedHashMap<Long, SeriesInfo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SeriesInfo>): Boolean =
            size > MAX_LOADED_SERIES
    }

    init {
        viewModelScope.launch {
            settings.favoriteKeys.collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
        viewModelScope.launch {
            // Persisted Movies/Series layout; a corrupt preference falls back to
            // the grid default inside SettingsStore.
            settings.vodLayout.collect { layout ->
                _state.update { it.copy(vodLayout = layout) }
            }
        }
        viewModelScope.launch {
            // Persisted mute; an unreadable preference degrades to "with sound".
            settings.playerMuted.collect { muted ->
                _state.update { it.copy(playerMuted = muted) }
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
        seriesJob?.cancel()
        loadedSeriesInfo.clear()
        _state.update {
            it.copy(
                screen = Screen.PLAYLIST,
                activeListId = list.id,
                form = null,
                playlist = null,
                catalog = null,
                catalogWarning = null,
                filteredChannels = emptyList(),
                filteredMovies = emptyList(),
                filteredSeries = emptyList(),
                selectedSeries = null,
                seriesInfo = null,
                seriesLoading = false,
                seriesError = null,
                selectedSeason = null,
                query = "",
                selectedGroup = null,
                selectedVodCategory = null,
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
            loadJob = viewModelScope.launch {
                applyPlaylist(list, inMemory.playlist, inMemory.catalog, inMemory.catalogWarning)
            }
        } else {
            loadFromCache(list, url)
        }
    }

    fun backToLists() {
        loadJob?.cancel()
        seriesJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.LISTS,
                form = null,
                error = null,
                selectedSeries = null,
                seriesInfo = null,
                seriesLoading = false,
                seriesError = null,
                selectedSeason = null
            )
        }
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
                    runCatching { xtreamCache.delete(saved.id) }
                    repository.forget(saved.id)
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
            runCatching { xtreamCache.delete(listId) }
            repository.forget(listId)
            runCatching { settings.deleteList(listId) }
            val lists = runCatching { settings.lists.first() }.getOrDefault(emptyList())
            val wasActive = _state.value.activeListId == listId
            if (wasActive) loadJob?.cancel()
            if (wasActive) seriesJob?.cancel()
            if (wasActive) loadedSeriesInfo.clear()
            _state.update { current ->
                current.copy(
                    lists = lists,
                    activeListId = if (wasActive) lists.firstOrNull()?.id else current.activeListId,
                    screen = if (wasActive) Screen.LISTS else current.screen,
                    form = if (lists.isEmpty()) ListFormState() else null,
                    playlist = if (wasActive) null else current.playlist,
                    filteredChannels = if (wasActive) emptyList() else current.filteredChannels,
                    selectedSeries = if (wasActive) null else current.selectedSeries,
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
        _state.update { it.copy(tab = tab, selectedGroup = null, selectedVodCategory = null) }
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

    /** Provider category chip of the Movies/Series tabs; null means "all". */
    fun setVodCategory(categoryId: String?) {
        _state.update { it.copy(selectedVodCategory = categoryId) }
        scheduleFilter(debounce = false)
    }

    /** Persists the Movies/Series layout so it survives a restart. */
    fun setVodLayout(layout: VodLayout) {
        _state.update { it.copy(vodLayout = layout) }
        viewModelScope.launch { runCatching { settings.setVodLayout(layout) } }
    }

    fun toggleVodLayout() {
        setVodLayout(
            if (_state.value.vodLayout == VodLayout.GRID) VodLayout.LIST else VodLayout.GRID
        )
    }

    /**
     * Silences or restores playback. Only the player's own output gain changes:
     * the phone's volume and any other app playing audio stay as they are.
     */
    fun setPlayerMuted(muted: Boolean) {
        _state.update { it.copy(playerMuted = muted) }
        viewModelScope.launch {
            // A failed write must not leave the UI claiming a state the app will
            // not have after a restart, so put the toggle back where it was.
            runCatching { settings.setPlayerMuted(muted) }
                .onFailure { _state.update { it.copy(playerMuted = !muted) } }
        }
    }

    fun togglePlayerMuted() {
        setPlayerMuted(!_state.value.playerMuted)
    }

    /** Plays one movie of the catalogue, mapped to the player's channel shape. */
    fun playVod(item: VodItem) {
        play(item.toChannel())
    }

    /**
     * Opens the series drill-down: seasons and episodes are fetched lazily on
     * the first open of each series and memoised for the session, so the second
     * open is instant and no episode list is written to disk.
     */
    fun openSeries(card: SeriesCard) {
        val list = _state.value.activeList ?: return
        val memo = synchronized(loadedSeriesInfo) { loadedSeriesInfo[card.seriesId] }
        _state.update {
            it.copy(
                selectedSeries = card,
                seriesInfo = memo,
                seriesLoading = memo == null,
                seriesError = null,
                selectedSeason = memo?.seasons?.firstOrNull()?.number
            )
        }
        if (memo == null) loadSeriesInfo(list, card.seriesId)
    }

    fun closeSeries() {
        seriesJob?.cancel()
        _state.update {
            it.copy(
                selectedSeries = null,
                seriesInfo = null,
                seriesLoading = false,
                seriesError = null,
                selectedSeason = null
            )
        }
    }

    fun retrySeries() {
        val list = _state.value.activeList ?: return
        val card = _state.value.selectedSeries ?: return
        synchronized(loadedSeriesInfo) { loadedSeriesInfo.remove(card.seriesId) }
        _state.update { it.copy(seriesLoading = true, seriesError = null) }
        loadSeriesInfo(list, card.seriesId)
    }

    fun setSeason(number: Int) {
        _state.update { it.copy(selectedSeason = number) }
    }

    fun playEpisode(episode: Episode) {
        val seriesName = _state.value.selectedSeries?.name.orEmpty()
        play(episode.toChannel(seriesName))
    }

    /** Dismisses the series drill-down error without leaving the series screen. */
    fun clearSeriesError() {
        _state.update { it.copy(seriesError = null) }
    }

    private fun loadSeriesInfo(list: PlaylistConfig, seriesId: Long) {
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            try {
                val info = repository.getSeriesInfo(list, seriesId)
                synchronized(loadedSeriesInfo) { loadedSeriesInfo[seriesId] = info }
                if (_state.value.selectedSeries?.seriesId != seriesId) return@launch
                _state.update {
                    it.copy(
                        seriesInfo = info,
                        seriesLoading = false,
                        seriesError = null,
                        selectedSeason = info.seasons.firstOrNull()?.number
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlaylistException) {
                if (_state.value.selectedSeries?.seriesId == seriesId) {
                    _state.update {
                        it.copy(seriesLoading = false, seriesError = e.userMessage())
                    }
                }
            } catch (e: Exception) {
                if (_state.value.selectedSeries?.seriesId == seriesId) {
                    _state.update {
                        it.copy(
                            seriesLoading = false,
                            seriesError = "No se pudieron cargar los episodios. " +
                                "Comprueba tu conexión e inténtalo de nuevo."
                        )
                    }
                }
            }
        }
    }

    fun play(channel: Channel) {
        _state.update { it.copy(selectedChannel = channel) }
    }

    fun closePlayer() {
        _state.update { it.copy(selectedChannel = null) }
    }

    /** Toggles a favourite by its stable key (movies, series, episodes). */
    fun toggleFavoriteKey(key: String) {
        viewModelScope.launch {
            settings.toggleFavorite(key)
        }
    }

    /**
     * Activates a favourites row: a channel or a movie plays, a series opens
     * its drill-down. The favourites screen stays open underneath, so backing
     * out of the drill-down or the player returns to it.
     */
    fun openFavorite(entry: FavoriteEntry) {
        when (entry) {
            is ChannelFavorite -> play(entry.channel)
            is SeriesFavorite -> openSeries(entry.card)
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
    private data class FavoritesInput(
        val playlist: Playlist?,
        val catalog: VodCatalog?,
        val keys: Set<String>
    )

    /**
     * Keeps [UiState.favorites] in sync with the playlist, the catalogue and the
     * favorite keys. The derivation runs off the main thread and only when one
     * of the three inputs changes, so a 40k-channel playlist costs one pass per
     * change, not one per recomposition. Progress ticks and search keystrokes
     * are filtered out here.
     */
    private suspend fun deriveFavorites() {
        _state
            .map { FavoritesInput(it.playlist, it.catalog, it.favoriteKeys) }
            .distinctUntilChanged { old, next ->
                old.playlist === next.playlist &&
                    old.catalog === next.catalog &&
                    old.keys == next.keys
            }
            .collect { input ->
                val favorites = withContext(Dispatchers.Default) {
                    buildFavorites(input.playlist, input.catalog, input.keys)
                }
                _state.update { current ->
                    if (current.playlist === input.playlist &&
                        current.catalog === input.catalog &&
                        current.favoriteKeys == input.keys
                    ) {
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
            var cachedFile = try {
                cache.read(list.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // A v1 cache move may not have finished in a previous run; retry it
            // lazily so the cached list is still available without the network.
            if (cachedFile == null && list.id.startsWith("legacy-")) {
                try {
                    cache.migrateLegacy(list.id)
                    cachedFile = cache.read(list.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore: fall through to the network fetch.
                }
            }
            if (cachedFile != null) {
                // The cache is parsed straight from the file, never read into
                // memory wholesale.
                val playlist = try {
                    withContext(Dispatchers.IO) { repository.parseCached(cachedFile, url) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (playlist != null && playlist.channels.isNotEmpty()) {
                    val catalog = if (list.kind == PlaylistKind.XTREAM) {
                        runCatching { xtreamCache.read(list.id) }.getOrNull()
                    } else {
                        null
                    }
                    loadedPlaylists[list.id] = LoadedInMemoryList(playlist, catalog)
                    applyPlaylist(list, playlist, catalog)
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
            val onProgress: (Long, Long, Int) -> Unit = { bytes, total, entries ->
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
            // The download streams into a staged cache file that the
            // repository commits itself after a fully successful parse. A load
            // cancelled by a switch or an edit deliberately does NOT commit:
            // its unique staged temp is discarded instead, so the previous
            // cache keeps working (the next open of this list re-downloads).
            // A failed load discards the temp too, so a failed Actualizar
            // keeps the working cache.
            // Plain-M3U lists keep byte-for-byte today's path; only Xtream lists
            // also fetch the player_api catalogue (degrading on failure).
            val cacheTarget = cache.stage(list.id)
            val loaded = if (list.kind == PlaylistKind.XTREAM) {
                repository.loadWithCatalog(list, onProgress, cacheTarget)
            } else {
                repository.load(url, onProgress, cacheTarget)
            }

            val now = System.currentTimeMillis()
            // The catalogue cache is only replaced on full success, mirroring
            // the M3U cache: a failed Actualizar keeps the working catalogue.
            loaded.catalog?.let { catalog ->
                runCatching { xtreamCache.write(list.id, catalog) }
            }
            loadedPlaylists[list.id] =
                LoadedInMemoryList(loaded.playlist, loaded.catalog, loaded.catalogWarning)
            runCatching { settings.markLoaded(list.id, now) }

            if (_state.value.activeListId != list.id) return
            applyPlaylist(
                list.copy(lastLoadedAt = now),
                loaded.playlist,
                loaded.catalog,
                loaded.catalogWarning
            )
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

    private suspend fun applyPlaylist(
        list: PlaylistConfig,
        playlist: Playlist,
        catalog: VodCatalog? = null,
        catalogWarning: CatalogWarning? = null
    ) {
        // The stream-id matcher is needed before any lazy get_series_info call,
        // including when the playlist was restored from the disk cache.
        if (list.kind == PlaylistKind.XTREAM) {
            runCatching { repository.registerChannels(list.id, playlist.channels) }
        }
        val tab = defaultTab(playlist, _state.value.tab)
        val result = withContext(Dispatchers.Default) {
            filter(
                playlist = playlist,
                catalog = catalog,
                query = _state.value.query,
                tab = tab,
                group = null,
                categoryId = _state.value.selectedVodCategory,
                searchAll = false
            )
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
                    catalog = catalog,
                    catalogWarning = catalogWarning,
                    filteredChannels = result.channels,
                    filteredMovies = result.movies,
                    filteredSeries = result.series,
                    tab = tab,
                    selectedGroup = null,
                    selectedVodCategory = null,
                    selectedSeries = null,
                    seriesInfo = null,
                    seriesLoading = false,
                    seriesError = null,
                    selectedSeason = null,
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
                    playlist = snapshot.playlist,
                    catalog = snapshot.catalog,
                    query = snapshot.query,
                    tab = snapshot.tab,
                    group = snapshot.selectedGroup,
                    categoryId = snapshot.selectedVodCategory,
                    searchAll = snapshot.searchAll
                )
            }
            _state.update { current ->
                val unchanged = current.playlist === snapshot.playlist &&
                    current.catalog === snapshot.catalog &&
                    current.query == snapshot.query &&
                    current.tab == snapshot.tab &&
                    current.selectedGroup == snapshot.selectedGroup &&
                    current.selectedVodCategory == snapshot.selectedVodCategory &&
                    current.searchAll == snapshot.searchAll
                if (unchanged) {
                    current.copy(
                        filteredChannels = result.channels,
                        filteredMovies = result.movies,
                        filteredSeries = result.series
                    )
                } else {
                    current
                }
            }
        }
    }

    /** What the current tab renders, in whichever layout is selected. */
    private data class FilterResult(
        val channels: List<Channel>,
        val movies: List<VodItem> = emptyList(),
        val series: List<SeriesCard> = emptyList()
    )

    /**
     * Filters the active tab. When the Xtream catalogue drives the tab (Movies
     * or Series, catalogue loaded, no cross-type search) the catalogue is the
     * item source for both layouts and the flat channel pass is skipped: it
     * would be a second full scan of tens of thousands of entries whose result
     * nothing renders. Everything else keeps today's channel filtering.
     */
    private fun filter(
        playlist: Playlist?,
        catalog: VodCatalog?,
        query: String,
        tab: SourceType?,
        group: String?,
        categoryId: String?,
        searchAll: Boolean
    ): FilterResult {
        val source = playlist ?: return FilterResult(emptyList())
        val normalized = SearchNormalizer.query(query)

        if (catalog != null && !searchAll &&
            (tab == SourceType.MOVIE || tab == SourceType.SERIES)
        ) {
            val movies = if (tab == SourceType.MOVIE) {
                catalog.movies.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .filter { normalized.isEmpty() || it.searchKey.contains(normalized) }
                    .toList()
            } else {
                emptyList()
            }
            val series = if (tab == SourceType.SERIES) {
                catalog.series.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .filter { normalized.isEmpty() || it.searchKey.contains(normalized) }
                    .toList()
            } else {
                emptyList()
            }
            return FilterResult(emptyList(), movies, series)
        }

        val channels = source.channels.asSequence()
            .filter { searchAll || tab == null || it.sourceType == tab }
            .filter { group == null || it.group == group }
            .filter { normalized.isEmpty() || it.searchKey.contains(normalized) }
            .toList()
        return FilterResult(channels)
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
        seriesJob?.cancel()
        loadedPlaylists.clear()
        loadedSeriesInfo.clear()
        super.onCleared()
    }

    private companion object {
        /** Parsed lists kept in RAM (the active one plus the previously used one). */
        const val MAX_LOADED_LISTS = 2

        /** Series detail trees kept in RAM (a drill-down is one series at a time). */
        const val MAX_LOADED_SERIES = 8
    }
}
