package dev.lluissunol.iptv.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lluissunol.iptv.ui.lists.ListsScreen
import dev.lluissunol.iptv.ui.player.PlayerScreen
import dev.lluissunol.iptv.ui.movie.MovieDetailScreen
import dev.lluissunol.iptv.ui.playlist.CatalogActions
import dev.lluissunol.iptv.ui.playlist.FavoritesScreen
import dev.lluissunol.iptv.ui.playlist.PlaylistScreen
import dev.lluissunol.iptv.ui.playlist.PlaylistViewModel
import dev.lluissunol.iptv.ui.playlist.ScrollVersions
import dev.lluissunol.iptv.ui.playlist.Screen
import dev.lluissunol.iptv.ui.series.SeriesDetailScreen
import dev.lluissunol.iptv.ui.theme.OpenTvTheme

@Composable
fun OpenTvApp(viewModel: PlaylistViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Lives above the screen switch so the poster grid keeps its scroll
    // position across a series drill-down and back (rememberSaveable-backed:
    // it also survives rotation and process death).
    val vodGridState = rememberLazyGridState()
    // The list layout needs the same hoisting, or back from a detail would
    // jump a list-layout user to the top.
    val vodListState = rememberLazyListState()
    val scrollVersions = remember { ScrollVersions() }
    val actions = remember(viewModel) {
        CatalogActions(
            onSort = viewModel::setSort,
            onLanguage = viewModel::setLanguage,
            onGenre = viewModel::setGenre,
            onMinRating = viewModel::setMinRating7,
            onClearFilters = viewModel::clearSheetFilters,
            onClearQuery = viewModel::clearQuery,
            onOpenMovie = viewModel::openMovie,
            onDismissHint = viewModel::dismissMovieHint,
            onAcknowledgeReset = viewModel::acknowledgeControlsReset
        )
    }

    OpenTvTheme {
        val channel = state.selectedChannel
        val series = state.selectedSeries
        val movie = state.selectedMovie
        when {
            channel != null -> PlayerScreen(
                channel = channel,
                muted = state.playerMuted,
                onToggleMute = viewModel::togglePlayerMuted,
                onBack = viewModel::closePlayer
            )
            // Movie detail (long-press). Play from here opens the player and back
            // returns to this screen, exactly like episodes do.
            movie != null -> MovieDetailScreen(
                movie = movie,
                info = state.movieInfo,
                isLoading = state.movieInfoLoading,
                error = state.movieInfoError,
                isFavorite = movie.favoriteKey in state.favoriteKeys,
                onBack = viewModel::closeMovie,
                onPlay = { viewModel.playVod(movie) },
                onRetry = viewModel::retryMovieInfo,
                onClearError = viewModel::clearMovieError,
                onToggleFavorite = { viewModel.toggleFavoriteKey(movie.favoriteKey) }
            )
            // Series drill-down: seasons then episodes. Back returns to the
            // grid it was opened from, with its scroll position intact.
            series != null -> SeriesDetailScreen(
                series = series,
                info = state.seriesInfo,
                genres = state.selectedSeriesGenres,
                selectedSeason = state.selectedSeason,
                isLoading = state.seriesLoading,
                error = state.seriesError,
                favoriteKeys = state.favoriteKeys,
                onBack = viewModel::closeSeries,
                onRetry = viewModel::retrySeries,
                onSeasonChange = viewModel::setSeason,
                onPlayEpisode = viewModel::playEpisode,
                onToggleEpisodeFavorite = { episode ->
                    viewModel.toggleFavoriteKey(episode.favoriteKey)
                },
                onToggleSeriesFavorite = {
                    viewModel.toggleFavoriteKey(series.favoriteKey)
                },
                onClearError = viewModel::clearSeriesError
            )
            state.showFavorites -> FavoritesScreen(
                state = state,
                onBack = viewModel::hideFavorites,
                onTabChange = viewModel::setTab,
                onOpen = viewModel::openFavorite,
                onToggleFavorite = { entry ->
                    viewModel.toggleFavoriteKey(entry.favoriteKey)
                }
            )
            state.screen == Screen.LISTS -> ListsScreen(
                state = state,
                onOpenList = viewModel::openList,
                onAddList = viewModel::startAddList,
                onEditList = viewModel::editList,
                onDeleteList = viewModel::deleteList,
                onCancelForm = viewModel::cancelForm,
                onFormNameChange = viewModel::setFormName,
                onFormKindChange = viewModel::setFormKind,
                onFormUrlChange = viewModel::setFormUrl,
                onFormServerChange = viewModel::setFormServer,
                onFormUsernameChange = viewModel::setFormUsername,
                onFormPasswordChange = viewModel::setFormPassword,
                onFormSubmit = viewModel::submitForm
            )
            else -> PlaylistScreen(
                state = state,
                vodGridState = vodGridState,
                vodListState = vodListState,
                scrollVersions = scrollVersions,
                actions = actions,
                onBackToLists = viewModel::backToLists,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onQueryChange = viewModel::setQuery,
                onTabChange = viewModel::setTab,
                onSearchAllChange = viewModel::setSearchAll,
                onGroupChange = viewModel::setGroup,
                onVodCategoryChange = viewModel::setVodCategory,
                onVodLayoutToggle = viewModel::toggleVodLayout,
                onPlay = viewModel::play,
                onPlayVod = viewModel::playVod,
                onOpenSeries = viewModel::openSeries,
                onToggleFavoriteKey = viewModel::toggleFavoriteKey,
                onShowFavorites = viewModel::showFavorites,
                onEditActiveList = viewModel::editActiveList,
                onClearError = viewModel::clearError
            )
        }
    }
}
