package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.Episode
import dev.lluissunol.iptv.data.model.MovieInfo
import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SeriesInfo
import dev.lluissunol.iptv.data.model.Season
import dev.lluissunol.iptv.data.model.buildXtreamApiUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Xtream `player_api.php` client. Fetches **metadata only** (names, posters,
 * plots, ratings, categories and, lazily, seasons/episodes); playback URLs
 * always come from the parsed M3U via [XtreamStreamMatcher] with a conservative
 * `.m3u8` fallback.
 *
 * The API responses are untrusted input: every field is read tolerantly, a bad
 * entry is skipped with a warning instead of aborting the catalogue, and the
 * request URLs (which embed the credentials in the query) are never logged.
 */
class XtreamClient(private val client: OkHttpClient) {

    /**
     * Fetches the four catalogue endpoints sequentially. Transport/HTTP
     * failures throw [PlaylistException]; per-item problems are skipped into
     * [XtreamApiCatalog.warnings] instead.
     */
    suspend fun fetchCatalog(config: PlaylistConfig): XtreamApiCatalog =
        withContext(Dispatchers.IO) {
            if (config.kind != PlaylistKind.XTREAM) {
                throw PlaylistException.Unknown(IllegalStateException("not an Xtream list"))
            }
            val warnings = ArrayList<CatalogWarning>(4)
            val vodStreams = get(config, ACTION_VOD_STREAMS) { body ->
                decodeVodStreams(body, warnings)
            }
            val seriesList = get(config, ACTION_SERIES) { body ->
                decodeSeriesList(body, warnings)
            }
            val vodCategories = get(config, ACTION_VOD_CATEGORIES) { body ->
                decodeCategories(body, warnings)
            }
            val seriesCategories = get(config, ACTION_SERIES_CATEGORIES) { body ->
                decodeCategories(body, warnings)
            }
            XtreamApiCatalog(
                vodStreams = vodStreams,
                seriesList = seriesList,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories,
                warnings = warnings
            )
        }

    /** Lazy per-series detail call. Throws [PlaylistException] on failure. */
    suspend fun fetchSeriesInfo(config: PlaylistConfig, seriesId: Long): SeriesInfoDto =
        withContext(Dispatchers.IO) {
            get(config, "$ACTION_SERIES_INFO&series_id=$seriesId") { body ->
                decodeSeriesInfo(body)
            }
        }

    /**
     * Lazy per-movie detail call (`get_vod_info`). It only feeds the detail
     * screen: nothing in the answer is ever used to build a playback URL.
     * Throws [PlaylistException] on failure.
     */
    suspend fun fetchVodInfo(config: PlaylistConfig, vodId: Long): VodInfoDto =
        withContext(Dispatchers.IO) {
            get(config, "$ACTION_VOD_INFO&vod_id=$vodId") { body -> decodeVodInfo(body) }
        }

    private inline fun <T> get(
        config: PlaylistConfig,
        action: String,
        decode: (body: String) -> T
    ): T {
        val url = buildXtreamApiUrl(config.server, config.username, config.password, action)
            ?: throw PlaylistException.InvalidUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PlaylistRepository.USER_AGENT)
            .header("Accept", "application/json, */*")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlaylistException.Http(response.code)
                val contentType = response.header("Content-Type")
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    throw PlaylistException.Html()
                }
                val body = response.body?.string()
                    ?: throw PlaylistException.Unknown(IOException("Response without body"))
                val head = body.trimStart().take(16).lowercase()
                if (head.startsWith("<!doctype") || head.startsWith("<html")) {
                    throw PlaylistException.Html()
                }
                return decode(body)
            }
        } catch (e: PlaylistException) {
            throw e
        } catch (e: IOException) {
            throw PlaylistException.Network(e)
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (e: Exception) {
            throw PlaylistException.Unknown(e)
        }
    }

    companion object {
        const val ACTION_VOD_STREAMS = "get_vod_streams"
        const val ACTION_SERIES = "get_series"
        const val ACTION_VOD_CATEGORIES = "get_vod_categories"
        const val ACTION_SERIES_CATEGORIES = "get_series_categories"
        const val ACTION_SERIES_INFO = "get_series_info"
        const val ACTION_VOD_INFO = "get_vod_info"
    }
}

