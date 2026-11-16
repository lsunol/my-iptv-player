package dev.lluissunol.iptv.data.model

import dev.lluissunol.iptv.data.CATALOG_SCHEMA_VERSION
import dev.lluissunol.iptv.data.CatalogEntry
import dev.lluissunol.iptv.data.GenreLabel
import dev.lluissunol.iptv.data.GenreNormalizer
import dev.lluissunol.iptv.data.SearchNormalizer
import dev.lluissunol.iptv.data.XtreamApiCatalog
import dev.lluissunol.iptv.data.XtreamStreamMatcher
import dev.lluissunol.iptv.data.currentYearUtc
import dev.lluissunol.iptv.data.firstReleaseKey
import dev.lluissunol.iptv.data.normalizeArtworkUrl
import dev.lluissunol.iptv.data.normalizeRating10
import dev.lluissunol.iptv.data.parseEpochSeconds
import dev.lluissunol.iptv.data.parseRuntimeMinutes
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
    override val name: String,
    val posterUrl: String?,
    override val categoryId: String,
    /** Normalised 0-10 rating; 0.0 means "no rating supplied by the provider". */
    override val rating: Double,
    override val searchKey: String,
    val favoriteKey: String,
    val playbackUrl: String,
    /**
     * Quality badge taken from the matched M3U channel, or detected in the API
     * name. Defaulted so catalogues cached before this field still decode.
     */
    val qualityTag: String? = null,
    /** Epoch seconds the provider added the title; 0 means unknown. */
    val added: Long = 0L,
    /** `yyyymmdd` / `yyyy0000` release key; 0 means unknown. */
    override val releaseKey: Int = 0,
    /** Index into [VodCatalog.movieGenres]; -1 means no usable genre. */
    override val genreRef: Int = -1
) : CatalogEntry

/** A series (its episodes are loaded lazily via `get_series_info`). */
@Serializable
data class SeriesCard(
    val seriesId: Long,
    override val name: String,
    val posterUrl: String?,
    val plot: String?,
    override val categoryId: String,
    /** Normalised 0-10 rating; 0.0 means "no rating supplied by the provider". */
    override val rating: Double,
    override val searchKey: String,
    /** Stable key independent of the M3U: `series:<series_id>`. */
    val favoriteKey: String,
    /** Quality badge detected in the series name, if any. */
    val qualityTag: String? = null,
    /** `yyyymmdd` / `yyyy0000` release key from the list; 0 means unknown. */
    override val releaseKey: Int = 0,
    /** Episode runtime in minutes; 0 means unknown. */
    val runtimeMin: Int = 0,
    /** Index into [VodCatalog.seriesGenres]; -1 means no usable genre. */
    override val genreRef: Int = -1
) : CatalogEntry

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
    val favoriteKey: String,
    /** `yyyymmdd` / `yyyy0000` from the episode's own release date; 0 unknown. */
    val releaseKey: Int = 0
)

/** Result of the lazy `get_series_info` call, grouped into seasons. */
data class SeriesInfo(
    val seriesId: Long,
    val name: String,
    val plot: String?,
    val posterUrl: String?,
    val seasons: List<Season>,
    /** Broadcast years from the episodes' release dates; 0 means unknown. */
    val firstYear: Int = 0,
    val lastYear: Int = 0
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
}

/** The merged, cacheable catalogue of one Xtream list. */
@Serializable
data class VodCatalog(
    val movies: List<VodItem>,
    val series: List<SeriesCard>,
    val movieCategories: List<VodCategory>,
    val seriesCategories: List<VodCategory>,
    /**
     * Shape of this cache. Defaults to 0 ON PURPOSE: the cache JSON does not
     * encode defaults, so an old file has no such field and must decode as
     * outdated (0), never as current. [buildVodCatalog] sets the real version.
     */
    val schemaVersion: Int = 0,
    /** Distinct raw genre strings of the movies ([VodItem.genreRef] indexes it). */
    val movieGenres: List<String> = emptyList(),
    /** Distinct raw genre strings of the series ([SeriesCard.genreRef] indexes it). */
    val seriesGenres: List<String> = emptyList()
)

/** Displayable metadata of one movie, from the lazy `get_vod_info` call. */
data class MovieInfo(
    /** 0 means unknown. */
    val year: Int,
    /** 0 means unknown. */
    val durationSec: Int,
    /** 0-10; 0.0 means unknown. */
    val rating10: Double,
    val genres: List<GenreLabel>,
    val director: String?,
    /** At most 8 names. */
    val cast: List<String>,
    val plot: String?,
    val posterUrl: String?
) {
    val isEmpty: Boolean
        get() = year == 0 && durationSec == 0 && rating10 <= 0.0 && genres.isEmpty() &&
            director == null && cast.isEmpty() && plot == null
}

