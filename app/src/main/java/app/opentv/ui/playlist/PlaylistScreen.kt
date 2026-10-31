package app.opentv.ui.playlist

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import app.opentv.R
import app.opentv.data.model.Channel
import app.opentv.data.model.Playlist
import app.opentv.data.model.SourceType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LiveColor = Color(0xFFF87171)
private val LogoPalette = listOf(
    Color(0xFF1D4ED8),
    Color(0xFF7C3AED),
    Color(0xFF0F766E),
    Color(0xFFB45309),
    Color(0xFFBE123C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTabChange: (SourceType?) -> Unit,
    onSearchAllChange: (Boolean) -> Unit,
    onGroupChange: (String?) -> Unit,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onShowFavorites: () -> Unit,
    onShowUrlEditor: () -> Unit,
    onCancelUrlEditor: () -> Unit,
    onClearError: () -> Unit
) {
    val showEditor = state.showUrlEditor || state.playlist == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                state = state,
                showEditor = showEditor,
                onRefresh = onRefresh,
                onShowFavorites = onShowFavorites,
                onShowUrlEditor = onShowUrlEditor
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showEditor) {
                UrlEntry(
                    state = state,
                    onUrlChange = onUrlChange,
                    onLoad = onLoad,
                    onCancel = if (state.playlist != null) onCancelUrlEditor else null,
                    onClearError = onClearError
                )
            } else {
                PlaylistContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onTabChange = onTabChange,
                    onSearchAllChange = onSearchAllChange,
                    onGroupChange = onGroupChange,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onRetry = onRetry
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHeader(
    state: UiState,
    showEditor: Boolean,
    onRefresh: () -> Unit,
    onShowFavorites: () -> Unit,
    onShowUrlEditor: () -> Unit
) {
    val subtitle = if (state.playlist == null) {
        stringResource(R.string.tagline)
    } else {
        val count = formatNumber(state.playlist.channels.size)
        val base = stringResource(R.string.channels_count, count)
        val updated = state.lastUpdated?.let { " · " + shortDate(it) }.orEmpty()
        "$base$updated"
    }

    TopAppBar(
        modifier = Modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF0B2A28), MaterialTheme.colorScheme.background)
            )
        ),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (state.playlist != null && !showEditor) {
                IconButton(onClick = onShowFavorites) {
                    Icon(
                        imageVector = if (state.favoriteKeys.isEmpty()) {
                            Icons.Default.StarBorder
                        } else {
                            Icons.Default.Star
                        },
                        contentDescription = stringResource(R.string.favorites),
                        tint = if (state.favoriteKeys.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
                IconButton(onClick = onShowUrlEditor) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.change_list)
                    )
                }
            }
        }
    )
}

