package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.i18n.asString
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.lluissunol.iptv.R
import androidx.compose.runtime.remember
import dev.lluissunol.iptv.data.GenreNormalizer
import dev.lluissunol.iptv.data.CatalogSort
import dev.lluissunol.iptv.data.VodLayout
import dev.lluissunol.iptv.data.PickerEntry
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.lluissunol.iptv.data.UNNAMED_CHANNEL
import dev.lluissunol.iptv.data.UNNAMED_SERIES
import dev.lluissunol.iptv.data.UNNAMED_TITLE
import dev.lluissunol.iptv.data.cleanDisplayName
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.ui.formatCompact
import dev.lluissunol.iptv.ui.formatNumber
import dev.lluissunol.iptv.ui.shortDate
import java.util.Locale

private val LiveColor = Color(0xFFF87171)

/** Palette shared with the poster fallbacks in `PosterGrid.kt`. */
internal val LogoPalette = listOf(
    Color(0xFF1D4ED8),
    Color(0xFF7C3AED),
    Color(0xFF0F766E),
    Color(0xFFB45309),
    Color(0xFFBE123C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistScreen(
    state: UiState,
    vodGridState: LazyGridState,
    vodListState: LazyListState,
    scrollVersions: ScrollVersions,
    actions: CatalogActions,
    onShowLists: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onTabChange: (SourceType?) -> Unit,
    onSearchAllChange: (Boolean) -> Unit,
    onGroupChange: (String?) -> Unit,
    onVodCategoryChange: (String?) -> Unit,
    onVodLayoutToggle: () -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayVod: (VodItem) -> Unit,
    onOpenSeries: (SeriesCard) -> Unit,
    onToggleFavoriteKey: (String) -> Unit,
    onShowFavorites: () -> Unit,
    onEditActiveList: () -> Unit,
    onClearError: () -> Unit
) {
    // Back closes search (clearing the query) first. Otherwise no handler is
    // enabled, so back at the playlist root leaves the app.
    BackHandler(enabled = state.searchMode) { onCloseSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            AppHeader(
                state = state,
                onShowLists = onShowLists,
                onRefresh = onRefresh,
                onShowFavorites = onShowFavorites,
                onEditActiveList = onEditActiveList,
                onOpenSearch = onOpenSearch,
                onCloseSearch = onCloseSearch,
                onQueryChange = onQueryChange,
                onSearchAllChange = onSearchAllChange
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.playlist == null) {
                ListLoadPane(
                    state = state,
                    onRetry = onRetry,
                    onEditList = onEditActiveList,
                    onSwitchList = onShowLists,
                    onClearError = onClearError
                )
            } else {
                PlaylistContent(
                    state = state,
                    vodGridState = vodGridState,
                    vodListState = vodListState,
                    scrollVersions = scrollVersions,
                    actions = actions,
                    onTabChange = onTabChange,
                    onGroupChange = onGroupChange,
                    onVodCategoryChange = onVodCategoryChange,
                    onVodLayoutToggle = onVodLayoutToggle,
                    onPlay = onPlay,
                    onPlayVod = onPlayVod,
                    onOpenSeries = onOpenSeries,
                    onToggleFavoriteKey = onToggleFavoriteKey,
                    onRefresh = onRefresh,
                    onRetry = onRetry
                )
            }
        }
    }
}

/**
 * Shown while a list is being read from the cache or downloaded, and when the
 * load failed before any channel was available (including a saved list whose
 * URL is broken). Every state is actionable: retry, or go back to the lists.
 */
@Composable
private fun ListLoadPane(
    state: UiState,
    onRetry: () -> Unit,
    onEditList: () -> Unit,
    onSwitchList: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = state.activeList?.name ?: stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.list_loading_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLoading) {
            LoadingProgress(state)
        }

        state.error?.let { message ->
            ErrorCard(error = message.asString(), onRetry = onRetry, onDismiss = onClearError)
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onEditList, enabled = state.activeList != null) {
                Text(stringResource(R.string.edit_list))
            }
            TextButton(onClick = onSwitchList) {
                Text(stringResource(R.string.switch_list))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoadingProgress(state: UiState) {
    val text = when {
        state.isReadingCache -> stringResource(R.string.loading_cache)
        state.totalBytes > 0 ->
            stringResource(
                R.string.loading_download_progress,
                formatBytes(state.progressBytes),
                formatBytes(state.totalBytes),
                formatNumber(state.parsedEntries)
            )
        else ->
            stringResource(
                R.string.loading_download_progress_unknown,
                formatBytes(state.progressBytes),
                formatNumber(state.parsedEntries)
            )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.isReadingCache && state.totalBytes > 0) {
            LinearProgressIndicator(
                progress = {
                    (state.progressBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(error, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistContent(
    state: UiState,
    vodGridState: LazyGridState,
    vodListState: LazyListState,
    scrollVersions: ScrollVersions,
    actions: CatalogActions,
    onTabChange: (SourceType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    onVodCategoryChange: (String?) -> Unit,
    onVodLayoutToggle: () -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayVod: (VodItem) -> Unit,
    onOpenSeries: (SeriesCard) -> Unit,
    onToggleFavoriteKey: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit
) {
    val playlist = state.playlist ?: return
    // One instance of sheet chrome state; it closes itself if the tab loses its filters.
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val canFilter = state.isCatalogTab && state.tabIndex?.let { it.size >= 2 && it.hasFilters } == true
    // Forget a stale "open" so the sheet cannot pop up later on its own.
    // Close the sheet when the catalogue stops supporting filters (e.g. a
    // refresh): an effect, not a write during composition.
    LaunchedEffect(canFilter) { if (!canFilter) showFilters = false }
    if (showFilters) {
        FilterSheet(state = state, onDismiss = { showFilters = false }, actions = actions)
    }

    // Group / category pickers: one instance of sheet state each. Entries (and
    // their normalized search keys) are built once per source list, never per
    // keystroke or per row.
    var showGroupSheet by rememberSaveable { mutableStateOf(false) }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    // The chip that opened a picker gets focus back when the sheet closes.
    val pickerChipFocus = remember { FocusRequester() }
    var restoreChipFocus by remember { mutableStateOf(false) }
    LaunchedEffect(restoreChipFocus) {
        if (restoreChipFocus) {
            runCatching { pickerChipFocus.requestFocus() }
            restoreChipFocus = false
        }
    }
    val groupEntries = remember(state.groups) {
        state.groups.map { PickerEntry.of(it.name, it.name, it.count) }
    }
    val categories = state.visibleVodCategories
    val categoryEntries = remember(categories) {
        categories.map { PickerEntry.of(it.id, it.name, null) }
    }
    val groupPickerAvailable = !state.isCatalogTab && groupEntries.isNotEmpty()
    val categoryPickerAvailable = state.isCatalogTab && categoryEntries.isNotEmpty()
    // A tab change or a refresh can take the data away: close instead of showing a stale sheet.
    LaunchedEffect(groupPickerAvailable) { if (!groupPickerAvailable) showGroupSheet = false }
    LaunchedEffect(categoryPickerAvailable) { if (!categoryPickerAvailable) showCategorySheet = false }
    if (showGroupSheet && groupPickerAvailable) {
        PickerSheet(
            titleRes = R.string.groups_sheet_title,
            hintRes = R.string.groups_filter_hint,
            allRes = R.string.groups_all,
            noMatchRes = R.string.groups_none_match,
            entries = groupEntries,
            allCount = state.tabCount(if (state.searchAll) null else state.tab),
            selectedId = state.selectedGroup,
            onSelect = onGroupChange,
            onDismiss = {
                showGroupSheet = false
                restoreChipFocus = true
            }
        )
    }
    if (showCategorySheet && categoryPickerAvailable) {
        PickerSheet(
            titleRes = R.string.categories_sheet_title,
            hintRes = R.string.categories_filter_hint,
            allRes = R.string.categories_all,
            noMatchRes = R.string.categories_none_match,
            entries = categoryEntries,
            allCount = null,
            selectedId = state.selectedVodCategory,
            onSelect = onVodCategoryChange,
            onDismiss = {
                showCategorySheet = false
                restoreChipFocus = true
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TabPills(state = state, onTabChange = onTabChange)

        if (state.isCatalogTab && state.activeSheetFilters > 0) {
            ActiveFilterChips(state = state, actions = actions)
        }

        ListHeader(
            state = state,
            playlist = playlist,
            onOpenFilters = { showFilters = true },
            onLayoutToggle = onVodLayoutToggle,
            actions = actions,
            categories = categories,
            onOpenCategories = { showCategorySheet = true },
            onClearCategory = { onVodCategoryChange(null) },
            showGroupChip = groupPickerAvailable,
            onOpenGroups = { showGroupSheet = true },
            onClearGroup = { onGroupChange(null) },
            chipFocus = pickerChipFocus
        )

        state.error?.let { message ->
            ErrorBanner(message = message.asString(), onRetry = onRetry)
        }

        // Degradation and cross-type-search notices: one quiet line, so the
        // flat list stays usable and the reason for it is not a mystery. When
        // the catalogue itself is missing the notice carries the remedy: the
        // flat list is a degradation, not a documented behaviour, and staying
        // silent about it reads as "the app is broken" (see `catalogNotice`).
        val notice = catalogNotice(
            kind = state.activeList?.kind ?: PlaylistKind.URL,
            catalog = state.catalog,
            catalogWarning = state.catalogWarning,
            tab = state.tab,
            searchAll = state.searchAll,
            isLoading = state.isLoading,
            unreachablePosters = state.unreachablePostersInTab,
            catalogOutdated = state.catalogOutdated,
            controlsReset = state.controlsReset,
            showMovieHint = !state.movieHintSeen
        )
        notice.text?.let { text ->
            NoticeLine(
                message = noticeText(text),
                action = when (notice.kind) {
                    CatalogNoticeKind.ACTION_REFRESH -> onRefresh
                    CatalogNoticeKind.ACTION_DISMISS -> actions.onDismissHint
                    else -> null
                },
                actionLabel = stringResource(
                    if (notice.kind == CatalogNoticeKind.ACTION_DISMISS) R.string.got_it else R.string.refresh
                ),
                actionEnabled = notice.kind == CatalogNoticeKind.ACTION_DISMISS || !state.isLoading
            )
        }

        val posterGrid = state.isCatalogTab && state.vodLayout == VodLayout.GRID
        // A new catalogue view (sort, language, genre, rating or category) scrolls
        // to the top FROM COMPOSITION, in the pass that already shows the new
        // list: a keyed lazy list keeps its first visible key in place, so a
        // scroll issued before the list lands would end in the middle of it.
        val view = state.catalogViewVersion
        when {
            state.contentIsEmpty -> EmptyState(
                state = state,
                onClearFilters = actions.onClearFilters,
                onAllCategories = { onVodCategoryChange(null) },
                onClearQuery = actions.onClearQuery,
                onAllGroups = { onGroupChange(null) }
            )

            posterGrid && state.tab == SourceType.MOVIE -> {
                if (scrollVersions.grid != view) {
                    scrollVersions.grid = view
                    vodGridState.requestScrollToItem(0)
                }
                MoviePosterGrid(
                    items = state.filteredMovies,
                    favoriteKeys = state.favoriteKeys,
                    gridState = vodGridState,
                    onPlay = onPlayVod,
                    onOpenDetails = actions.onOpenMovie,
                    onToggleFavorite = { onToggleFavoriteKey(it.favoriteKey) },
                    modifier = Modifier.weight(1f)
                )
            }

            posterGrid -> {
                if (scrollVersions.grid != view) {
                    scrollVersions.grid = view
                    vodGridState.requestScrollToItem(0)
                }
                SeriesPosterGrid(
                    items = state.filteredSeries,
                    favoriteKeys = state.favoriteKeys,
                    gridState = vodGridState,
                    onOpen = onOpenSeries,
                    onToggleFavorite = { onToggleFavoriteKey(it.favoriteKey) },
                    modifier = Modifier.weight(1f)
                )
            }

            state.isCatalogTab && state.tab == SourceType.MOVIE -> {
                if (scrollVersions.list != view) {
                    scrollVersions.list = view
                    vodListState.requestScrollToItem(0)
                }
                MovieCatalogueList(
                    genreTable = state.catalog?.movieGenres.orEmpty(),
                    showAdded = state.currentSort == CatalogSort.RECENT,
                    items = state.filteredMovies,
                    favoriteKeys = state.favoriteKeys,
                    listState = vodListState,
                    onPlay = onPlayVod,
                    onOpenDetails = actions.onOpenMovie,
                    onToggleFavorite = onToggleFavoriteKey,
                    modifier = Modifier.weight(1f)
                )
            }

            state.isCatalogTab -> {
                if (scrollVersions.list != view) {
                    scrollVersions.list = view
                    vodListState.requestScrollToItem(0)
                }
                SeriesCatalogueList(
                    genreTable = state.catalog?.seriesGenres.orEmpty(),
                    items = state.filteredSeries,
                    favoriteKeys = state.favoriteKeys,
                    listState = vodListState,
                    onOpen = onOpenSeries,
                    onToggleFavorite = onToggleFavoriteKey,
                    modifier = Modifier.weight(1f)
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = state.filteredChannels,
                    key = { it.id },
                    contentType = { it.sourceType }
                ) { channel ->
                    ChannelRow(
                        channel = channel,
                        isFavorite = channel.favoriteKey in state.favoriteKeys,
                        onClick = { onPlay(channel) },
                        onToggleFavorite = { onToggleFavoriteKey(channel.favoriteKey) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * List layout of the Movies/Series catalogue, used when the user switches the
 * poster grid to the list. The flat channel list of today is never used for a
 * catalogue tab: the API metadata carries names, posters and categories.
 */
@Composable
private fun MovieCatalogueList(
    genreTable: List<String>,
    showAdded: Boolean,
    items: List<VodItem>,
    favoriteKeys: Set<String>,
    listState: LazyListState,
    onPlay: (VodItem) -> Unit,
    onOpenDetails: (VodItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playLabel = stringResource(R.string.play)
    val detailsLabel = stringResource(R.string.movie_details)
    // The genre table is parsed once per catalogue, not once per row.
    val genreLabels = remember(genreTable) { genreTable.map { GenreNormalizer.labels(it) } }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = items,
            key = { "movie:" + it.streamId },
            contentType = { SourceType.MOVIE }
        ) { item ->
            MovieRow(
                item = item,
                isFavorite = item.favoriteKey in favoriteKeys,
                onPlay = onPlay,
                onToggleFavorite = { onToggleFavorite(it.favoriteKey) },
                onOpenDetails = onOpenDetails,
                playLabel = playLabel,
                detailsLabel = detailsLabel,
                genreLabels = genreLabels,
                showAdded = showAdded
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SeriesCatalogueList(
    genreTable: List<String>,
    items: List<SeriesCard>,
    favoriteKeys: Set<String>,
    listState: LazyListState,
    onOpen: (SeriesCard) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val genreLabels = remember(genreTable) { genreTable.map { GenreNormalizer.labels(it) } }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = items,
            key = { "series:" + it.seriesId },
            contentType = { SourceType.SERIES }
        ) { card ->
            SeriesRow(
                card = card,
                isFavorite = card.favoriteKey in favoriteKeys,
                onOpen = onOpen,
                onToggleFavorite = { onToggleFavorite(card.favoriteKey) },
                genreLabels = genreLabels
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Resolves one notice message to its text. Every sentence lives in
 * `strings.xml`; this is the only place that knows the mapping, so the notice
 * selection itself stays free of Android and testable.
 */
@Composable
private fun noticeText(text: CatalogNoticeText): String = when (text) {
    CatalogNoticeText.CatalogMissing -> stringResource(R.string.catalog_missing)
    CatalogNoticeText.CatalogUnavailable -> stringResource(R.string.catalog_unavailable)
    is CatalogNoticeText.SkippedItems ->
        stringResource(R.string.catalog_skipped_items, text.count)
    CatalogNoticeText.EmptyResponse -> stringResource(R.string.catalog_empty_response)
    CatalogNoticeText.InvalidJson -> stringResource(R.string.catalog_invalid_json)
    CatalogNoticeText.UnexpectedFormat -> stringResource(R.string.catalog_unexpected_format)
    is CatalogNoticeText.PostersUnreachable ->
        stringResource(R.string.catalog_posters_unreachable, text.count, text.total)
    CatalogNoticeText.SearchAll -> stringResource(R.string.catalog_search_all)
    CatalogNoticeText.CatalogOutdated -> stringResource(R.string.catalog_outdated)
    CatalogNoticeText.ControlsReset -> stringResource(R.string.controls_reset)
    CatalogNoticeText.MovieDetailHint -> stringResource(R.string.movie_detail_hint)
}

/**
 * One-line notice under the header (catalogue degraded, limited, or not
 * downloaded yet). The optional [action] turns it into an actionable line
 * (message + button, like the error banner) without blocking the list: the flat
 * list stays browsable and playable while the notice is on screen.
 *
 * The text is not clickable on purpose: the button is the only action target,
 * so a tap on the notice can never be confused with a tap on a channel row.
 */
@Composable
private fun NoticeLine(
    message: String,
    action: (() -> Unit)? = null,
    actionLabel: String = stringResource(R.string.refresh),
    actionEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            TextButton(onClick = action, enabled = actionEnabled) {
                Text(actionLabel)
            }
        }
    }
}

private data class TabSpec(
    val type: SourceType?,
    val label: String,
    val count: Int
)

@Composable
private fun TabPills(
    state: UiState,
    onTabChange: (SourceType?) -> Unit
) {
    val tabs = listOf(
        TabSpec(SourceType.LIVE, stringResource(R.string.tab_live), state.tabCount(SourceType.LIVE)),
        TabSpec(SourceType.MOVIE, stringResource(R.string.tab_movies), state.tabCount(SourceType.MOVIE)),
        TabSpec(SourceType.SERIES, stringResource(R.string.tab_series), state.tabCount(SourceType.SERIES)),
        TabSpec(null, stringResource(R.string.tab_all), state.tabCount(null))
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = tabs, key = { it.type?.name ?: "all" }) { tab ->
            Pill(
                label = tab.label,
                count = tab.count,
                selected = state.tab == tab.type,
                onClick = { onTabChange(tab.type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatCompact(count),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 30.dp)
                .widthIn(max = 220.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListHeader(
    state: UiState,
    playlist: Playlist,
    onOpenFilters: () -> Unit,
    onLayoutToggle: () -> Unit,
    actions: CatalogActions,
    categories: List<VodCategory>,
    onOpenCategories: () -> Unit,
    onClearCategory: () -> Unit,
    showGroupChip: Boolean,
    onOpenGroups: () -> Unit,
    onClearGroup: () -> Unit,
    chipFocus: FocusRequester
) {
    if (state.isCatalogTab) {
        ResultsToolbar(
            state = state,
            warningCount = playlist.warnings.size,
            onOpenFilters = onOpenFilters,
            onLayoutToggle = onLayoutToggle,
            actions = actions,
            categories = categories,
            onOpenCategories = onOpenCategories,
            onClearCategory = onClearCategory,
            chipFocus = chipFocus
        )
        return
    }

    // One row: the group chip on the left, the result count on the right. The
    // count is an unweighted child, so it is measured first and never truncated;
    // the chip is what shrinks.
    val displayed = formatNumber(state.displayedCount)
    val narrowed = state.selectedGroup != null || state.query.isNotBlank()
    val total = formatNumber(state.tabCount(if (state.searchAll) null else state.tab))
    val countText = stringResource(
        if (narrowed) R.string.channels_count_of else R.string.channels_count,
        *(if (narrowed) arrayOf<Any>(displayed, total) else arrayOf<Any>(displayed))
    )
    val warnings = playlist.warnings.size
    val errorColor = MaterialTheme.colorScheme.error
    val warningText = if (warnings > 0) pluralStringResource(R.plurals.warning_count, warnings, warnings) else null
    val separator = stringResource(R.string.meta_separator)
    val annotated = buildAnnotatedString {
        append(countText)
        if (warningText != null) {
            append(separator)
            withStyle(SpanStyle(color = errorColor)) { append(warningText) }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (showGroupChip) {
                val group = state.selectedGroup
                PickerChipWithClear(
                    label = group ?: stringResource(R.string.group_chip, stringResource(R.string.all_groups)),
                    selected = group != null,
                    chipDescription = stringResource(
                        R.string.group_chip_cd,
                        group ?: stringResource(R.string.all_groups)
                    ),
                    clearDescription = group?.let { stringResource(R.string.group_clear_cd, it) },
                    minWidth = 96.dp,
                    maxWidth = 240.dp,
                    onOpen = onOpenGroups,
                    onClear = onClearGroup,
                    focusRequester = chipFocus
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = annotated,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.retry),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    state: UiState,
    onClearFilters: () -> Unit,
    onAllCategories: () -> Unit,
    onClearQuery: () -> Unit,
    onAllGroups: () -> Unit
) {
    // On a catalogue tab every empty result offers the way out that peels off
    // one layer of constraint at a time: filters, then category, then query.
    val catalog = state.isCatalogTab
    val hasFilters = catalog && state.activeSheetFilters > 0
    val hasQuery = state.query.isNotBlank()
    val hasCategory = catalog && state.selectedVodCategory != null
    val message = when {
        state.playlist == null -> stringResource(R.string.empty_playlist)
        hasFilters && hasQuery -> stringResource(R.string.empty_search_filters)
        hasFilters -> stringResource(R.string.empty_filters)
        hasCategory -> stringResource(R.string.empty_category)
        catalog && hasQuery -> stringResource(R.string.empty_search_titles)
        hasQuery -> stringResource(R.string.empty_search)
        catalog -> stringResource(R.string.empty_tab_titles)
        else -> stringResource(R.string.empty_tab)
    }
    val remedy: Pair<String, () -> Unit>? = when {
        state.playlist == null -> null
        hasFilters -> stringResource(R.string.filters_clear) to onClearFilters
        hasCategory -> stringResource(R.string.empty_category_action) to onAllCategories
        !catalog && state.selectedGroup != null ->
            stringResource(R.string.empty_group_action) to onAllGroups
        hasQuery -> stringResource(R.string.clear_search) to onClearQuery
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (remedy != null) {
                TextButton(onClick = remedy.second) { Text(remedy.first) }
            }
        }
    }
}

@Composable
internal fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val displayName = cleanChannelName(channel.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(channel)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.sourceType == SourceType.LIVE) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LiveColor)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = channel.group ?: typeLabel(channel.sourceType),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                channel.qualityTag?.let {
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(it)
                }
            }
        }

        Spacer(Modifier.width(4.dp))
        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
    }
}

/**
 * Favourite star shared by channel rows, catalogue rows, grid cells and the
 * series header. [size] keeps a 40dp+ touch target while the icon stays small.
 */
@Composable
internal fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    // 24dp keeps the existing list rows pixel-identical; the poster grid asks
    // for a smaller star explicitly because its cells are narrow.
    iconSize: Dp = 24.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.remove_favorite else R.string.add_favorite
            ),
            modifier = Modifier.size(iconSize),
            tint = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ChannelLogo(channel: Channel) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentScale = ContentScale.Fit,
                loading = { LogoFallback(channel.name) },
                error = { LogoFallback(channel.name) }
            )
        } else {
            LogoFallback(channel.name)
        }
    }
}

@Composable
private fun LogoFallback(name: String) {
    val index = ((name.hashCode() % LogoPalette.size) + LogoPalette.size) % LogoPalette.size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LogoPalette[index]),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = logoInitial(name),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun QualityBadge(text: String, overImage: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (overImage) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        },
        contentColor = if (overImage) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun typeLabel(type: SourceType): String = when (type) {
    SourceType.LIVE -> stringResource(R.string.tab_live)
    SourceType.MOVIE -> stringResource(R.string.tab_movies)
    SourceType.SERIES -> stringResource(R.string.tab_series)
    SourceType.OTHER -> stringResource(R.string.other)
}

/** The stored name with the "no name" placeholders shown in the current language. */
@Composable
internal fun localizedName(name: String): String = when (name) {
    UNNAMED_CHANNEL -> stringResource(R.string.unnamed_channel)
    UNNAMED_TITLE -> stringResource(R.string.unnamed_title)
    UNNAMED_SERIES -> stringResource(R.string.series_fallback)
    else -> name
}

@Composable
internal fun cleanChannelName(name: String): String {
    val localized = localizedName(name)
    return if (localized === name) cleanDisplayName(name) else localized
}

internal fun logoInitial(name: String): String {
    if (name == UNNAMED_CHANNEL || name == UNNAMED_TITLE || name == UNNAMED_SERIES) return "?"
    val cleaned = cleanDisplayName(name)
    return cleaned.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}

@Composable
private fun formatBytes(bytes: Long): String {
    val megabytes = megabyteText(bytes)
    return if (megabytes != null) {
        stringResource(R.string.size_mb, megabytes)
    } else {
        stringResource(R.string.size_kb, formatNumber((bytes / 1000).toInt()))
    }
}

/** `12.3` for 1 MB or more (and `0` for nothing), or null when KB reads better. */
private fun megabyteText(bytes: Long): String? {
    if (bytes <= 0) return "0"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1.0) String.format(Locale.getDefault(), "%.1f", mb) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: UiState,
    onBack: () -> Unit,
    onTabChange: (SourceType?) -> Unit,
    onOpen: (FavoriteEntry) -> Unit,
    onToggleFavorite: (FavoriteEntry) -> Unit
) {
    val favorites = state.favorites
    val filtered = when (val tab = state.tab) {
        null -> favorites
        else -> favorites.filter { it.sourceType == tab }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.favorites),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(
                                R.string.favorites_count,
                                formatNumber(favorites.size)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FavoritePills(
                favorites = favorites,
                selected = state.tab,
                onTabChange = onTabChange
            )

            if (filtered.isEmpty()) {
                FavoritesEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.rowKey },
                        contentType = { it.sourceType }
                    ) { entry ->
                        // Same rows as the tabs they were starred from, so a
                        // catalogue favourite looks like the cell that starred
                        // it and can be un-starred here.
                        when (entry) {
                            is SeriesFavorite -> SeriesRow(
                                card = entry.card,
                                isFavorite = true,
                                onOpen = { onOpen(entry) },
                                onToggleFavorite = { onToggleFavorite(entry) }
                            )

                            is ChannelFavorite -> ChannelRow(
                                channel = entry.channel,
                                isFavorite = true,
                                onClick = { onOpen(entry) },
                                onToggleFavorite = { onToggleFavorite(entry) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FavoritePills(
    favorites: List<FavoriteEntry>,
    selected: SourceType?,
    onTabChange: (SourceType?) -> Unit
) {
    val tabs = listOf(
        TabSpec(
            type = SourceType.LIVE,
            label = stringResource(R.string.tab_live),
            count = favorites.count { it.sourceType == SourceType.LIVE }
        ),
        TabSpec(
            type = SourceType.MOVIE,
            label = stringResource(R.string.tab_movies),
            count = favorites.count { it.sourceType == SourceType.MOVIE }
        ),
        TabSpec(
            type = SourceType.SERIES,
            label = stringResource(R.string.tab_series),
            count = favorites.count { it.sourceType == SourceType.SERIES }
        ),
        TabSpec(
            type = null,
            label = stringResource(R.string.tab_all),
            count = favorites.size
        )
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = tabs, key = { it.type?.name ?: "all" }) { tab ->
            Pill(
                label = tab.label,
                count = tab.count,
                selected = selected == tab.type,
                onClick = { onTabChange(tab.type) }
            )
        }
    }
}

@Composable
private fun FavoritesEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
