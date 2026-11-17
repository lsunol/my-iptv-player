package dev.lluissunol.iptv.ui.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.ui.HeaderLayout
import dev.lluissunol.iptv.ui.UpdatedDay
import dev.lluissunol.iptv.ui.UpdatedWhen
import dev.lluissunol.iptv.ui.chooseHeaderLayout
import dev.lluissunol.iptv.ui.updatedWhen

/** Long and short subtitle plus the spoken form of "when the list was refreshed". */
private class UpdatedTexts(val long: String, val short: String, val spoken: String)

@Composable
private fun updatedTexts(whenUpdated: UpdatedWhen): UpdatedTexts {
    val whenPart = when (whenUpdated.day) {
        UpdatedDay.TODAY -> stringResource(R.string.updated_when_today, whenUpdated.time)
        UpdatedDay.YESTERDAY -> stringResource(R.string.updated_when_yesterday, whenUpdated.time)
        UpdatedDay.DATE -> stringResource(R.string.updated_date_time, whenUpdated.date, whenUpdated.time)
        UpdatedDay.OLD_YEAR -> whenUpdated.date
    }
    val spoken = when (whenUpdated.day) {
        UpdatedDay.TODAY -> stringResource(R.string.updated_cd_today, whenUpdated.time)
        UpdatedDay.YESTERDAY -> stringResource(R.string.updated_cd_yesterday, whenUpdated.time)
        UpdatedDay.DATE -> stringResource(R.string.updated_cd_date, whenUpdated.date, whenUpdated.time)
        UpdatedDay.OLD_YEAR -> stringResource(R.string.updated_cd_day, whenUpdated.date)
    }
    return UpdatedTexts(
        long = stringResource(R.string.header_updated, whenPart),
        short = whenPart.replaceFirstChar { it.uppercase() },
        spoken = spoken
    )
}