@Composable
private fun UrlEntry(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onCancel: (() -> Unit)?,
    onClearError: () -> Unit
) {
    var showUrl by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = onCancel != null) {
        onCancel?.invoke()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Text(
            text = stringResource(R.string.no_playlist_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.no_playlist_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.urlInput,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.url_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.url_placeholder),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onLoad() }),
            isError = state.error != null,
            visualTransformation = if (showUrl) {
                VisualTransformation.None
            } else {
                RedactCredentialsTransformation
            },
            trailingIcon = {
                IconButton(onClick = { showUrl = !showUrl }) {
                    Icon(
                        imageVector = if (showUrl) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (showUrl) {
                            "Ocultar credenciales"
                        } else {
                            "Mostrar credenciales"
                        }
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Button(
            onClick = onLoad,
            enabled = state.urlInput.isNotBlank() && !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                when {
                    state.isReadingCache -> stringResource(R.string.loading_cache)
                    state.isLoading -> stringResource(R.string.loading_list)
                    else -> stringResource(R.string.load_list)
                }
            )
        }

        if (state.isLoading) {
            LoadingProgress(state)
        }

        state.error?.let { error ->
            ErrorCard(
                error = error,
                onRetry = onLoad,
                onDismiss = onClearError
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.url_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.cancel))
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
            "Descargando ${formatBytes(state.progressBytes)} de ${formatBytes(state.totalBytes)} · ${state.parsedEntries} canales"
        else ->
            "Descargando ${formatBytes(state.progressBytes)} · ${state.parsedEntries} canales"
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
    onQueryChange: (String) -> Unit,
    onTabChange: (SourceType?) -> Unit,
    onSearchAllChange: (Boolean) -> Unit,
    onGroupChange: (String?) -> Unit,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRetry: () -> Unit
) {
    val playlist = state.playlist ?: return

    Column(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            RefreshProgress(state)
        }

        Spacer(Modifier.height(4.dp))
        TabPills(
            state = state,
            playlist = playlist,
            onTabChange = onTabChange
        )

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            searchAll = state.searchAll,
            onSearchAllChange = onSearchAllChange
        )

        GroupPills(state = state, onGroupChange = onGroupChange)

        ListHeader(state = state, playlist = playlist)

        state.error?.let { message ->
            ErrorBanner(message = message, onRetry = onRetry)
        }

        if (state.filteredChannels.isEmpty()) {
            EmptyState(state)
        } else {
            LazyColumn(
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
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RefreshProgress(state: UiState) {
    val progress = if (state.totalBytes > 0) {
        (state.progressBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
    } else {
        null
    }
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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
    playlist: Playlist,
    onTabChange: (SourceType?) -> Unit
) {
    val tabs = listOf(
        TabSpec(SourceType.LIVE, stringResource(R.string.tab_live), playlist.count(SourceType.LIVE)),
        TabSpec(SourceType.MOVIE, stringResource(R.string.tab_movies), playlist.count(SourceType.MOVIE)),
        TabSpec(SourceType.SERIES, stringResource(R.string.tab_series), playlist.count(SourceType.SERIES)),
        TabSpec(null, stringResource(R.string.tab_all), playlist.channels.size)
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    searchAll: Boolean,
    onSearchAllChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
                IconButton(onClick = { onSearchAllChange(!searchAll) }) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = stringResource(R.string.search_all),
                        tint = if (searchAll) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

@Composable
private fun GroupPills(state: UiState, onGroupChange: (String?) -> Unit) {
    val groups = state.groups.take(12)
    if (groups.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all-groups") {
            GroupPill(
                label = stringResource(R.string.all_groups),
                selected = state.selectedGroup == null,
                onClick = { onGroupChange(null) }
            )
        }
        items(items = groups, key = { it.name }) { group ->
            GroupPill(
                label = group.name,
                selected = state.selectedGroup == group.name,
                onClick = {
                    onGroupChange(if (state.selectedGroup == group.name) null else group.name)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPill(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun ListHeader(state: UiState, playlist: Playlist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.channels_count,
                formatNumber(state.filteredChannels.size)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (playlist.warnings.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        R.string.warning_count,
                        playlist.warnings.size
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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
private fun EmptyState(state: UiState) {
    val message = when {
        state.playlist == null -> stringResource(R.string.empty_playlist)
        state.query.isNotBlank() -> stringResource(R.string.empty_search)
        else -> stringResource(R.string.empty_tab)
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
        }
    }
}

@Composable
private fun ChannelRow(
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
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) {
                    Icons.Default.Star
                } else {
                    Icons.Default.StarBorder
                },
                contentDescription = stringResource(
                    if (isFavorite) R.string.remove_favorite else R.string.add_favorite
                ),
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
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
private fun QualityBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.primary
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

private object RedactCredentialsTransformation : VisualTransformation {
    private val pattern = Regex("""(?i)(username|password|token|key|auth)=([^&\s]+)""")

    override fun filter(text: AnnotatedString): TransformedText {
        val redacted = pattern.replace(text.text) { match ->
            val value = match.groupValues[2]
            "${match.groupValues[1]}=" + "•".repeat(value.length)
        }
        return TransformedText(AnnotatedString(redacted), OffsetMapping.Identity)
    }
}

private fun cleanChannelName(name: String): String {
    val cleaned = name.replace(Regex("""^\s*\|[^|]*\|\s*"""), "").trim()
    return cleaned.ifEmpty { name.trim() }
}

private fun logoInitial(name: String): String {
    val cleaned = cleanChannelName(name)
    return cleaned.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}

private fun formatCompact(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", value / 1_000.0)
    else -> value.toString()
}

private fun formatNumber(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

private fun shortDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1.0) String.format(Locale.getDefault(), "%.1f MB", mb)
    else "${bytes / 1000} KB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: UiState,
    onBack: () -> Unit,
    onTabChange: (SourceType?) -> Unit,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    val favorites = state.favorites
    val filtered = when (val tab = state.tab) {
        null -> favorites
        else -> favorites.filter { it.sourceType == tab }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        key = { it.id },
                        contentType = { it.sourceType }
                    ) { channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = true,
                            onClick = { onPlay(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FavoritePills(
    favorites: List<Channel>,
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
