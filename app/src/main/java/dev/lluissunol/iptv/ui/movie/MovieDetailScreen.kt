package dev.lluissunol.iptv.ui.movie

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.model.MovieInfo
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.releaseYear
import dev.lluissunol.iptv.ui.formatDurationParts
import dev.lluissunol.iptv.ui.formatRating10
import dev.lluissunol.iptv.ui.playlist.FavoriteButton
import dev.lluissunol.iptv.ui.playlist.PosterThumb
import dev.lluissunol.iptv.ui.playlist.QualityBadge
import dev.lluissunol.iptv.ui.playlist.cleanChannelName
import dev.lluissunol.iptv.ui.playlist.genreLabel
import dev.lluissunol.iptv.ui.series.DetailErrorBanner

/**
 * Movie detail, reached by long-pressing a movie (a tap still plays it).
 *
 * The facts come from `get_vod_info` and are display-only: Play uses the
 * catalogue's [VodItem.playbackUrl] and is enabled from the first frame, so a
 * slow, failed or empty metadata call never blocks playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movie: VodItem,
    info: MovieInfo?,
    isLoading: Boolean,
    error: String?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    BackHandler { onBack() }
    val title = cleanChannelName(movie.name)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Header(movie = movie, info = info, isLoading = isLoading)

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
                Spacer(Modifier.width(8.dp))
                FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite, size = 48.dp)
            }

            error?.let { reason ->
                // The reason is the mapped, specific message; the tail tells the
                // user playback is unaffected.
                DetailErrorBanner(
                    message = stringResource(R.string.movie_detail_error) + " " + reason + "\n" +
                        stringResource(R.string.movie_detail_error_play),
                    onRetry = onRetry,
                    onDismiss = onClearError,
                    maxLines = 8,
                    // The detail column already has the 20 dp page margin.
                    horizontalPadding = 0.dp
                )
            }

            val plot = info?.plot
            when {
                plot != null -> {
                    Spacer(Modifier.height(16.dp))
                    Text(text = plot, style = MaterialTheme.typography.bodyMedium)
                }
                info != null && info.isEmpty && error == null -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.movie_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (info != null && info.cast.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.movie_cast, info.cast.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Header(movie: VodItem, info: MovieInfo?, isLoading: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        PosterThumb(
            posterUrl = movie.posterUrl ?: info?.posterUrl,
            title = cleanChannelName(movie.name),
            modifier = Modifier.size(width = 120.dp, height = 180.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Facts: the detail's value first, then the list's, each omitted when unknown.
            val year = info?.year?.takeIf { it > 0 } ?: releaseYear(movie.releaseKey)
            val durationSec = info?.durationSec ?: 0
            val rating = info?.rating10?.takeIf { it > 0.0 } ?: movie.rating
            val parts = ArrayList<String>(3)
            if (year > 0) parts += year.toString()
            if (durationSec > 0) {
                val (hours, minutes) = formatDurationParts(durationSec)
                parts += if (hours > 0) {
                    stringResource(R.string.duration_hours_minutes, hours, minutes)
                } else {
                    stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
                }
            }
            val ratingText = rating.takeIf { it > 0.0 }?.let { formatRating10(it) }
            if (ratingText != null) parts += stringResource(R.string.rating_short, ratingText)
            val ratingCd = ratingText?.let { stringResource(R.string.rating_cd, it) }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (ratingCd != null) {
                        Modifier.semantics {
                            contentDescription = (parts.dropLast(1) + ratingCd).joinToString(". ")
                        }
                    } else {
                        Modifier
                    }
                )
            }
            info?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                Text(
                    text = genres.map { genreLabel(it) }.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            info?.director?.let { director ->
                Text(
                    text = stringResource(R.string.movie_director, director),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            movie.qualityTag?.takeIf { it.isNotBlank() }?.let { QualityBadge(it) }
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.movie_detail_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
