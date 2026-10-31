package app.opentv.ui.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentv.OpenTvApplication
import app.opentv.data.PlaylistCache
import app.opentv.data.PlaylistException
import app.opentv.data.PlaylistRepository
import app.opentv.data.SearchNormalizer
import app.opentv.data.SettingsStore
import app.opentv.data.model.Channel
import app.opentv.data.model.GroupCount
import app.opentv.data.model.Playlist
import app.opentv.data.model.SourceType
import app.opentv.data.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val urlInput: String = "",
    val playlistUrl: String? = null,
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
    val showUrlEditor: Boolean = true,
    val showFavorites: Boolean = false,
    val favoriteKeys: Set<String> = emptySet()
) {
    val counts: Map<SourceType, Int> get() = playlist?.counts ?: emptyMap()
    val groups: List<GroupCount>
        get() = playlist?.groupsFor(if (searchAll) null else tab).orEmpty()
    val favorites: List<Channel>
        get() = playlist?.channels?.filter { it.favoriteKey in favoriteKeys }.orEmpty()
}

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenTvApplication
    private val settings: SettingsStore = app.settingsStore
    private val repository: PlaylistRepository = app.playlistRepository
    private val cache = PlaylistCache(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            settings.favoriteKeys.collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
        viewModelScope.launch {
            val saved = settings.playlistUrl.first()
            if (saved.isNullOrBlank()) return@launch

            _state.update {
                it.copy(urlInput = saved, playlistUrl = saved, showUrlEditor = false)
            }

            val cacheInfo = settings.cacheInfo.first()
            val cachedBytes = if (cacheInfo.url == saved) cache.read() else null
            if (cachedBytes != null) {
                showCached(saved, cachedBytes, cacheInfo.savedAt)
            } else {
                load(saved)
            }
        }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(urlInput = value, error = null) }
    }

    /**
     * Fetches and parses a playlist. The current list is kept visible while the
     * request runs and is only replaced on success, so a failed refresh never
     * destroys the last working list.
     */
    fun load(url: String = _state.value.urlInput) {
        val candidate = url.trim()
        if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            _state.update { it.copy(error = PlaylistException.InvalidUrl().userMessage()) }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
            try {
                val loaded = repository.load(candidate) { bytes, total, entries ->
                    _state.update {
                        it.copy(
                            progressBytes = bytes,
                            totalBytes = total,
                            parsedEntries = entries
                        )
                    }
                }

                val now = System.currentTimeMillis()
                val cacheWritten = runCatching { cache.write(loaded.rawBytes) }.isSuccess
                runCatching { settings.savePlaylistUrl(candidate) }
                if (cacheWritten) {
                    runCatching { settings.saveCacheInfo(candidate, now) }
                }

                val playlist = loaded.playlist
                val currentTab = _state.value.tab
                val tab = if (currentTab != null && playlist.count(currentTab) > 0) {
                    currentTab
                } else {
                    defaultTab(playlist)
                }
                val filtered = withContext(Dispatchers.Default) {
                    filter(playlist, _state.value.query, tab, null, false)
                }
                _state.update {
                    it.copy(
                        playlist = playlist,
                        playlistUrl = candidate,
                        filteredChannels = filtered,
                        tab = tab,
                        selectedGroup = null,
                        isLoading = false,
                        isReadingCache = false,
                        showUrlEditor = false,
                        lastUpdated = now,
                        error = null,
                        progressBytes = playlist.bytes,
                        totalBytes = playlist.bytes,
                        parsedEntries = playlist.channels.size
                    )
                }
            } catch (e: PlaylistException) {
                _state.update { it.copy(isLoading = false, isReadingCache = false, error = e.userMessage()) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isReadingCache = false,
                        error = "No se pudo cargar la lista: ${e.message ?: "error desconocido"}"
                    )
                }
            }
        }
    }

    fun refresh() {
        val target = _state.value.playlistUrl ?: _state.value.urlInput
        if (target.isNotBlank()) load(target)
    }

    fun retry() {
        val target = if (_state.value.showUrlEditor) {
            _state.value.urlInput
        } else {
            _state.value.playlistUrl ?: _state.value.urlInput
        }
        load(target)
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
            val favorites = current.playlist?.channels
                ?.filter { it.favoriteKey in current.favoriteKeys }
                .orEmpty()
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

    fun showUrlEditor() {
        _state.update {
            it.copy(
                showUrlEditor = true,
                urlInput = it.playlistUrl ?: it.urlInput,
                error = null
            )
        }
    }

    fun cancelUrlEditor() {
        if (_state.value.playlist != null) {
            _state.update {
                it.copy(
                    showUrlEditor = false,
                    urlInput = it.playlistUrl ?: it.urlInput,
                    error = null
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun showCached(url: String, bytes: ByteArray, savedAt: Long?) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    isReadingCache = true,
                    error = null,
                    playlistUrl = url,
                    showUrlEditor = false
                )
            }
            try {
                val playlist = withContext(Dispatchers.Default) {
                    repository.parseCached(bytes, url)
                }
                if (playlist.channels.isEmpty()) throw PlaylistException.Empty()
                val currentTab = _state.value.tab
                val tab = if (currentTab != null && playlist.count(currentTab) > 0) {
                    currentTab
                } else {
                    defaultTab(playlist)
                }
                val filtered = withContext(Dispatchers.Default) {
                    filter(playlist, _state.value.query, tab, null, false)
                }
                _state.update {
                    it.copy(
                        playlist = playlist,
                        playlistUrl = url,
                        filteredChannels = filtered,
                        tab = tab,
                        selectedGroup = null,
                        isLoading = false,
                        isReadingCache = false,
                        showUrlEditor = false,
                        lastUpdated = savedAt,
                        error = null,
                        progressBytes = playlist.bytes,
                        totalBytes = playlist.bytes,
                        parsedEntries = playlist.channels.size
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, isReadingCache = false) }
                load(url)
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

    private fun defaultTab(playlist: Playlist): SourceType? = when {
        playlist.count(SourceType.LIVE) > 0 -> SourceType.LIVE
        playlist.count(SourceType.MOVIE) > 0 -> SourceType.MOVIE
        playlist.count(SourceType.SERIES) > 0 -> SourceType.SERIES
        else -> null
    }

    override fun onCleared() {
        loadJob?.cancel()
        filterJob?.cancel()
        super.onCleared()
    }
}
