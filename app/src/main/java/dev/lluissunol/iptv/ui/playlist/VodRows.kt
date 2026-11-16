package dev.lluissunol.iptv.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.GenreLabel
import dev.lluissunol.iptv.data.model.Episode
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.releaseYear
import dev.lluissunol.iptv.ui.formatRating10
import dev.lluissunol.iptv.ui.shortDay

/**
 * List layout of the catalogue tabs, used when the user switches the Movies or
 * Series tab from the poster grid to the list.
 *
 * The rows are as lean as the grid cells: a poster thumbnail, two lines of
 * text, the quality badge and the favourite star. No per-row state, stable keys
 * provided by the caller.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VodRow(
    posterUrl: String?,
    title: String,
    subtitle: String,
    qualityTag: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null
) {
    val cleanTitle = cleanChannelName(title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(
            posterUrl = posterUrl,
            title = cleanTitle,
            modifier = Modifier.size(width = 44.dp, height = 66.dp)
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = cleanTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!qualityTag.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(qualityTag)
                }
            }
        }

        Spacer(Modifier.width(4.dp))
        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
    }
}

/**
 * The facts a list row can show about a title, in priority order: rating, year,
 * genres, then what is specific to the type. Every part is omitted when the
 * provider did not send it, so a panel with little data simply gets a shorter
 * line; [fallback] stands in when there is nothing at all.
 */
@Composable
private fun factsLine(
    rating: Double,
    releaseKey: Int,
    genres: List<GenreLabel>,
    extra: String?,
    fallback: String
): String {
    val parts = ArrayList<String>(4)
    if (rating > 0.0) parts += stringResource(R.string.rating_short, formatRating10(rating))
    if (releaseKey > 0) parts += releaseYear(releaseKey).toString()
    if (genres.isNotEmpty()) {
        val labels = ArrayList<String>(2)
        for (genre in genres.take(2)) labels += genreLabel(genre)
        parts += labels.joinToString(", ")
    }
    if (extra != null) parts += extra
    return if (parts.isEmpty()) fallback else parts.joinToString(" · ")
}

/** One movie of the catalogue, in list layout. */
@Composable
internal fun MovieRow(
    item: VodItem,
    isFavorite: Boolean,
    onPlay: (VodItem) -> Unit,
    onToggleFavorite: (VodItem) -> Unit,
    /** Long-press opens the movie detail; null (favourites) keeps a plain tap target. */
    onOpenDetails: ((VodItem) -> Unit)? = null,
    playLabel: String? = null,
    detailsLabel: String? = null,
    /** Labels of the catalogue's genre table, resolved once by the list. */
    genreLabels: List<List<GenreLabel>> = emptyList(),
    /** The date a title was added is only worth a line while sorting by it. */
    showAdded: Boolean = false
) {
    val added = if (showAdded && item.added > 0L) {
        stringResource(R.string.added_on, shortDay(item.added * 1000L))
    } else {
        null
    }
    VodRow(
        posterUrl = item.posterUrl,
        title = item.name,
        subtitle = factsLine(
            rating = item.rating,
            releaseKey = item.releaseKey,
            genres = genreLabels.getOrNull(item.genreRef).orEmpty(),
            extra = added,
            fallback = stringResource(R.string.tab_movies)
        ),
        qualityTag = item.qualityTag,
        isFavorite = isFavorite,
        onClick = { onPlay(item) },
        onToggleFavorite = { onToggleFavorite(item) },
        onLongClick = onOpenDetails?.let { open -> { open(item) } },
        onClickLabel = playLabel,
        onLongClickLabel = detailsLabel
    )
}

/** One series of the catalogue, in list layout; tapping opens the drill-down. */
@Composable
internal fun SeriesRow(
    card: SeriesCard,
    isFavorite: Boolean,
    onOpen: (SeriesCard) -> Unit,
    onToggleFavorite: (SeriesCard) -> Unit,
    genreLabels: List<List<GenreLabel>> = emptyList()
) {
    val runtime = if (card.runtimeMin > 0) {
        stringResource(R.string.duration_minutes, card.runtimeMin)
    } else {
        null
    }
    VodRow(
        posterUrl = card.posterUrl,
        title = card.name,
        subtitle = factsLine(
            rating = card.rating,
            releaseKey = card.releaseKey,
            genres = genreLabels.getOrNull(card.genreRef).orEmpty(),
            extra = runtime,
            fallback = stringResource(R.string.tab_series)
        ),
        qualityTag = card.qualityTag,
        isFavorite = isFavorite,
        onClick = { onOpen(card) },
        onToggleFavorite = { onToggleFavorite(card) }
    )
}

/**
 * One episode row of the series drill-down. [Episode.playbackUrl] is an HLS
 * URL resolved by the repository, so tapping plays with the unchanged player.
 */
@Composable
internal fun EpisodeRow(
    episode: Episode,
    isFavorite: Boolean,
    onPlay: (Episode) -> Unit,
    onToggleFavorite: (Episode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay(episode) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = episodeLabel(episode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.episode_number, episode.season, episode.episodeNum),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(4.dp))
        FavoriteButton(isFavorite = isFavorite, onClick = { onToggleFavorite(episode) })
    }
}

/**
 * The chip left of an episode row: the episode number alone. The season is
 * already the selected pill above the list and the row's subtitle repeats both,
 * so `S01E05` in the chip was the third time the same fact was printed.
 */
private fun episodeLabel(episode: Episode): String =
    episode.episodeNum.toString().padStart(2, '0')
