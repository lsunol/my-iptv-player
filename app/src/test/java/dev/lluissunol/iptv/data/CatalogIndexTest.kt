package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.PanelFixtures.Variant
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the index offers on four panel shapes (measured, rich, bare, malformed),
 * the sort semantics, and filter combinations. Fixtures are synthetic.
 */
class CatalogIndexTest {

    private fun index(variant: Variant) = buildCatalogIndex(PanelFixtures.catalog(variant))

    // ----------------------------------------------------------- panel shapes

    @Test
    fun measuredPanelOffersRecentRatingAndAzButNoRelease() {
        val index = index(Variant.MEASURED)
        assertFalse(index.outdated)

        val movies = index.movies
        assertEquals(
            listOf(CatalogSort.PROVIDER, CatalogSort.RECENT, CatalogSort.RATING, CatalogSort.AZ),
            movies.sorts
        )
        assertTrue("footnote needed: Estreno was hidden", movies.sortsHidden)
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN), movies.language!!.options)
        assertNull("no genre in the movie list", movies.genres)
        assertTrue(movies.ratingFilter)
        assertTrue(movies.hasFilters)
        assertTrue("Género is hidden, so the sheet footnote shows", movies.filtersHidden)

        val series = index.series
        // `last_modified` is a bulk stamp and `releaseDate` is empty: never offered.
        assertEquals(listOf(CatalogSort.PROVIDER, CatalogSort.RATING, CatalogSort.AZ), series.sorts)
        assertNotNull(series.language)
        val genreKeys = series.genres!!.options.map { it.key }.toSet()
        // English and Spanish spellings of one genre are one option.
        assertEquals(
            setOf("c:CRIME", "c:DRAMA", "c:THRILLER", "c:COMEDY", "c:ACTION", "c:ADVENTURE"),
            genreKeys
        )
        assertFalse(series.genres!!.capped)
    }

    @Test
    fun richPanelOffersEverything() {
        val index = index(Variant.RICH)
        assertEquals(
            listOf(
                CatalogSort.PROVIDER, CatalogSort.RECENT, CatalogSort.RELEASE,
                CatalogSort.RATING, CatalogSort.AZ
            ),
            index.movies.sorts
        )
        assertFalse(index.movies.sortsHidden)
        assertEquals(
            listOf(CatalogSort.PROVIDER, CatalogSort.RELEASE, CatalogSort.RATING, CatalogSort.AZ),
            index.series.sorts
        )
        val languages = index.movies.language!!.options
        assertEquals(LanguageOption.OTHER, languages.last())
        assertEquals(
            setOf(LanguageOption.ES, LanguageOption.EN, LanguageOption.DE, LanguageOption.OTHER),
            languages.toSet()
        )
        assertEquals(
            setOf("c:ACTION", "c:COMEDY", "c:DRAMA", "c:HORROR", "c:THRILLER", "c:SCIFI", "c:FANTASY", "c:ANIMATION"),
            index.movies.genres!!.options.map { it.key }.toSet()
        )
        assertFalse(index.movies.filtersHidden)
    }

    @Test
    fun barePanelOffersOnlyProviderOrderAndAz() {
        val index = index(Variant.BARE)
        listOf(index.movies, index.series).forEach { tab ->
            assertEquals(listOf(CatalogSort.PROVIDER, CatalogSort.AZ), tab.sorts)
            assertTrue(tab.sortsHidden)
            assertNull(tab.language)
            assertNull(tab.genres)
            assertFalse(tab.ratingFilter)
            assertFalse("the Filtros button is hidden", tab.hasFilters)
            assertTrue(tab.filtersHidden)
        }
    }

    @Test
    fun malformedPanelLoadsEveryValidTitleAndNeverThrows() {
        val panel = PanelFixtures.panel(Variant.MALFORMED)
        val catalog = PanelFixtures.catalog(Variant.MALFORMED)
        // One duplicate id and one unusable id are the only items dropped.
        assertEquals(panel.movies - 2, catalog.movies.size)
        assertEquals(catalog.movies.map { it.streamId }.toSet().size, catalog.movies.size)
        assertEquals(panel.seriesCount, catalog.series.size)
        assertTrue("a null name degrades to a title", catalog.movies.any { it.name == "Sin nombre" })

        catalog.movies.forEach { m ->
            assertTrue(m.name, m.rating in 0.0..10.0)
            assertTrue(m.added == 0L || m.added <= PanelFixtures.NOW + 2 * 86_400)
            assertTrue(m.releaseKey == 0 || m.releaseKey / 10_000 in 1900..2025)
        }
        // Each release shape of the garbage table resolves as documented.
        val keys = catalog.movies.map { it.releaseKey }.toSet()
        assertTrue(20_161_019 in keys) // 19/10/2016 and 10/19/2016
        assertTrue(20_160_000 in keys) // 05/06/2016 is ambiguous: year only
        assertTrue(0 in keys) // 0000-00-00, 1800, 2999, {}
        assertTrue(catalog.movies.any { it.rating == 8.5 }) // 85 is a percentage
        assertTrue(catalog.movies.any { it.rating == 7.4 }) // "7,4"
        // `episode_run_time` "45 min" and "2700" are both 45 minutes.
        assertTrue(catalog.series.all { it.runtimeMin == 45 })

        val index = buildCatalogIndex(catalog)
        for (tab in listOf(index.movies, index.series)) {
            assertTrue(CatalogSort.PROVIDER in tab.sorts)
            assertTrue(CatalogSort.AZ in tab.sorts)
            tab.permutations.values.forEach { permutation ->
                assertEquals((0 until tab.size).toList(), permutation.sorted())
            }
        }
    }

    @Test
    fun malformedGenreTableKeepsOnlyUsableStrings() {
        val catalog = PanelFixtures.catalog(Variant.MALFORMED)
        // The 60-character token, "123", null and {} yield no genre.
        assertTrue(catalog.movieGenres.none { it.length > 30 || it == "123" })
        assertTrue("Drama/Crime" in catalog.movieGenres)
        assertTrue(catalog.movies.any { it.genreRef == -1 })
        assertTrue(catalog.movies.all { it.genreRef in -1 until catalog.movieGenres.size })
    }

    // ------------------------------------------------------------ thresholds

    @Test
    fun qualifiesNeedsCoverageDistinctnessAndNoDominantValue() {
        assertTrue(qualifies(100, 100, mapOf(1L to 50, 2L to 50)))
        // Coverage: 50% is enough, 49% is not.
        assertTrue(qualifies(50, 100, mapOf(1L to 25, 2L to 25)))
        assertFalse(qualifies(49, 100, mapOf(1L to 25, 2L to 24)))
        // Distinct values.
        assertFalse(qualifies(100, 100, mapOf(1L to 100)))
        // Dominance: 95% of the carrying titles is too much, 94% is fine.
        assertFalse(qualifies(100, 100, mapOf(1L to 95, 2L to 5)))
        assertTrue(qualifies(100, 100, mapOf(1L to 94, 2L to 6)))
        assertFalse(qualifies(0, 0, emptyMap()))
        assertFalse(qualifies(0, 10, emptyMap()))
    }

    @Test
    fun addedIsCountedInDayBucketsNotRawSeconds() {
        // Ten distinct timestamps inside ONE day: a bulk import, not a sort key.
        val base = 1_700_000_000L - 1_700_000_000L % 86_400
        val movies = (1..10).map { movie(it, added = base + it * 600L, rating = it % 4 + 1.0) }
        val index = buildCatalogIndex(catalogOf(movies))
        assertFalse(CatalogSort.RECENT in index.movies.sorts)
        // Same data spread over ten days qualifies.
        val spread = (1..10).map { movie(it, added = base + it * 86_400L) }
        assertTrue(CatalogSort.RECENT in buildCatalogIndex(catalogOf(spread)).movies.sorts)
    }

    @Test
    fun releaseIsCountedInYearBuckets() {
        // Different days of ONE year: one bucket, so no Estreno sort.
        val sameYear = (1..10).map { movie(it, releaseKey = 20_160_100 + it) }
        assertFalse(CatalogSort.RELEASE in buildCatalogIndex(catalogOf(sameYear)).movies.sorts)
        val years = (1..10).map { movie(it, releaseKey = (2000 + it) * 10_000) }
        assertTrue(CatalogSort.RELEASE in buildCatalogIndex(catalogOf(years)).movies.sorts)
    }

    @Test
    fun ratingIsCountedInTenthsOfAPoint() {
        val rounded = (1..10).map { movie(it, rating = 7.0 + (it % 2) * 0.04) }
        // 7.00 and 7.04 round to the same tenth: one bucket.
        assertFalse(CatalogSort.RATING in buildCatalogIndex(catalogOf(rounded)).movies.sorts)
    }

    @Test
    fun ratingFilterNeedsRatingsOnBothSidesOfSeven() {
        fun filterFor(vararg ratings: Double) =
            buildCatalogIndex(catalogOf(ratings.mapIndexed { i, r -> movie(i + 1, rating = r) })).movies.ratingFilter
        assertTrue(filterFor(5.0, 8.0, 6.0, 9.0))
        assertFalse("everything is >= 7", filterFor(8.0, 9.0, 7.0, 7.5))
        assertFalse("everything is < 7", filterFor(5.0, 6.0, 4.0))
        assertFalse("under half rated", filterFor(5.0, 8.0, 0.0, 0.0, 0.0))
    }

    // ----------------------------------------------------------- permutations

    @Test
    fun ratingSortPutsUnknownsLastAndTiesInProviderOrder() {
        val movies = listOf(5.0, 0.0, 9.0, 5.0, 0.0, 9.0).mapIndexed { i, r -> movie(i + 1, rating = r) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, false)
        assertEquals(listOf(3L, 6L, 1L, 4L, 2L, 5L), sorted.map { it.streamId })
    }

    @Test
    fun recentSortIsNewestFirstWithUnknownsLast() {
        val day = 86_400L
        val movies = listOf(0L, 5 * day + 10, 9 * day + 10, 7 * day + 10, 9 * day + 10)
            .mapIndexed { i, added -> movie(i + 1, added = if (added == 0L) 0L else added + 1_600_000_000L) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.RECENT, null, "", null, null, false)
        // Equal timestamps (ids 3 and 5) keep provider order; the unknown (id 1) is last.
        assertEquals(listOf(3L, 5L, 4L, 2L, 1L), sorted.map { it.streamId })
    }

    @Test
    fun releaseSortOrdersByFullDateThenYearOnly() {
        val keys = listOf(20_100_000, 0, 20_150_302, 20_150_000, 20_200_101)
        val movies = keys.mapIndexed { i, key -> movie(i + 1, releaseKey = key) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.RELEASE, null, "", null, null, false)
        assertEquals(listOf(5L, 3L, 4L, 1L, 2L), sorted.map { it.streamId })
    }

    @Test
    fun azUsesTheDisplayedTitleDigitsFirstAndIgnoresAccentsAndPrefix() {
        val names = listOf("| ES | Zebra", "Apple", "10 Cosas", "Árbol", "!!Banana")
        val movies = names.mapIndexed { i, n -> movie(i + 1, name = n) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.AZ, null, "", null, null, false)
        assertEquals(listOf("10 Cosas", "Apple", "Árbol", "!!Banana", "| ES | Zebra"), sorted.map { it.name })
    }

    // -------------------------------------------------------- reversed order

    @Test
    fun reversedRatingRunsLowestFirstAndKeepsUnknownsLast() {
        val movies = listOf(5.0, 0.0, 9.0, 5.0, 0.0, 9.0).mapIndexed { i, r -> movie(i + 1, rating = r) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val forward = filterCatalog(catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, false)
        val reversed = filterCatalog(
            catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, false, reversed = true
        )
        assertEquals(listOf(3L, 6L, 1L, 4L, 2L, 5L), forward.map { it.streamId })
        // Exact reverse of the four rated titles (lowest first, ties backwards too);
        // the two unknowns (2, 5) stay at the end.
        assertEquals(listOf(4L, 1L, 6L, 3L, 2L, 5L), reversed.map { it.streamId })
    }

    @Test
    fun reversedRecentRunsOldestFirst() {
        val day = 86_400L
        val movies = listOf(0L, 5 * day, 9 * day, 7 * day)
            .mapIndexed { i, added -> movie(i + 1, added = if (added == 0L) 0L else added + 1_600_000_000L) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val reversed = filterCatalog(
            catalog.movies, index.movies, CatalogSort.RECENT, null, "", null, null, false, reversed = true
        )
        assertEquals(listOf(2L, 4L, 3L, 1L), reversed.map { it.streamId })
    }

    @Test
    fun reversedAzRunsZToAWithTitlesWithoutLettersStillLast() {
        val names = listOf("Apple", "!!!", "Zebra", "Mango")
        val movies = names.mapIndexed { i, n -> movie(i + 1, name = n) }
        val catalog = catalogOf(movies)
        val index = buildCatalogIndex(catalog)
        val reversed = filterCatalog(
            catalog.movies, index.movies, CatalogSort.AZ, null, "", null, null, false, reversed = true
        )
        assertEquals(listOf("Zebra", "Mango", "Apple", "!!!"), reversed.map { it.name })
    }

    @Test
    fun reversedCombinesWithFiltersAndNeverLosesTitles() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val index = buildCatalogIndex(catalog)
        val forward = filterCatalog(catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, true)
        val reversed = filterCatalog(
            catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, true, reversed = true
        )
        assertEquals(forward.map { it.streamId }.toSet(), reversed.map { it.streamId }.toSet())
        assertEquals(forward.size, reversed.size)
        // Reversing the direction of the provider order is a no-op (it has none).
        val provider = filterCatalog(
            catalog.movies, index.movies, CatalogSort.PROVIDER, null, "", null, null, false, reversed = true
        )
        assertEquals(catalog.movies, provider)
    }

    @Test
    fun storedNamesRoundTripTheDirection() {
        assertEquals("RATING", CatalogSort.storedName(CatalogSort.RATING, false))
        assertEquals("RATING:REV", CatalogSort.storedName(CatalogSort.RATING, true))
        assertEquals("PROVIDER", CatalogSort.storedName(CatalogSort.PROVIDER, true))
        assertEquals(CatalogSort.RATING, CatalogSort.fromName("RATING:REV"))
        assertEquals(CatalogSort.AZ, CatalogSort.fromName("AZ"))
        assertTrue(CatalogSort.isReversedName("AZ:REV"))
        assertFalse(CatalogSort.isReversedName("AZ"))
        assertNull(CatalogSort.fromName("NOPE:REV"))
        assertFalse(CatalogSort.RATING.naturallyAscending)
        assertTrue(CatalogSort.AZ.naturallyAscending)
    }

    @Test
    fun providerOrderIsTheCatalogueOrder() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val index = buildCatalogIndex(catalog)
        val result = filterCatalog(catalog.movies, index.movies, CatalogSort.PROVIDER, null, "", null, null, false)
        assertEquals(catalog.movies, result)
    }

    @Test
    fun aStaleIndexOfAnotherSizeFallsBackToProviderOrder() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val index = buildCatalogIndex(catalog)
        val shorter = catalog.movies.take(10)
        val result = filterCatalog(shorter, index.movies, CatalogSort.RATING, null, "", null, null, false)
        assertEquals(shorter, result)
    }

    // ---------------------------------------------------------------- filters

    @Test
    fun filtersCombineWithCategoryQueryAndSort() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val index = buildCatalogIndex(catalog)
        val tab = index.movies
        val spanishCategories = (1..7).map { it.toString() }.toSet()

        val spanish = filterCatalog(catalog.movies, tab, CatalogSort.PROVIDER, null, "", LanguageOption.ES, null, false)
        assertEquals(catalog.movies.filter { it.categoryId in spanishCategories }, spanish)

        val rated = filterCatalog(catalog.movies, tab, CatalogSort.PROVIDER, null, "", null, null, true)
        assertEquals(catalog.movies.filter { it.rating >= 7.0 }, rated)

        val both = filterCatalog(catalog.movies, tab, CatalogSort.RATING, null, "", LanguageOption.ES, null, true)
        assertEquals(
            catalog.movies.filter { it.categoryId in spanishCategories && it.rating >= 7.0 }
                .sortedByDescending { it.rating }.map { it.streamId }.toSet(),
            both.map { it.streamId }.toSet()
        )
        // Sorted: ratings never increase down the list.
        assertEquals(both.map { it.rating }, both.map { it.rating }.sortedDescending())

        val inCategory = filterCatalog(catalog.movies, tab, CatalogSort.PROVIDER, "3", "", null, null, false)
        assertTrue(inCategory.isNotEmpty() && inCategory.all { it.categoryId == "3" })

        val query = filterCatalog(
            catalog.movies, tab, CatalogSort.PROVIDER, null,
            SearchNormalizer.query("PELÍCULA de prueba 12"), null, null, false
        )
        assertTrue(query.isNotEmpty() && query.all { "pelicula de prueba 12" in it.searchKey })

        // Query, category and language together can only narrow.
        val all = filterCatalog(
            catalog.movies, tab, CatalogSort.AZ, "3", "prueba", LanguageOption.ES, null, true
        )
        assertTrue(all.all { it.categoryId == "3" && it.rating >= 7.0 && "prueba" in it.searchKey })
    }

    @Test
    fun otrosSelectsUnrecognisedAndUnknownCategories() {
        val catalog = PanelFixtures.catalog(Variant.RICH)
        val index = buildCatalogIndex(catalog)
        val other = filterCatalog(
            catalog.movies, index.movies, CatalogSort.PROVIDER, null, "", LanguageOption.OTHER, null, false
        )
        // Category 7 is `VOD 4K`.
        assertTrue(other.isNotEmpty() && other.all { it.categoryId == "7" })
    }

    @Test
    fun genreFilterMatchesUnifiedGenres() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val index = buildCatalogIndex(catalog)
        val tab = index.series
        val comedy = filterCatalog(catalog.series, tab, CatalogSort.PROVIDER, null, "", null, "c:COMEDY", false)
        // "Comedy" and "Comedia" are the same filter.
        val expected = catalog.series.filter {
            catalog.seriesGenres.getOrNull(it.genreRef) in setOf("Comedy", "Comedia")
        }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, comedy)
        // A multi-genre string is found under each of its genres.
        val drama = filterCatalog(catalog.series, tab, CatalogSort.PROVIDER, null, "", null, "c:DRAMA", false)
        assertTrue(drama.any { catalog.seriesGenres[it.genreRef].contains("Crime") })
    }

    @Test
    fun aControlWhoseFacetIsMissingIsIgnored() {
        val catalog = PanelFixtures.catalog(Variant.BARE)
        val index = buildCatalogIndex(catalog)
        val result = filterCatalog(
            catalog.movies, index.movies, CatalogSort.PROVIDER, null, "",
            LanguageOption.ES, "c:ACTION", true
        )
        assertEquals(catalog.movies, result)
    }

    // ------------------------------------------------------------------ cache

    @Test
    fun anOldCacheDecodesAsOutdatedAndANewOneAsCurrent() {
        val old = """{"movies":[],"series":[],"movieCategories":[],"seriesCategories":[]}"""
        val decoded = dev.lluissunol.iptv.data.model.decodeVodCatalog(old)!!
        assertEquals(0, decoded.schemaVersion)
        assertTrue(buildCatalogIndex(decoded).outdated)

        val fresh = PanelFixtures.catalog(Variant.RICH)
        assertEquals(CATALOG_SCHEMA_VERSION, fresh.schemaVersion)
        val roundTrip = dev.lluissunol.iptv.data.model.decodeVodCatalog(
            dev.lluissunol.iptv.data.model.encodeVodCatalog(fresh)
        )!!
        assertEquals(fresh, roundTrip)
        assertFalse(buildCatalogIndex(roundTrip).outdated)
    }

    @Test
    fun oldItemsWithoutTheNewFieldsStillDecode() {
        val old = """{"movies":[{"streamId":1,"name":"Peli","posterUrl":null,"categoryId":"5","rating":7.0,
            "searchKey":"peli","favoriteKey":"url:1","playbackUrl":"http://fake.example/movie/u/p/1.m3u8"}],
            "series":[{"seriesId":2,"name":"Serie","posterUrl":null,"plot":null,"categoryId":"3",
            "rating":0.0,"searchKey":"serie","favoriteKey":"series:2"}],
            "movieCategories":[],"seriesCategories":[]}"""
        val decoded = dev.lluissunol.iptv.data.model.decodeVodCatalog(old)!!
        val movie = decoded.movies.single()
        assertEquals(0L, movie.added)
        assertEquals(0, movie.releaseKey)
        assertEquals(-1, movie.genreRef)
        assertEquals(-1, decoded.series.single().genreRef)
        // An outdated catalogue still offers what its old fields support.
        val tab = buildCatalogIndex(decoded).movies
        assertEquals(listOf(CatalogSort.PROVIDER), tab.sorts)
    }

    // ------------------------------------------------------------------ scale

    @Test
    fun buildsAHundredThousandTitleIndexWithinASanityBound() {
        val n = 100_000
        val categories = (1..40).map { VodCategory(it.toString(), if (it % 2 == 0) "ES - Cat $it" else "EN - Cat $it") }
        val random = java.util.Random(7)
        val movies = (1..n).map { i ->
            movie(
                i,
                name = "Título sintético ${random.nextInt(1_000_000)}",
                category = (i % 40 + 1).toString(),
                rating = random.nextInt(100) / 10.0,
                added = 1_600_000_000L + random.nextInt(100_000_000),
                releaseKey = (1950 + random.nextInt(70)) * 10_000
            )
        }
        val catalog = VodCatalog(
            movies = movies, series = emptyList(), movieCategories = categories, seriesCategories = emptyList(),
            schemaVersion = CATALOG_SCHEMA_VERSION
        )
        val start = System.nanoTime()
        val index = buildCatalogIndex(catalog)
        val millis = (System.nanoTime() - start) / 1_000_000
        // A sanity bound for a cold JVM, not a benchmark.
        assertTrue("index build took $millis ms", millis < 3_000)
        assertEquals(5, index.movies.sorts.size)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.RATING, null, "", null, null, false)
        assertEquals(n, sorted.size)
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.rating >= b.rating })
    }

    // ---------------------------------------------------------------- helpers

    private fun movie(
        id: Int,
        name: String = "Peli $id",
        category: String = "1",
        rating: Double = 0.0,
        added: Long = 0L,
        releaseKey: Int = 0,
        genreRef: Int = -1
    ) = VodItem(
        streamId = id.toLong(),
        name = name,
        posterUrl = null,
        categoryId = category,
        rating = rating,
        searchKey = SearchNormalizer.key(name),
        favoriteKey = "url:$id",
        playbackUrl = "http://fake.example/movie/u/p/$id.m3u8",
        added = added,
        releaseKey = releaseKey,
        genreRef = genreRef
    )

    private fun catalogOf(movies: List<VodItem>, series: List<SeriesCard> = emptyList()) = VodCatalog(
        movies = movies,
        series = series,
        movieCategories = emptyList(),
        seriesCategories = emptyList(),
        schemaVersion = CATALOG_SCHEMA_VERSION
    )
}