/**
 * Compact top bar (64 dp): brand mark, active list name with a caret and the
 * last-updated time, then Buscar / Favoritos / Actualizar / overflow. Tapping
 * Buscar turns the same bar into a search field in place. The 2 dp refresh
 * progress is overlaid on its bottom edge and takes no layout height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppHeader(
    state: UiState,
    onShowLists: () -> Unit,
    onRefresh: () -> Unit,
    onShowFavorites: () -> Unit,
    onEditActiveList: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchAllChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    // 64 dp up to ~1.4x font scale, then it grows so a two-line title block is never clipped.
    val barHeight = maxOf(64.dp, with(density) { 40.sp.toDp() } + 8.dp)
    val searchMode = state.searchMode
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val searchIconFocus = remember { FocusRequester() }
    // Only a tap on Buscar may pop the keyboard; recomposition and rotation must not.
    var focusSearchField by remember { mutableStateOf(false) }
    var wasSearching by remember { mutableStateOf(searchMode) }
    LaunchedEffect(searchMode) {
        if (wasSearching && !searchMode) {
            runCatching { searchIconFocus.requestFocus() }
        }
        wasSearching = searchMode
    }

    Box {
        BoxWithConstraints {
            val totalWidth = maxWidth
            val hasPlaylist = state.playlist != null
            val updated = state.lastUpdated?.let {
                updatedTexts(
                    updatedWhen(
                        it,
                        System.currentTimeMillis(),
                        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
                    )
                )
            }
            val subtitleStyle = MaterialTheme.typography.labelSmall
            val measurer = rememberTextMeasurer()
            // 4 dp bar padding + 48 dp mark + 4 + 4 dp title padding + the title
            // slot's own 8 dp of insets, then 48 dp per action.
            val fixedChrome = 4.dp + 48.dp + 8.dp + 8.dp + 4.dp
            val layout: HeaderLayout = if (updated == null || !hasPlaylist) {
                HeaderLayout(useShortSubtitle = false, favoritesInOverflow = false)
            } else {
                val withFav = with(density) { (totalWidth - fixedChrome - 48.dp * 4).toPx() }
                val withoutFav = with(density) { (totalWidth - fixedChrome - 48.dp * 3).toPx() }
                chooseHeaderLayout(
                    longWidth = measurer.textWidth(updated.long, subtitleStyle),
                    shortWidth = measurer.textWidth(updated.short, subtitleStyle),
                    availableWithFavorites = withFav,
                    availableWithoutFavorites = withoutFav
                )
            }
            val favoritesInOverflow = layout.favoritesInOverflow

            TopAppBar(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B2A28), MaterialTheme.colorScheme.background)
                    )
                ),
                expandedHeight = barHeight,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    if (searchMode) {
                        IconButton(onClick = onCloseSearch) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.search_close)
                            )
                        }
                    } else {
                        // Decorative brand mark: not focusable, not clickable.
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.logo_mark),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                },
                title = {
                    if (searchMode) {
                        SearchTitle(
                            query = state.query,
                            hint = stringResource(
                                if (state.isCatalogTab) R.string.search_hint_titles else R.string.search_hint
                            ),
                            requestFocus = focusSearchField,
                            onFocusRequested = { focusSearchField = false },
                            onQueryChange = onQueryChange
                        )
                    } else {
                        TitleBlock(
                            name = state.activeList?.name ?: stringResource(R.string.app_name),
                            subtitle = when {
                                updated == null -> stringResource(R.string.header_never_updated)
                                layout.useShortSubtitle -> updated.short
                                else -> updated.long
                            },
                            spoken = updated?.spoken,
                            onClick = onShowLists
                        )
                    }
                },
                actions = {
                    if (searchMode) {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                        IconToggleButton(
                            checked = state.searchAll,
                            onCheckedChange = onSearchAllChange
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = stringResource(R.string.search_all),
                                tint = if (state.searchAll) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    } else {
                        if (hasPlaylist) {
                            IconButton(
                                onClick = {
                                    focusSearchField = true
                                    onOpenSearch()
                                },
                                modifier = Modifier.focusRequester(searchIconFocus)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!favoritesInOverflow) {
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
                            }
                            IconButton(
                                onClick = onRefresh,
                                enabled = !state.isLoading
                            ) {
                                if (state.isLoading) {
                                    val loadingCd = stringResource(R.string.refreshing_cd)
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clearAndSetSemantics { contentDescription = loadingCd },
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.widthIn(min = 200.dp)
                            ) {
                                if (favoritesInOverflow && hasPlaylist) {
                                    OverflowItem(Icons.Default.Star, stringResource(R.string.favorites)) {
                                        menuOpen = false
                                        onShowFavorites()
                                    }
                                }
                                OverflowItem(
                                    Icons.AutoMirrored.Filled.List,
                                    stringResource(R.string.lists_title)
                                ) {
                                    menuOpen = false
                                    onShowLists()
                                }
                                OverflowItem(
                                    Icons.Default.Edit,
                                    stringResource(R.string.edit_list),
                                    enabled = state.activeList != null
                                ) {
                                    menuOpen = false
                                    onEditActiveList()
                                }
                            }
                        }
                    }
                }
            )
        }
        if (state.isLoading && state.playlist != null) {
            RefreshProgress(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun OverflowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

private fun TextMeasurer.textWidth(text: String, style: TextStyle): Float =
    measure(text = text, style = style, maxLines = 1, softWrap = false).size.width.toFloat()

/** Active list name + caret + last-updated time: one tap target that opens the lists screen. */
@Composable
private fun TitleBlock(
    name: String,
    subtitle: String,
    spoken: String?,
    onClick: () -> Unit
) {
    val description = if (spoken != null) {
        stringResource(R.string.header_title_cd_updated, name, spoken)
    } else {
        stringResource(R.string.header_title_cd, name)
    }
    Column(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
            },
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The search field that takes the title slot while the bar is in search mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTitle(
    query: String,
    hint: String,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (requestFocus) {
            runCatching { requester.requestFocus() }
            onFocusRequested()
        }
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
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
private fun RefreshProgress(state: UiState, modifier: Modifier = Modifier) {
    val progress = if (state.totalBytes > 0) {
        (state.progressBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
    } else {
        null
    }
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
