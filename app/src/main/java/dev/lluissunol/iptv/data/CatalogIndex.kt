package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.VodCatalog
import kotlin.math.roundToLong

/** What a catalogue row exposes to sorting and filtering (movies and series). */
interface CatalogEntry {
    val name: String
    val categoryId: String
    val searchKey: String
    /** Normalised 0-10 rating; 0.0 means unknown. */
    val rating: Double
    /** Index into the catalogue's raw genre table; -1 means unknown. */
    val genreRef: Int
    /** `yyyymmdd` / `yyyy0000`; 0 means unknown. */
    val releaseKey: Int
}

/** The orders a catalogue tab can offer, in the order the menu lists them. */
enum class CatalogSort {
    PROVIDER, RECENT, RELEASE, RATING, AZ;

    /**
     * True when the natural direction of this sort runs from the smallest to the
     * largest value (A-Z). Ratings and dates run best/newest first, so they
     * are "descending" by default.
     */
    val naturallyAscending: Boolean get() = this == AZ

    companion object {
        /** Suffix of a stored name whose direction is reversed (`RATING:REV`). */
        private const val REVERSED_SUFFIX = ":REV"

        /** Parses a stored name, with or without the reversed suffix. */
        fun fromName(name: String?): CatalogSort? {
            val base = name?.removeSuffix(REVERSED_SUFFIX)
            return entries.firstOrNull { it.name == base }
        }

        /** True when [name] carries the reversed suffix. */
        fun isReversedName(name: String?): Boolean = name?.endsWith(REVERSED_SUFFIX) == true

        /**
         * The string saved for [sort]: its name, plus the reversed suffix. The
         * provider order has no direction, so it never carries one.
         */
        fun storedName(sort: CatalogSort, reversed: Boolean): String =
            if (reversed && sort != PROVIDER) sort.name + REVERSED_SUFFIX else sort.name
    }
}

/** A sort is offered only when at least this share of the titles carries the value. */
const val MIN_COVERAGE = 0.50

/** ... and the values are not all the same. */
const val MIN_DISTINCT = 2

/** ... and no single value covers this share of the titles that carry it. */
const val MAX_DOMINANT_SHARE = 0.95

/** The genre sheet shows at most this many genres (the most frequent). */
const val MAX_GENRE_OPTIONS = 40

/** One selectable genre. [key] is `c:<CANONICAL>` or `o:<fold of the provider label>`. */
data class GenreOption(val key: String, val label: GenreLabel, val titles: Int)

/**
 * The genre filter of one tab. [options] hold the [MAX_GENRE_OPTIONS] most
 * frequent genres, most frequent first; the UI sorts them by their Spanish
 * label, which only it can resolve.
 */
class GenreFacet(
    val options: List<GenreOption>,
    val capped: Boolean,
    private val refKeys: List<Set<String>>
) {
    /** Per raw-genre-table entry: does it carry [key]? */
    fun refMatches(key: String): BooleanArray = BooleanArray(refKeys.size) { key in refKeys[it] }
}

/**
 * Everything derived for one tab (Movies or Series). It is a pure function of
 * the catalogue, built once on `Dispatchers.Default` and never serialized.
 */
class TabIndex(
    val size: Int,
    /** Available sorts in menu order; always starts with PROVIDER. */
    val sorts: List<CatalogSort>,
    /** True when a sort that applies to this tab was hidden for lack of data. */
    val sortsHidden: Boolean,
    val language: LanguageFacet?,
    val genres: GenreFacet?,
    val ratingFilter: Boolean,
    /** True when at least one of the three filters is unavailable. */
    val filtersHidden: Boolean,
    internal val permutations: Map<CatalogSort, IntArray>,
    /**
     * Per sort: how many titles at the front of its permutation carry a value.
     * The rest (unknown) always stay last, also when the direction is reversed.
     */
    internal val knownCounts: Map<CatalogSort, Int> = emptyMap()
) {
    val hasFilters: Boolean get() = language != null || genres != null || ratingFilter
}

/** The derived data of one catalogue. */
class CatalogIndex(
    val catalog: VodCatalog,
    val movies: TabIndex,
    val series: TabIndex
) {
    /** Cached by an older build: it lacks the metadata the new controls need. */
    val outdated: Boolean get() = catalog.schemaVersion < CATALOG_SCHEMA_VERSION
}

/** Builds the per-tab capabilities and sort permutations of [catalog]. */
fun buildCatalogIndex(catalog: VodCatalog): CatalogIndex {
    val movies = buildTabIndex(
        entries = catalog.movies,
        categories = catalog.movieCategories,
        genreTable = catalog.movieGenres,
        isMovies = true,
        addedOf = { catalog.movies[it].added }
    )
    val series = buildTabIndex(
        entries = catalog.series,
        categories = catalog.seriesCategories,
        genreTable = catalog.seriesGenres,
        isMovies = false,
        addedOf = null
    )
    return CatalogIndex(catalog, movies, series)
}

