package dev.lluissunol.iptv.data.model

import dev.lluissunol.iptv.data.SearchNormalizer
import dev.lluissunol.iptv.data.XtreamApiCatalog
import dev.lluissunol.iptv.data.XtreamStreamMatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lean Xtream catalogue models.
 *
 * These are built from `player_api.php` metadata **merged with the parsed M3U**:
 * playback URLs come from the M3U (see [XtreamStreamMatcher]) and the API only
 * contributes names, posters, plots, ratings and categories. No raw API JSON is
 * retained after the merge, and `info` blobs are dropped, so a huge account does
 * not pay twice for its metadata (the parsed M3U already costs the heap).
 *
 * [VodItem], [SeriesCard] and [VodCategory] are [Serializable] because the whole
 * [VodCatalog] is cached on disk per list ([dev.lluissunol.iptv.data.XtreamCache]).
 * [Season]/[Episode]/[SeriesInfo] are fetched lazily per series and only kept in
 * memory, so they do not need to be serializable.
 */
@Serializable
data class VodCategory(
    val id: String,
    val name: String
)

/**
 * A movie/VOD entry ready for the poster grid. [playbackUrl] is always an M3U
 * URL (matched from the playlist, or the provider's own `/movie/U/P/<id>.m3u8`
 * pattern) — never a `container_extension` URL such as `.mkv` or `.avi`.
 *
 * [favoriteKey] reuses the *matched M3U channel's* key verbatim so stars
 * survive refreshes; only unmatched items fall back to the URL-hash key.
 */
@Serializable
data class VodItem(
    val streamId: Long,
    val name: String,
    val posterUrl: String?,
    val categoryId: String,
    /** 0.0 means "no rating supplied by the provider". */
    val rating: Double,
    val searchKey: String,
    val favoriteKey: String,
    val playbackUrl: String,
    /**
     * Quality badge taken from the matched M3U channel, or detected in the API
     * name. Defaulted so catalogues cached before this field still decode.
     */
    val qualityTag: String? = null
)

/** A series (its episodes are loaded lazily via `get_series_info`). */
@Serializable
data class SeriesCard(
    val seriesId: Long,
    val name: String,
    val posterUrl: String?,
    val plot: String?,
    val categoryId: String,
    /** 0.0 means "no rating supplied by the provider". */
    val rating: Double,
    val searchKey: String,
    /** Stable key independent of the M3U: `series:<series_id>`. */
    val favoriteKey: String,
    /** Quality badge detected in the series name, if any. */
    val qualityTag: String? = null
)

/** One season with its already URL-resolved episodes. */
data class Season(
    val number: Int,
    val episodes: List<Episode>
)

data class Episode(
    /**
     * The API id, disambiguated with a `#2`, `#3`, ... suffix when a provider
     * repeats it inside one season (see
     * `dev.lluissunol.iptv.data`'s `toSeriesInfo`); unique within its season
     * because the drill-down keys its episode rows on it. The raw form may be
     * `"123"` or `"ep-123"`.
     */
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    /** Always an M3U/HLS URL: the matched playlist entry, or the provider fallback. */
    val playbackUrl: String,
    val favoriteKey: String
)

/** Result of the lazy `get_series_info` call, grouped into seasons. */
data class SeriesInfo(
    val seriesId: Long,
    val name: String,
    val plot: String?,
    val posterUrl: String?,
    val seasons: List<Season>
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
}

/** The merged, cacheable catalogue of one Xtream list. */
@Serializable
data class VodCatalog(
    val movies: List<VodItem>,
    val series: List<SeriesCard>,
    val movieCategories: List<VodCategory>,
    val seriesCategories: List<VodCategory>
)

/**
 * Merges the decoded API metadata with the M3U-derived stream URLs.
 *
 * Rules, in order:
 * - items without a usable id, or whose id resolves to no URL at all
 *   (no M3U match and no fallback), are skipped;
 * - a movie keeps the matched channel's URL and `favoriteKey` verbatim so
 *   existing stars survive a refresh;
 * - an unmatched movie falls back to `fallbackUrl("movie", id)` (the provider's
 *   own `.m3u8` pattern) and gets the same URL-hash favorite key the parser
 *   would have built for a channel with that URL;
 * - series use the stable `series:<series_id>` key.
 */
