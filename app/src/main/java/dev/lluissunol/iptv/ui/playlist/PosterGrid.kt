package dev.lluissunol.iptv.ui.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.lluissunol.iptv.ui.formatRating10
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.VodItem

/**
 * Poster grid of the Movies and Series tabs.
 *
 * Performance rules that make a catalogue of tens of thousands of entries safe:
 * - the row model is the lean [VodItem]/[SeriesCard] from the data layer; no
 *   parsed JSON and no per-row state object is kept in the composition;
 * - stable keys (`movie:<id>`, `series:<id>`) and a `contentType` per type, so
 *   recycling reuses the same cell layout;
 * - Coil is asked for an explicit bitmap size (the measured cell), and only a
 *   small window of posters ahead of the visible range is prefetched. The app
 *   already janks on large flings; a big prefetch window would make it worse,
 *   not better;
 * - no `SubcomposeAsyncImage`/`BoxWithConstraints`: no subcomposition per cell.
 */

/** Columns of the poster grid (2:3 portrait cells). */
private const val POSTER_COLUMNS = 3

/**
 * Posters prefetched ahead of the visible window. Deliberately small: the goal
 * is a hit when the next row scrolls in, not to fill the memory cache.
 */
private const val PREFETCH_AHEAD = 8

private const val CONTENT_TYPE_POSTER = "poster"

@Composable
internal fun MoviePosterGrid(
    items: List<VodItem>,
    favoriteKeys: Set<String>,
    gridState: LazyGridState,
    onPlay: (VodItem) -> Unit,
    onOpenDetails: (VodItem) -> Unit,
    onToggleFavorite: (VodItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val cellSize = rememberPosterCellSize(gridState)
    // Resolved once for the whole grid, never per cell.
    val playLabel = stringResource(R.string.play)
    val detailsLabel = stringResource(R.string.movie_details)
    LazyVerticalGrid(
        columns = GridCells.Fixed(POSTER_COLUMNS),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = items,
            key = { "movie:" + it.streamId },
            contentType = { CONTENT_TYPE_POSTER }
        ) { item ->
            PosterCell(
                posterUrl = item.posterUrl,
                title = item.name,
                qualityTag = item.qualityTag,
                rating = item.rating,
                cellSize = cellSize,
                isFavorite = item.favoriteKey in favoriteKeys,
                onClick = { onPlay(item) },
                onToggleFavorite = { onToggleFavorite(item) },
                onLongClick = { onOpenDetails(item) },
                onClickLabel = playLabel,
                onLongClickLabel = detailsLabel
            )
        }
    }
    PosterPrefetch(
        gridState = gridState,
        cellSize = cellSize,
        posterAt = { index -> items.getOrNull(index)?.posterUrl },
        // Identity hash, not the list itself: a LaunchedEffect key is compared
        // with structural equality on the main thread, and comparing two
        // 100k-entry catalogues would cost more than the prefetch saves.
        contentKey = System.identityHashCode(items)
    )
}

@Composable
internal fun SeriesPosterGrid(
    items: List<SeriesCard>,
    favoriteKeys: Set<String>,
    gridState: LazyGridState,
    onOpen: (SeriesCard) -> Unit,
    onToggleFavorite: (SeriesCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val cellSize = rememberPosterCellSize(gridState)
    LazyVerticalGrid(
        columns = GridCells.Fixed(POSTER_COLUMNS),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = items,
            key = { "series:" + it.seriesId },
            contentType = { CONTENT_TYPE_POSTER }
        ) { item ->
            PosterCell(
                posterUrl = item.posterUrl,
                title = item.name,
                qualityTag = item.qualityTag,
                rating = item.rating,
                cellSize = cellSize,
                isFavorite = item.favoriteKey in favoriteKeys,
                onClick = { onOpen(item) },
                onToggleFavorite = { onToggleFavorite(item) }
            )
        }
    }
    PosterPrefetch(
        gridState = gridState,
        cellSize = cellSize,
        posterAt = { index -> items.getOrNull(index)?.posterUrl },
        contentKey = System.identityHashCode(items)
    )
}