/**
 * Coverage, distinctness and dominance are all counted in buckets (a day, a
 * year, a tenth of a point): raw `added` seconds are almost all distinct even
 * after a bulk import, which would make the check pass for nothing.
 */
internal fun qualifies(known: Int, total: Int, bucketCounts: Map<Long, Int>): Boolean {
    if (total <= 0 || known <= 0) return false
    if (known.toDouble() / total < MIN_COVERAGE) return false
    if (bucketCounts.size < MIN_DISTINCT) return false
    val dominant = bucketCounts.values.max()
    return dominant.toDouble() / known < MAX_DOMINANT_SHARE
}

private inline fun bucketStats(n: Int, value: (Int) -> Long, bucket: (Long) -> Long): Pair<Int, Map<Long, Int>> {
    var known = 0
    val counts = HashMap<Long, Int>()
    for (i in 0 until n) {
        val v = value(i)
        if (v <= 0L) continue
        known++
        counts.merge(bucket(v), 1, Int::plus)
    }
    return known to counts
}

private fun ratingValue(rating: Double): Long = (rating * 10).roundToLong()

private fun buildTabIndex(
    entries: List<CatalogEntry>,
    categories: List<dev.lluissunol.iptv.data.model.VodCategory>,
    genreTable: List<String>,
    isMovies: Boolean,
    addedOf: ((Int) -> Long)?
): TabIndex {
    val n = entries.size
    val titlesPerCategory = HashMap<String, Int>()
    for (entry in entries) titlesPerCategory.merge(entry.categoryId, 1, Int::plus)

    val recentOk = isMovies && addedOf != null && bucketStats(n, addedOf) { it / 86_400L }
        .let { (known, counts) -> qualifies(known, n, counts) }
    val releaseOk = bucketStats(n, { entries[it].releaseKey.toLong() }) { it / 10_000L }
        .let { (known, counts) -> qualifies(known, n, counts) }
    val ratingOk = bucketStats(n, { ratingValue(entries[it].rating) }) { it }
        .let { (known, counts) -> qualifies(known, n, counts) }

    val sorts = ArrayList<CatalogSort>(5)
    sorts += CatalogSort.PROVIDER
    val permutations = HashMap<CatalogSort, IntArray>()
    val knownCounts = HashMap<CatalogSort, Int>()
    if (n >= 2) {
        if (recentOk) {
            sorts += CatalogSort.RECENT
            permutations[CatalogSort.RECENT] = descendingPermutation(n) { addedOf!!(it) }
            knownCounts[CatalogSort.RECENT] = (0 until n).count { addedOf!!(it) > 0L }
        }
        if (releaseOk) {
            sorts += CatalogSort.RELEASE
            permutations[CatalogSort.RELEASE] = descendingPermutation(n) { entries[it].releaseKey.toLong() }
            knownCounts[CatalogSort.RELEASE] = entries.count { it.releaseKey > 0 }
        }
        if (ratingOk) {
            sorts += CatalogSort.RATING
            permutations[CatalogSort.RATING] = descendingPermutation(n) { ratingValue(entries[it].rating) }
            knownCounts[CatalogSort.RATING] = entries.count { ratingValue(it.rating) > 0L }
        }
        sorts += CatalogSort.AZ
        permutations[CatalogSort.AZ] = alphabeticalPermutation(entries)
        // Titles without any letter sort last in A-Z and stay last when reversed.
        knownCounts[CatalogSort.AZ] = entries.count { alphabeticalGroup(alphabeticalKey(it.name)) < 2 }
    }
    val sortsHidden = n >= 2 && ((isMovies && !recentOk) || !releaseOk || !ratingOk)

    val ratingFilter = hasRatingFilter(entries)
    val language = detectLanguageFacet(categories, titlesPerCategory, n)
    val genres = buildGenreFacet(entries, genreTable)
    return TabIndex(
        size = n,
        sorts = sorts,
        sortsHidden = sortsHidden,
        language = language,
        genres = genres,
        ratingFilter = ratingFilter,
        filtersHidden = n >= 2 && (language == null || genres == null || !ratingFilter),
        permutations = permutations,
        knownCounts = knownCounts
    )
}

