package app.opentv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.ui.lists.ListsScreen
import app.opentv.ui.player.PlayerScreen
import app.opentv.ui.playlist.FavoritesScreen
import app.opentv.ui.playlist.PlaylistScreen
import app.opentv.ui.playlist.PlaylistViewModel
import app.opentv.ui.playlist.Screen
import app.opentv.ui.theme.OpenTvTheme

@Composable
fun OpenTvApp(viewModel: PlaylistViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OpenTvTheme {
        val channel = state.selectedChannel
        when {
            channel != null -> PlayerScreen(channel = channel, onBack = viewModel::closePlayer)
            state.showFavorites -> FavoritesScreen(
                state = state,
                onBack = viewModel::hideFavorites,
                onTabChange = viewModel::setTab,
                onPlay = viewModel::play,
                onToggleFavorite = viewModel::toggleFavorite
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
                onBackToLists = viewModel::backToLists,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onQueryChange = viewModel::setQuery,
                onTabChange = viewModel::setTab,
                onSearchAllChange = viewModel::setSearchAll,
                onGroupChange = viewModel::setGroup,
                onPlay = viewModel::play,
                onToggleFavorite = viewModel::toggleFavorite,
                onShowFavorites = viewModel::showFavorites,
                onEditActiveList = viewModel::editActiveList,
                onClearError = viewModel::clearError
            )
        }
    }
}