/**
 * One grid cell: 2:3 poster, title (max two lines), quality badge overlaid
 * bottom-start and the favourite star top-end. No state is remembered here
 * beyond Coil's painter, so the cell is safe to repeat tens of thousands of
 * times.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCell(
    posterUrl: String?,
    title: String,
    qualityTag: String?,
    rating: Double,
    cellSize: IntSize?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null
) {
    val cleanTitle = cleanChannelName(title)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
        ) {
            PosterImage(
                posterUrl = posterUrl,
                title = cleanTitle,
                cellSize = cellSize
            )
            if (!qualityTag.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    QualityBadge(text = qualityTag, overImage = true)
                }
            }
            if (rating > 0.0) {
                // Soft dark gradient along the top edge: it fades to nothing, so
                // there is no visible box, and keeps the rating (and the star)
                // legible on white or busy artwork.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                )
                RatingBadge(
                    rating = rating,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            FavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
                size = 36.dp,
                iconSize = 20.dp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = cleanTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The rating over a poster's top-left corner, as `7/10`: white, bold, with a
 * text shadow, over the cell's top scrim (see [PosterCell]), so it stays
 * readable over bright artwork. The "/10" is smaller and slightly translucent so
 * the number is what the eye catches. A cell carries no state for it, and its
 * description is the spoken rating.
 */
@Composable
private fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    val number = formatRating10(rating)
    val suffix = stringResource(R.string.rating_suffix)
    val description = stringResource(R.string.rating_cd, number)
    val text = buildAnnotatedString {
        append(number)
        withStyle(
            SpanStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
        ) { append(suffix) }
    }
    Box(
        modifier = modifier
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.95f),
                    offset = Offset(0f, 3f),
                    blurRadius = 10f
                )
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * The poster itself, with the generated placeholder drawn underneath: it shows
 * while the image loads and stays visible when the provider has no poster or
 * the download fails. Drawing the fallback first avoids a subcomposition and a
 * state read per cell.
 */
@Composable
internal fun PosterImage(
    posterUrl: String?,
    title: String,
    cellSize: IntSize?
) {
    val context = LocalContext.current
    val request = remember(posterUrl, cellSize) {
        posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .apply { if (cellSize != null) size(cellSize.width, cellSize.height) }
                .build()
        }
    }
    PosterFallback(title)
    if (request != null) {
        Image(
            painter = rememberAsyncImagePainter(model = request),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** Small poster used by the list layout and the series header. */
@Composable
internal fun PosterThumb(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(posterUrl) {
        posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context).data(url).crossfade(true).build()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        PosterFallback(title)
        if (request != null) {
            Image(
                painter = rememberAsyncImagePainter(model = request),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Generated initial (same palette as channel logos) shown before/without a poster. */
@Composable
private fun PosterFallback(title: String) {
    val index = ((title.hashCode() % LogoPalette.size) + LogoPalette.size) % LogoPalette.size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LogoPalette[index]),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = logoInitial(title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** Size of one laid-out cell, or null before the first layout pass. */
@Composable
private fun rememberPosterCellSize(gridState: LazyGridState): IntSize? {
    val size by remember(gridState) {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.size
                ?.takeIf { it.width > 0 && it.height > 0 }
        }
    }
    return size
}

/**
 * Enqueues the next few posters into Coil's memory cache so the row that
 * scrolls in resolves without a network round trip.
 *
 * The effect is keyed on the last visible index, the cell size and the list
 * identity only: the captured poster lookup must not restart it on every
 * recomposition (the catalogue list identity changes only on load, filter or
 * refresh).
 */
@Composable
private fun PosterPrefetch(
    gridState: LazyGridState,
    cellSize: IntSize?,
    posterAt: (Int) -> String?,
    contentKey: Any?
) {
    val context = LocalContext.current
    val lastVisibleIndex by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
    }

    // [contentKey] restarts the window when the catalogue changes (tab switch,
    // new filter, refresh) so the next row of the *new* list is the one warmed.
    LaunchedEffect(lastVisibleIndex, cellSize, contentKey) {
        val last = lastVisibleIndex ?: return@LaunchedEffect
        val size = cellSize ?: return@LaunchedEffect
        val loader = context.imageLoader
        for (index in last + 1..last + PREFETCH_AHEAD) {
            val url = posterAt(index)?.takeIf { it.isNotBlank() } ?: continue
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(size.width, size.height)
                    .build()
            )
        }
    }
}