private const val MAX_GENRE_ENTRIES = 20_000
private const val MAX_GENRE_CHARS = 200
private val GENRE_SPACES = Regex("""\s+""")

/**
 * Assigns each distinct *set of genres* a table index. Entries are keyed by the
 * sorted canonical keys of their labels, so `"Drama, Crime"`, `"Crime /  Drama"`
 * and `"crimen; drama"` share one entry instead of three (TMDB-style panels send
 * thousands of orderings). A string that yields no usable label (junk, digits,
 * too long) degrades to -1 (unknown); the table is a safety cap only.
 */
private class GenreTable {
    private val byRaw = HashMap<String, Int>()
    private val bySignature = HashMap<String, Int>()
    val entries = ArrayList<String>()

    fun refOf(raw: String?): Int {
        val clean = raw?.trim()?.replace(GENRE_SPACES, " ")?.takeIf { it.isNotEmpty() } ?: return -1
        if (clean.length > MAX_GENRE_CHARS) return -1
        byRaw[clean]?.let { return it }
        val labels = GenreNormalizer.labels(clean)
        if (labels.isEmpty()) return -1
        val signature = labels.map {
            when (it) {
                is GenreLabel.Known -> "c:" + it.genre.name
                is GenreLabel.Other -> "o:" + GenreNormalizer.foldKey(it.label)
            }
        }.sorted().joinToString("|")
        val ref = bySignature[signature] ?: run {
            if (entries.size >= MAX_GENRE_ENTRIES) return -1
            entries += clean
            (entries.size - 1).also { bySignature[signature] = it }
        }
        byRaw[clean] = ref
        return ref
    }
}

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
 * - series use the stable `series:<series_id>` key;
 * - artwork goes through [normalizeArtworkUrl] (PHP-serialized and relative
 *   paths are resolved against [serverBase], junk becomes `null`).
 */
fun buildVodCatalog(
    api: XtreamApiCatalog,
    matcher: XtreamStreamMatcher,
    fallbackUrl: (type: String, id: String) -> String?,
    serverBase: String? = null,
    nowEpochSec: Long = System.currentTimeMillis() / 1000,
    currentYear: Int = currentYearUtc(nowEpochSec)
): VodCatalog {
    val movieGenres = GenreTable()
    val seriesGenres = GenreTable()
    val dayFirst = dev.lluissunol.iptv.data.detectDayFirst(api.vodStreams.asSequence().map { it.added })
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
            posterUrl = normalizeArtworkUrl(dto.streamIcon, serverBase),
            categoryId = dto.categoryId,
            rating = normalizeRating10(dto.rating, dto.rating5Based),
            searchKey = SearchNormalizer.key(name),
            favoriteKey = matched?.favoriteKey ?: urlFavoriteKey(url),
            playbackUrl = url,
            qualityTag = matched?.qualityTag ?: qualityTagOf(name),
            added = parseEpochSeconds(dto.added, nowEpochSec, dayFirst),
            releaseKey = firstReleaseKey(dto.releaseRaws(), currentYear),
            genreRef = movieGenres.refOf(dto.genre)
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
            posterUrl = normalizeArtworkUrl(dto.cover, serverBase),
            plot = dto.plot?.trim()?.takeIf { it.isNotEmpty() },
            categoryId = dto.categoryId,
            rating = normalizeRating10(dto.rating, dto.rating5Based),
            searchKey = SearchNormalizer.key(name),
            favoriteKey = seriesFavoriteKey(dto.seriesId),
            qualityTag = qualityTagOf(name),
            releaseKey = firstReleaseKey(dto.releaseRaws(), currentYear),
            runtimeMin = parseRuntimeMinutes(dto.episodeRunTime),
            genreRef = seriesGenres.refOf(dto.genre)
        )
    }

    return VodCatalog(
        movies = movies,
        series = series,
        movieCategories = categories(api.vodCategories),
        seriesCategories = categories(api.seriesCategories),
        schemaVersion = CATALOG_SCHEMA_VERSION,
        movieGenres = movieGenres.entries,
        seriesGenres = seriesGenres.entries
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
        ?.let { catalog ->
            if (catalog.schemaVersion >= CATALOG_SCHEMA_VERSION) {
                catalog
            } else {
                // An old cache kept raw ratings (a percentage reads "★ 85" and passes
                // "Nota ≥ 7"); clamp them to 0-10 until a refresh rebuilds it.
                catalog.copy(
                    movies = catalog.movies.map { it.copy(rating = normalizeRating10(it.rating, null)) },
                    series = catalog.series.map { it.copy(rating = normalizeRating10(it.rating, null)) }
                )
            }
        }