/** Nota ≥ 7 needs ≥ half the titles rated, with at least one on each side of 7. */
private fun hasRatingFilter(entries: List<CatalogEntry>): Boolean {
    var rated = 0
    var high = 0
    for (entry in entries) {
        if (entry.rating <= 0.0) continue
        rated++
        if (entry.rating >= MIN_RATING_FILTER) high++
    }
    return entries.isNotEmpty() && rated.toDouble() / entries.size >= MIN_COVERAGE &&
        high >= 1 && rated - high >= 1
}

/** The "Nota ≥ 7" threshold. */
const val MIN_RATING_FILTER = 7.0

private const val RANK_CAP = 2_100_000_000L

/**
 * Indices ordered by [value] descending, unknown (<= 0) last and ties in
 * provider order, packed into a primitive `LongArray` so 100k titles sort
 * without boxing.
 */
private inline fun descendingPermutation(n: Int, value: (Int) -> Long): IntArray {
    val packed = LongArray(n)
    for (i in 0 until n) {
        val v = value(i)
        val rank = if (v > 0L) RANK_CAP - v.coerceAtMost(RANK_CAP) else RANK_CAP + 1
        packed[i] = (rank shl 32) or i.toLong()
    }
    packed.sort()
    return IntArray(n) { (packed[it] and 0xFFFF_FFFFL).toInt() }
}

private val AZ_MARKS = Regex("""\p{M}+""")
private val AZ_SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

/**
 * The A-Z key of a title: accent/case-folded, punctuation collapsed, but letters
 * of every script kept (the search key drops everything outside a-z0-9, which
 * would give Cyrillic, Arabic or CJK titles an empty key).
 */
internal fun alphabeticalKey(name: String): String =
    java.text.Normalizer.normalize(cleanDisplayName(name), java.text.Normalizer.Form.NFD)
        .replace(AZ_MARKS, "")
        .lowercase(java.util.Locale.ROOT)
        .replace(AZ_SEPARATORS, " ")
        .trim()

/** 0 = starts with a Latin letter or digit, 1 = another script, 2 = no letters at all. */
private fun alphabeticalGroup(key: String): Int = when {
    key.isEmpty() -> 2
    key[0] in 'a'..'z' || key[0] in '0'..'9' -> 0
    else -> 1
}

/**
 * A-Z by the displayed title: digits before letters, Latin before other scripts,
 * untitled-looking entries last, ties by provider order.
 */
private fun alphabeticalPermutation(entries: List<CatalogEntry>): IntArray {
    val keys = Array(entries.size) { alphabeticalKey(entries[it].name) }
    val groups = IntArray(entries.size) { alphabeticalGroup(keys[it]) }
    val boxed = Array<Int>(entries.size) { it }
    java.util.Arrays.sort(boxed) { a, b ->
        val byGroup = groups[a].compareTo(groups[b])
        if (byGroup != 0) return@sort byGroup
        val byKey = keys[a].compareTo(keys[b])
        if (byKey != 0) byKey else a.compareTo(b)
    }
    return IntArray(boxed.size) { boxed[it] }
}

private fun keyOf(label: GenreLabel): String = when (label) {
    is GenreLabel.Known -> "c:" + label.genre.name
    is GenreLabel.Other -> "o:" + GenreNormalizer.foldKey(label.label)
}

private fun buildGenreFacet(entries: List<CatalogEntry>, table: List<String>): GenreFacet? {
    if (table.isEmpty() || entries.isEmpty()) return null
    val labelsPerRef = table.map { GenreNormalizer.labels(it) }
    val titlesPerRef = IntArray(table.size)
    var withGenre = 0
    for (entry in entries) {
        val ref = entry.genreRef
        if (ref in table.indices && labelsPerRef[ref].isNotEmpty()) {
            titlesPerRef[ref]++
            withGenre++
        }
    }
    if (withGenre.toDouble() / entries.size < MIN_COVERAGE) return null

    val titlesByKey = HashMap<String, Int>()
    val spellings = HashMap<String, HashMap<String, Int>>()
    val knownLabels = HashMap<String, GenreLabel>()
    val refKeys = ArrayList<Set<String>>(table.size)
    for (ref in table.indices) {
        val keys = LinkedHashSet<String>()
        for (label in labelsPerRef[ref]) {
            val key = keyOf(label)
            keys += key
            titlesByKey.merge(key, titlesPerRef[ref], Int::plus)
            if (label is GenreLabel.Other) {
                spellings.getOrPut(key) { HashMap() }.merge(label.label, titlesPerRef[ref], Int::plus)
            } else {
                knownLabels[key] = label
            }
        }
        refKeys += keys
    }

    val options = titlesByKey.entries
        .filter { it.value > 0 }
        .map { (key, titles) ->
            // Pass-through genres show their most frequent spelling.
            val label = knownLabels[key]
                ?: GenreLabel.Other(
                    spellings.getValue(key).entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .first().key
                )
            GenreOption(key, label, titles)
        }
        .sortedWith(compareByDescending<GenreOption> { it.titles }.thenBy { it.key })
    if (options.size < 2) return null
    return GenreFacet(options.take(MAX_GENRE_OPTIONS), options.size > MAX_GENRE_OPTIONS, refKeys)
}