/** The four decoded metadata endpoints of one list, before merging with the M3U. */
data class XtreamApiCatalog(
    val vodStreams: List<VodStreamDto>,
    val seriesList: List<SeriesDto>,
    val vodCategories: List<CategoryDto>,
    val seriesCategories: List<CategoryDto>,
    /** Decode problems, typed: their text lives in `strings.xml`. */
    val warnings: List<CatalogWarning>
)

// ---------------------------------------------------------------- JSON model

@Serializable
data class CategoryDto(
    @SerialName("category_id")
    @Serializable(with = FlexStringSerializer::class)
    val categoryId: String = "",
    @SerialName("category_name")
    @Serializable(with = FlexStringSerializer::class)
    val categoryName: String = ""
)

@Serializable
data class VodStreamDto(
    @Serializable(with = FlexStringSerializer::class)
    val name: String = "",
    @SerialName("stream_id")
    @Serializable(with = FlexLongSerializer::class)
    val streamId: Long = 0L,
    @SerialName("stream_icon")
    @Serializable(with = FlexStringSerializer::class)
    val streamIcon: String = "",
    @SerialName("category_id")
    @Serializable(with = FlexStringSerializer::class)
    val categoryId: String = "",
    /** Retained for diagnostics only; never used to build a playback URL. */
    @SerialName("container_extension")
    val containerExtension: String? = null,
    @Serializable(with = FlexDoubleSerializer::class)
    val rating: Double = 0.0,
    @SerialName("rating_5based")
    @Serializable(with = FlexTextSerializer::class)
    val rating5Based: String? = null,
    /** Epoch seconds/ms or a date string, depending on the panel. */
    @Serializable(with = FlexTextSerializer::class)
    val added: String? = null,
    // The release date has no standard name: each alias is its own property
    // (never `@JsonNames`) and [releaseRaws] takes the first known value.
    @Serializable(with = FlexTextSerializer::class)
    val releasedate: String? = null,
    @SerialName("release_date")
    @Serializable(with = FlexTextSerializer::class)
    val releaseDateSnake: String? = null,
    @SerialName("releaseDate")
    @Serializable(with = FlexTextSerializer::class)
    val releaseDateCamel: String? = null,
    @Serializable(with = FlexTextSerializer::class)
    val year: String? = null,
    @Serializable(with = FlexTextSerializer::class)
    val genre: String? = null
) {
    fun releaseRaws(): List<String?> = listOf(releasedate, releaseDateSnake, releaseDateCamel, year)
}

@Serializable
data class SeriesDto(
    @Serializable(with = FlexStringSerializer::class)
    val name: String = "",
    @SerialName("series_id")
    @Serializable(with = FlexLongSerializer::class)
    val seriesId: Long = 0L,
    @Serializable(with = FlexStringSerializer::class)
    val cover: String = "",
    @Serializable(with = FlexStringSerializer::class)
    val plot: String = "",
    @SerialName("category_id")
    @Serializable(with = FlexStringSerializer::class)
    val categoryId: String = "",
    @Serializable(with = FlexDoubleSerializer::class)
    val rating: Double = 0.0,
    @SerialName("rating_5based")
    @Serializable(with = FlexTextSerializer::class)
    val rating5Based: String? = null,
    @SerialName("releaseDate")
    @Serializable(with = FlexTextSerializer::class)
    val releaseDateCamel: String? = null,
    @SerialName("release_date")
    @Serializable(with = FlexTextSerializer::class)
    val releaseDateSnake: String? = null,
    @Serializable(with = FlexTextSerializer::class)
    val releasedate: String? = null,
    @Serializable(with = FlexTextSerializer::class)
    val year: String? = null,
    @Serializable(with = FlexTextSerializer::class)
    val genre: String? = null,
    @SerialName("episode_run_time")
    @Serializable(with = FlexTextSerializer::class)
    val episodeRunTime: String? = null
) {
    fun releaseRaws(): List<String?> = listOf(releaseDateCamel, releaseDateSnake, releasedate, year)
}

@Serializable
data class EpisodeDto(
    @Serializable(with = FlexStringSerializer::class)
    val id: String = "",
    @SerialName("episode_num")
    @Serializable(with = FlexLongSerializer::class)
    val episodeNum: Long = 0L,
    @Serializable(with = FlexStringSerializer::class)
    val title: String = "",
    /** Optional on purpose: some providers omit it, and we never use it. */
    @SerialName("container_extension")
    val containerExtension: String? = null,
    /** Raw `info` payload (object or JSON string); only its release date is read. */
    val info: JsonElement? = null
)

