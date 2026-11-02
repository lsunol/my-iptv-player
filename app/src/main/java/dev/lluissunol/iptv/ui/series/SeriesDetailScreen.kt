package dev.lluissunol.iptv.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.model.Episode
import dev.lluissunol.iptv.data.model.Season
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SeriesInfo
import dev.lluissunol.iptv.ui.playlist.EpisodeRow
import dev.lluissunol.iptv.ui.playlist.FavoriteButton
import dev.lluissunol.iptv.ui.playlist.GroupPill
import dev.lluissunol.iptv.ui.playlist.PosterThumb
import dev.lluissunol.iptv.ui.playlist.cleanChannelName
import dev.lluissunol.iptv.ui.formatNumber

/**
 * Series drill-down: series header, then seasons, then the episode list of the
 * selected season. Seasons/episodes are fetched lazily by the ViewModel
 * ([dev.lluissunol.iptv.ui.playlist.PlaylistViewModel.openSeries]) and cached
 * for the session; nothing is written to disk.
 *
 * Tapping an episode plays it through the unchanged player: episodes are mapped
 * to the same [dev.lluissunol.iptv.data.model.Channel] shape as a live channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    series: SeriesCard,
    info: SeriesInfo?,
    selectedSeason: Int?,
    isLoading: Boolean,
    error: String?,
    favoriteKeys: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleEpisodeFavorite: (Episode) -> Unit,
    onToggleSeriesFavorite: () -> Unit,
    onClearError: () -> Unit
) {
    BackHandler { onBack() }

    // A duplicate season number would duplicate a season-pill key and crash the
    // composition, so equal numbers are collapsed first (first occurrence wins,
    // matching the catalogue's own id dedupe).
    val seasons = remember(info) { info?.seasons?.distinctBy { it.number }.orEmpty() }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = cleanChannelName(series.name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            SeriesHeader(
                series = series,
                episodeCount = info?.episodeCount ?: 0,
                isFavorite = series.favoriteKey in favoriteKeys,
                onToggleFavorite = onToggleSeriesFavorite
            )

            error?.let { message ->
                SeriesErrorBanner(message = message, onRetry = onRetry, onDismiss = onClearError)
            }

            when {
                isLoading -> LoadingEpisodes()
                seasons.isEmpty() -> EmptyEpisodes()
                else -> {
                    SeasonPills(
                        seasons = seasons,
                        selected = season?.number,
                        onSeasonChange = onSeasonChange
                    )
                    if (season == null || season.episodes.isEmpty()) {
                        EmptyEpisodes()
                    } else {
                        EpisodeList(
                            episodes = season.episodes,
                            favoriteKeys = favoriteKeys,
                            onPlayEpisode = onPlayEpisode,
                            onToggleEpisodeFavorite = onToggleEpisodeFavorite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(
    series: SeriesCard,
    episodeCount: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        PosterThumb(
            posterUrl = series.posterUrl,
            title = series.name,
            modifier = Modifier.size(width = 92.dp, height = 138.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // The title already lives in the top app bar; the header shows the
            // plot and the episode count instead of repeating it.
            val plot = series.plot?.takeIf { it.isNotBlank() }
            if (plot == null) {
                Text(
                    text = cleanChannelName(series.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (episodeCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.series_episodes_count,
                        formatNumber(episodeCount)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (plot != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
        }
    }
}

@Composable
private fun SeasonPills(
    seasons: List<Season>,
    selected: Int?,
    onSeasonChange: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = seasons, key = { it.number }) { season ->
            GroupPill(
                label = stringResource(R.string.season_number, season.number),
                selected = season.number == selected,
                onClick = { onSeasonChange(season.number) }
            )
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Episode>,
    favoriteKeys: Set<String>,
    onPlayEpisode: (Episode) -> Unit,
    onToggleEpisodeFavorite: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Computed once per season snapshot: the key must be unique, and a
    // duplicate key is a composition crash rather than a cosmetic glitch.
    val keys = remember(episodes) { uniqueEpisodeKeys(episodes) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Episodes come from one lazily fetched season, so this list is small.
        itemsIndexed(
            items = episodes,
            key = { index, _ -> keys[index] },
            contentType = { _, _ -> "episode" }
        ) { _, episode ->
            EpisodeRow(
                episode = episode,
                isFavorite = episode.favoriteKey in favoriteKeys,
                onPlay = onPlayEpisode,
                onToggleFavorite = onToggleEpisodeFavorite
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * `LazyColumn` keys for one season's episodes: unique by construction.
 *
 * `season:episode:id` is already unique for the data the catalogue builds
 * ([Episode.id] is disambiguated per season), but the provider is the one
 * filling that data, so the row index is appended on a collision instead of
 * trusting it. Keys stay stable while the season snapshot does not change, and
 * the returned list always matches [episodes] in size and order.
 */
internal fun uniqueEpisodeKeys(episodes: List<Episode>): List<String> {
    val used = HashSet<String>(episodes.size)
    return episodes.mapIndexed { index, episode ->
        val base = "${episode.season}:${episode.episodeNum}:${episode.id}"
        if (used.add(base)) {
            base
        } else {
            // Retried until unused so that an id which already ends in the
            // suffix (`7#1`) cannot collide with the suffix of another row.
            var suffix = index
            var candidate: String
            do {
                candidate = "$base#$suffix"
                suffix++
            } while (!used.add(candidate))
            candidate
        }
    }
}

@Composable
private fun LoadingEpisodes() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.series_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyEpisodes() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                text = stringResource(R.string.series_no_episodes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Same actionable pattern as the playlist error banner, with a retry. */
@Composable
private fun SeriesErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.retry),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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