/**
 * Applies category, query and the sheet filters in the chosen [sort] order.
 * Walks the precomputed permutation, so there is no per-call sort. A control
 * whose facet is missing is ignored. Pure: safe on `Dispatchers.Default`.
 */
fun <T : CatalogEntry> filterCatalog(
    items: List<T>,
    index: TabIndex?,
    sort: CatalogSort,
    categoryId: String?,
    normalizedQuery: String,
    language: LanguageOption?,
    genreKey: String?,
    minRating7: Boolean,
    /** Flips the direction of [sort]; titles with no value stay last. */
    reversed: Boolean = false
): List<T> {
    val order = if (sort != CatalogSort.PROVIDER && index != null && index.size == items.size) {
        index.permutations[sort]
    } else {
        null
    }
    // Reversed: the titles that carry a value run backwards, the unknown tail
    // keeps its place.
    val known = if (order != null && reversed) index?.knownCounts?.get(sort) ?: 0 else 0
    val byCategory = if (language != null) index?.language?.byCategory else null
    val genreMatches = if (genreKey != null) index?.genres?.refMatches(genreKey) else null
    val ratingOn = minRating7 && index?.ratingFilter == true
    val applyLanguage = language != null && byCategory != null

    val out = ArrayList<T>()
    for (position in items.indices) {
        val at = if (position < known) known - 1 - position else position
        val item = items[if (order != null) order[at] else position]
        if (categoryId != null && item.categoryId != categoryId) continue
        if (normalizedQuery.isNotEmpty() && !item.searchKey.contains(normalizedQuery)) continue
        if (applyLanguage && (byCategory!![item.categoryId] ?: LanguageOption.OTHER) != language) continue
        if (genreMatches != null) {
            val ref = item.genreRef
            if (ref !in genreMatches.indices || !genreMatches[ref]) continue
        }
        if (ratingOn && item.rating < MIN_RATING_FILTER) continue
        out += item
    }
    return out
}

/** The saved sort/language choices, as stored strings. */
data class StoredControls(val movieSort: String?, val seriesSort: String?, val language: String?)

/**
 * The choices to apply after checking them against a catalogue.
 * [resetHappened] drives the `controls_reset` notice; [writeBack] says whether
 * the corrected values should be saved again.
 */
data class Reconciled(
    val movieSort: CatalogSort,
    val seriesSort: CatalogSort,
    val language: LanguageOption?,
    val resetHappened: Boolean,
    val writeBack: Boolean,
    val movieReversed: Boolean = false,
    val seriesReversed: Boolean = false
)

/**
 * Checks saved choices against a freshly built index. Only a current-schema
 * index can invalidate a choice: an outdated cache simply lacks the data until
 * the next refresh, so there the saved values are applied where available and
 * neither written back nor announced (the "outdated" notice covers it).
 */
fun reconcileControls(stored: StoredControls, index: CatalogIndex): Reconciled {
    val movieSort = CatalogSort.fromName(stored.movieSort)?.takeIf { it in index.movies.sorts }
    val seriesSort = CatalogSort.fromName(stored.seriesSort)?.takeIf { it in index.series.sorts }
    val language = LanguageOption.fromId(stored.language)?.takeIf {
        it in index.movies.language?.options.orEmpty() || it in index.series.language?.options.orEmpty()
    }
    val applied = Reconciled(
        movieSort = movieSort ?: CatalogSort.PROVIDER,
        seriesSort = seriesSort ?: CatalogSort.PROVIDER,
        language = language,
        resetHappened = false,
        writeBack = false,
        movieReversed = movieSort != null && movieSort != CatalogSort.PROVIDER &&
            CatalogSort.isReversedName(stored.movieSort),
        seriesReversed = seriesSort != null && seriesSort != CatalogSort.PROVIDER &&
            CatalogSort.isReversedName(stored.seriesSort)
    )
    if (index.outdated) return applied

    // A stored PROVIDER is not a choice that can become invalid.
    fun invalid(raw: String?, resolved: Any?) =
        raw != null && resolved == null && raw != CatalogSort.PROVIDER.name
    val reset = invalid(stored.movieSort, movieSort) || invalid(stored.seriesSort, seriesSort) ||
        (stored.language != null && language == null)
    return applied.copy(resetHappened = reset, writeBack = reset)
}