/**
 * `get_vod_info` root. `movie_data` is deliberately NOT declared: it carries
 * the provider's own stream name/extension, which must never reach playback.
 */
@Serializable
data class VodInfoDto(
    val info: JsonElement? = null
)

/**
 * `get_series_info` root. The three payloads vary wildly between Xtream forks
 * (`info` can even be a JSON-encoded *string*), so they are kept as raw
 * [JsonElement]s and interpreted by [toSeriesInfo].
 */
@Serializable
data class SeriesInfoDto(
    val info: JsonElement? = null,
    val seasons: JsonElement? = null,
    val episodes: JsonElement? = null
)

// --------------------------------------------------------------- decoding API

private val xtreamJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal fun decodeVodStreams(
    body: String,
    warnings: MutableList<CatalogWarning>
): List<VodStreamDto> = decodeItemArray(body, warnings, VodStreamDto.serializer())

internal fun decodeSeriesList(
    body: String,
    warnings: MutableList<CatalogWarning>
): List<SeriesDto> = decodeItemArray(body, warnings, SeriesDto.serializer())

internal fun decodeCategories(
    body: String,
    warnings: MutableList<CatalogWarning>
): List<CategoryDto> = decodeItemArray(body, warnings, CategoryDto.serializer())

/**
 * Decodes one of the array-shaped endpoints. Anything that is not a JSON
 * array of objects degrades to an empty list plus a warning (several forks
 * answer with a `user_info` object instead of an array); a single bad element
 * is skipped, never the whole catalogue.
 */
private fun <T> decodeItemArray(
    body: String,
    warnings: MutableList<CatalogWarning>,
    serializer: KSerializer<T>
): List<T> {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) {
        warnings += CatalogWarning.EmptyResponse
        return emptyList()
    }
    val root = runCatching { xtreamJson.parseToJsonElement(trimmed) }.getOrElse {
        warnings += CatalogWarning.InvalidJson
        return emptyList()
    }
    val array = root as? JsonArray ?: run {
        // Not an array: usually an auth/user_info object or a fork's error shape.
        warnings += CatalogWarning.UnexpectedFormat
        return emptyList()
    }
    var skipped = 0
    val items = ArrayList<T>(array.size)
    for (element in array) {
        val item = runCatching {
            xtreamJson.decodeFromJsonElement(serializer, element)
        }.getOrNull()
        if (item == null) skipped++ else items += item
    }
    if (skipped > 0) {
        warnings += CatalogWarning.SkippedItems(skipped)
    }
    return items
}

/** Decodes the `get_series_info` object; failures throw [PlaylistException.Malformed]. */
internal fun decodeSeriesInfo(body: String): SeriesInfoDto {
    val root = runCatching { xtreamJson.parseToJsonElement(body.trim()) }
        .getOrElse { throw PlaylistException.Malformed(it) }
    val obj = root as? JsonObject ?: throw PlaylistException.Malformed(null)
    return runCatching { xtreamJson.decodeFromJsonElement(SeriesInfoDto.serializer(), obj) }
        .getOrElse { throw PlaylistException.Malformed(it) }
}

/** Decodes `get_vod_info`; a root that is neither an object, `[]` nor `null` is [PlaylistException.Malformed]. */
internal fun decodeVodInfo(body: String): VodInfoDto {
    val root = runCatching { xtreamJson.parseToJsonElement(body.trim()) }
        .getOrElse { throw PlaylistException.Malformed(it) }
    // `[]` and `null` are what some forks answer for a deleted id: the "empty" state.
    if (root is JsonArray || root is JsonNull) return VodInfoDto(null)
    val obj = root as? JsonObject ?: throw PlaylistException.Malformed(null)
    return runCatching { xtreamJson.decodeFromJsonElement(VodInfoDto.serializer(), obj) }
        .getOrElse { throw PlaylistException.Malformed(it) }
}

/**
 * The displayable metadata of a movie. `info` may be an object, `[]`, a JSON
 * string, `null` or `{}` depending on the panel fork; every field accepts the
 * aliases the forks use and a bad value is simply unknown.
 */
