package dev.lluissunol.iptv.ui

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lluissunol.iptv.ui.lists.ListsScreen
import dev.lluissunol.iptv.ui.player.PlayerScreen
import dev.lluissunol.iptv.ui.playlist.FavoritesScreen
import dev.lluissunol.iptv.ui.playlist.PlaylistScreen
import dev.lluissunol.iptv.ui.playlist.PlaylistViewModel
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

    OpenTvTheme {
        val channel = state.selectedChannel
        val series = state.selectedSeries
        when {
            channel != null -> PlayerScreen(
                channel = channel,
                muted = state.playerMuted,
                onToggleMute = viewModel::togglePlayerMuted,
                onBack = viewModel::closePlayer
            )
            // Series drill-down: seasons then episodes. Back returns to the
            // grid it was opened from, with its scroll position intact.
            series != null -> SeriesDetailScreen(
                series = series,
                info = state.seriesInfo,
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