fun buildVodCatalog(
    api: XtreamApiCatalog,
    matcher: XtreamStreamMatcher,
    fallbackUrl: (type: String, id: String) -> String?
): VodCatalog {
    val movies = ArrayList<VodItem>(api.vodStreams.size)
    // The poster grid uses `movie:<id>` as its LazyGrid key, so the ids must be
    // unique: a provider that repeats a stream_id would otherwise crash the
    // list. First occurrence wins, like the M3U matcher.
    val seenMovieIds = HashSet<Long>(api.vodStreams.size)
    for (dto in api.vodStreams) {
        if (dto.streamId <= 0L) continue
        if (!seenMovieIds.add(dto.streamId)) continue
        val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin nombre"
        val matched = matcher.movie(dto.streamId)
        val url = matched?.url ?: fallbackUrl("movie", dto.streamId.toString())
        if (url == null) continue
        movies += VodItem(
            streamId = dto.streamId,
            name = name,
            posterUrl = dto.streamIcon?.trim()?.takeIf { it.isNotEmpty() },
            categoryId = dto.categoryId,
            rating = dto.rating.takeIf { it > 0.0 } ?: 0.0,
            searchKey = SearchNormalizer.key(name),
            favoriteKey = matched?.favoriteKey ?: urlFavoriteKey(url),
            playbackUrl = url,
            qualityTag = matched?.qualityTag ?: qualityTagOf(name)
        )
    }

    val series = ArrayList<SeriesCard>(api.seriesList.size)
    val seenSeriesIds = HashSet<Long>(api.seriesList.size)
    for (dto in api.seriesList) {
        if (dto.seriesId <= 0L) continue
        if (!seenSeriesIds.add(dto.seriesId)) continue
        val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin nombre"
        series += SeriesCard(
            seriesId = dto.seriesId,
            name = name,
            posterUrl = dto.cover?.trim()?.takeIf { it.isNotEmpty() },
            plot = dto.plot?.trim()?.takeIf { it.isNotEmpty() },
            categoryId = dto.categoryId,
            rating = dto.rating.takeIf { it > 0.0 } ?: 0.0,
            searchKey = SearchNormalizer.key(name),
            favoriteKey = seriesFavoriteKey(dto.seriesId),
            qualityTag = qualityTagOf(name)
        )
    }

    return VodCatalog(
        movies = movies,
        series = series,
        movieCategories = categories(api.vodCategories),
        seriesCategories = categories(api.seriesCategories)
    )
}

/** Stable favorite key for a series, independent of the M3U contents. */
fun seriesFavoriteKey(seriesId: Long): String = "series:$seriesId"

/**
 * The same URL-hash favorite key [dev.lluissunol.iptv.data.PlaylistParser]
 * builds for a channel without `tvg-id`, so an unmatched catalogue item and a
 * later-matched channel agree on the key.
 */
internal fun urlFavoriteKey(url: String): String = "url:" + Integer.toHexString(url.hashCode())

private fun categories(dtos: List<dev.lluissunol.iptv.data.CategoryDto>): List<VodCategory> {
    val seen = HashSet<String>()
    val result = ArrayList<VodCategory>(dtos.size)
    for (dto in dtos) {
        val name = dto.categoryName?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val id = dto.categoryId.ifEmpty { name }
        if (seen.add(id)) result += VodCategory(id = id, name = name)
    }
    return result
}

/**
 * Maps a movie to the [Channel] shape the player consumes, so `PlayerScreen`
 * needs no changes for catalogue playback.
 */
fun VodItem.toChannel(): Channel = Channel(
    id = "movie:$streamId",
    name = name,
    url = playbackUrl,
    group = null,
    logoUrl = posterUrl,
    tvgId = null,
    sourceType = SourceType.MOVIE,
    searchKey = searchKey,
    qualityTag = qualityTag,
    favoriteKey = favoriteKey
)

/**
 * Maps an episode to the [Channel] shape the player already consumes, so
 * `PlayerScreen` needs no changes for series playback.
 */
fun Episode.toChannel(seriesName: String): Channel {
    return Channel(
        // Built from the episode's own parts instead of a pair of 32-bit
        // hashes: [id] is unique inside its season, and the season and
        // episode number keep two seasons' worth of equal ids apart, so two
        // episodes never share a Channel id (the player keys on it).
        id = "episode:$season:$episodeNum:$id",
        name = title,
        url = playbackUrl,
        group = seriesName,
        logoUrl = null,
        tvgId = null,
        sourceType = SourceType.SERIES,
        searchKey = SearchNormalizer.key("$seriesName $title"),
        qualityTag = null,
        favoriteKey = favoriteKey
    )
}

private val catalogJson = Json { ignoreUnknownKeys = true }

/** Encodes a catalog for the per-list disk cache. */
fun encodeVodCatalog(catalog: VodCatalog): String =
    catalogJson.encodeToString(VodCatalog.serializer(), catalog)

/**
 * Decodes a cached catalog. A corrupt or foreign file degrades to `null` (the
 * flat list still works), never to a crash at startup.
 */
fun decodeVodCatalog(json: String): VodCatalog? =
    runCatching { catalogJson.decodeFromString(VodCatalog.serializer(), json) }.getOrNull()