internal fun VodInfoDto.toMovieInfo(
    serverBase: String? = null,
    currentYear: Int = currentYearUtc()
): MovieInfo {
    val obj = infoObjectOf(info)
    return MovieInfo(
        year = releaseYear(
            firstReleaseKey(
                listOf("releasedate", "release_date", "releaseDate", "year").map { obj?.text(it) },
                currentYear
            )
        ),
        durationSec = parseDurationSeconds(obj?.text("duration_secs"), obj?.text("duration")),
        rating10 = normalizeRating10(parseRatingText(obj?.text("rating")), obj?.text("rating_5based")),
        genres = GenreNormalizer.labels(obj?.text("genre")),
        director = obj?.text("director")?.cleanLine()?.takeIf { it.isNotEmpty() },
        cast = (obj?.text("cast") ?: obj?.text("actors"))
            ?.split(',', '/', '|')
            ?.map { it.cleanLine() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.take(MAX_CAST)
            .orEmpty(),
        plot = (obj?.text("plot") ?: obj?.text("description"))?.trim()?.takeIf { it.isNotEmpty() },
        posterUrl = normalizeArtworkUrl(obj?.text("movie_image") ?: obj?.text("cover_big"), serverBase)
    )
}

private const val MAX_CAST = 8

private val INNER_WHITESPACE = Regex("""\s+""")

private fun String.cleanLine(): String = trim().replace(INNER_WHITESPACE, " ")

/**
 * Builds the lean [SeriesInfo] from a decoded [SeriesInfoDto]:
 * - groups episodes by the season keys of the `episodes` object;
 * - each episode resolves its playback URL from the M3U matcher, falling back
 *   to the provider's own `.m3u8` pattern when unmatched;
 * - an episode without a usable (digit-bearing) id, or without any resolvable
 *   URL, is skipped;
 * - season keys that normalise to the same number (`"1"` and `"01"`) are
 *   merged into one season, so a duplicate season number never reaches the
 *   drill-down: its season pills key a LazyRow on the number, and a repeat
 *   would throw a duplicate-key crash. No episode is dropped by the merge;
 * - each episode gets an id unique inside its season (a provider repeating an
 *   id gets a `#2`, `#3`, ... suffix), because the episode list keys its rows
 *   on it;
 * - when there are no episode groups, season pills are derived from the
 *   `seasons` array (a repeated number is skipped: first occurrence wins),
 *   which may itself be absent (a series with no seasons).
 */
internal fun SeriesInfoDto.toSeriesInfo(
    seriesId: Long,
    defaultName: String,
    matcher: XtreamStreamMatcher?,
    fallbackUrl: (type: String, id: String) -> String?,
    serverBase: String? = null,
    currentYear: Int = currentYearUtc()
): SeriesInfo {
    val info = infoObject()
    val name = info?.text("name")?.takeIf { it.isNotBlank() }
        ?: defaultName.takeIf { it.isNotBlank() }
        ?: UNNAMED_SERIES

    // Insertion-ordered so "first occurrence wins" is deterministic: the
    // first season key that normalises to a number owns that season, and any
    // later duplicate key only appends its episodes to it.
    val episodesBySeason = LinkedHashMap<Int, ArrayList<Episode>>(8)
    val usedEpisodeIds = HashMap<Int, HashSet<String>>(8)
    (episodes as? JsonObject)?.forEach { (seasonKey, seasonValue) ->
        val seasonNumber = XtreamStreamMatcher.digitsOnly(seasonKey).toIntOrNull()
            ?: seasonKey.toIntOrNull()
            ?: return@forEach
        val episodeArray = seasonValue as? JsonArray ?: return@forEach
        val episodes = episodesBySeason.getOrPut(seasonNumber) { ArrayList(episodeArray.size) }
        val usedIds = usedEpisodeIds.getOrPut(seasonNumber) { HashSet() }
        for (element in episodeArray) {
            decodeEpisode(element, name, seasonNumber, matcher, fallbackUrl, usedIds, currentYear)
                ?.let { episodes += it }
        }
    }
    val seasons = ArrayList<Season>(episodesBySeason.size)
    for ((number, episodes) in episodesBySeason) {
        if (episodes.isNotEmpty()) seasons += Season(number, episodes)
    }
    if (seasons.isEmpty()) {
        // Some forks only send the `seasons` array; keep the pills even when
        // the episode groups are missing or empty. A repeated number is
        // skipped: the pills key on it and a duplicate would crash them.
        val seenNumbers = HashSet<Int>(8)
        (this.seasons as? JsonArray)?.forEach { element ->
            val number = (element as? JsonObject)
                ?.get("season")
                ?.let { season -> (season as? JsonPrimitive)?.longOrNull }
                ?.toInt()
            if (number != null && seenNumbers.add(number)) {
                seasons += Season(number, emptyList())
            }
        }
    }

    // Broadcast years come ONLY from the episodes' own release dates. `added`
    // and `last_modified` are bulk-import stamps on many panels, so a 1990s
    // show would read "2023": no date at all beats a false one.
    var firstYear = 0
    var lastYear = 0
    for (season in seasons) for (episode in season.episodes) {
        val year = releaseYear(episode.releaseKey)
        if (year == 0) continue
        if (firstYear == 0 || year < firstYear) firstYear = year
        if (year > lastYear) lastYear = year
    }

    return SeriesInfo(
        seriesId = seriesId,
        name = name,
        plot = info?.text("plot"),
        posterUrl = normalizeArtworkUrl(info?.text("cover"), serverBase),
        seasons = seasons.sortedBy { it.number },
        firstYear = firstYear,
        lastYear = lastYear
    )
}

private fun decodeEpisode(
    element: JsonElement,
    seriesName: String,
    seasonNumber: Int,
    matcher: XtreamStreamMatcher?,
    fallbackUrl: (type: String, id: String) -> String?,
    usedIds: MutableSet<String>,
    currentYear: Int
): Episode? {
    if (element !is JsonObject) return null
    val dto = runCatching {
        xtreamJson.decodeFromJsonElement(EpisodeDto.serializer(), element)
    }.getOrNull() ?: return null
    val idDigits = XtreamStreamMatcher.digitsOnly(dto.id)
    if (idDigits.isEmpty()) return null
    val matched = matcher?.seriesEpisode(idDigits)
    val url = matched?.url ?: fallbackUrl(XtreamStreamMatcher.TYPE_SERIES, idDigits) ?: return null
    return Episode(
        id = uniqueEpisodeId(dto.id, usedIds),
        season = seasonNumber,
        episodeNum = dto.episodeNum.toInt(),
        title = cleanEpisodeTitle(dto.title, seriesName, dto.episodeNum.toInt()),
        playbackUrl = url,
        favoriteKey = matched?.favoriteKey ?: dev.lluissunol.iptv.data.model.urlFavoriteKey(url),
        releaseKey = firstReleaseKey(
            infoObjectOf(dto.info)?.let { info ->
                listOf("releaseDate", "release_date", "releasedate", "air_date").map { info.text(it) }
            }.orEmpty(),
            currentYear
        )
    )
}

/**
 * A row id unique inside one season: the raw API id as the provider sent it,
 * or the id plus a `#2`, `#3`, ... suffix when the provider repeats it. The
 * drill-down keys its episode rows on [Episode.id], so a repeat would throw a
 * duplicate-key crash; the suffixed id is never used to resolve a URL (the
 * raw id's digits already did that before this runs).
 */
/**
 * Separator characters a provider puts between the series name, the episode
 * code and the episode title (`"White Rabbit Project - S01E01 - Heist!"`).
 */
private const val TITLE_SEPARATORS = "-–—:.|"

/**
 * The episode code inside a title: `S01E01`, `s1e1`, `1x01`, `01X01`, with the
 * separators the provider pads it with on either side.
 */
private val EPISODE_CODE = Regex(
    """(?i)\s*[-–—:.|]?\s*(?:\bS\d{1,3}\s*[-_.]?\s*E\d{1,4}\b|\b\d{1,3}\s*[xX]\s*\d{1,4}\b)\s*[-–—:.|]?\s*"""
)

private val WHITESPACE_RUN = Regex("""\s{2,}""")

/**
 * Reduces a provider title to the episode name alone.
 *
 * Many panels send the whole line the M3U's `tvg-name` also carries,
 * `"<series> - S01E01 - <title>"`, which is three times the information the
 * episode row already shows (the header carries the series, the row carries the
 * season and the episode number) and reads badly in the player's top bar.
 *
 * Only a *leading* series name and the episode code are removed; a title that
 * legitimately contains a dash or a colon keeps it. An empty result degrades to
 * `"Episodio N"` so a row never renders blank.
 */
internal fun cleanEpisodeTitle(raw: String, seriesName: String?, episodeNum: Int): String {
    val fallback = "Episodio $episodeNum"
    // The code is first normalised to a plain separator, so every shape the
    // panels use — `"<series> - S01E01 - X"`, `"<series> 1x01 - X"`,
    // `"S01E01 - X"` — collapses to `"<series> - X"` before the single rule
    // below runs. Replacing with a space instead would glue the series name to
    // the episode title and defeat the prefix test.
    var title = EPISODE_CODE.replace(raw.trim(), " - ").trimSeparators()
    if (title.isEmpty()) return fallback

    // The separator is required after the name, so a title that merely starts
    // with the same letters ("White Rabbit Project" vs "...Projection") is left
    // alone. A title that is nothing but the series name is not an episode
    // name either, so it falls through to the fallback.
    val name = seriesName?.trim().orEmpty()
    if (name.isNotEmpty() && title.length >= name.length &&
        title.regionMatches(0, name, 0, name.length, ignoreCase = true)
    ) {
        val rest = title.substring(name.length).trimStart()
        title = when {
            rest.isEmpty() -> ""
            rest[0] in TITLE_SEPARATORS -> rest.trimSeparators()
            else -> title
        }
    }

    title = WHITESPACE_RUN.replace(title, " ").trimSeparators()
    return title.ifEmpty { fallback }
}

/** Trims whitespace and title separators from both ends. */
private fun String.trimSeparators(): String = trim { it.isWhitespace() || it in TITLE_SEPARATORS }

private fun uniqueEpisodeId(rawId: String, used: MutableSet<String>): String {
    if (used.add(rawId)) return rawId
    var suffix = 2
    while (!used.add("$rawId#$suffix")) suffix++
    return "$rawId#$suffix"
}

/** The `info` payload: an object, or a JSON-encoded string (a known fork quirk). */
private fun SeriesInfoDto.infoObject(): JsonObject? = infoObjectOf(info)

private fun infoObjectOf(element: JsonElement?): JsonObject? {
    return when (element) {
        is JsonObject -> element
        is JsonPrimitive -> {
            if (element is JsonNull) return null
            runCatching { xtreamJson.parseToJsonElement(element.content) as? JsonObject }
                .getOrNull()
        }
        else -> null
    }
}

private fun JsonObject.text(key: String): String? = this[key].asFlexText()

/**
 * A JSON value read as text, tolerating every shape the forks send: a
 * primitive gives its content, an array of primitives is joined with `/`, an
 * object or `null` is unknown. Blank values are unknown too.
 */
internal fun JsonElement?.asFlexText(): String? {
    val text = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        is JsonArray -> mapNotNull { element ->
            (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        }.filter { it.isNotBlank() }.joinToString("/")
        else -> null
    }
    return text?.takeIf { it.isNotBlank() }
}

// ------------------------------------------------- tolerant JSON primitives

/**
 * The Xtream forks encode numbers as numbers, strings, or both. These
 * serializers accept any JSON primitive and fall back to the field default
 * (via [Json.coerceInputValues] for JSON nulls), which keeps one weird field
 * from killing a whole catalogue item.
 */
object FlexLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("FlexLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Long {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("FlexLong expects JSON input")
        val primitive = json.decodeJsonElement() as? JsonPrimitive
        return primitive?.longOrNull ?: primitive?.doubleOrNull?.toLong() ?: 0L
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Long
    ) = encoder.encodeLong(value)
}

object FlexDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Double {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("FlexDouble expects JSON input")
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return 0.0
        if (primitive is JsonNull) return 0.0
        // `"7,4"` is a real shape (decimal comma); anything else is unknown.
        val value = primitive.doubleOrNull
            ?: primitive.content.trim().replace(',', '.').toDoubleOrNull()
            ?: return 0.0
        return if (value.isFinite()) value else 0.0
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Double
    ) = encoder.encodeDouble(value)
}

object FlexStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("FlexString expects JSON input")
        val element = json.decodeJsonElement()
        if (element is JsonNull) return ""
        return (element as? JsonPrimitive)?.content ?: ""
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: String
    ) = encoder.encodeString(value)
}

/**
 * A text field of any JSON shape (see [asFlexText]). Declared nullable so an
 * absent field and an unusable one are both "unknown".
 */
object FlexTextSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexText", PrimitiveKind.STRING)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String? {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("FlexText expects JSON input")
        return json.decodeJsonElement().asFlexText()
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: String?
    ) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
