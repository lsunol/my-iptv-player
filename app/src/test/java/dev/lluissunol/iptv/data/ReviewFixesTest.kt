package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.model.buildVodCatalog
import dev.lluissunol.iptv.data.model.decodeVodCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions found in review: large genre tables, non-Latin A-Z, DD/MM inference, old ratings. */
class ReviewFixesTest {

    @Test
    fun thousandsOfGenreOrderingsCollapseAndNoTitleLosesItsGenre() {
        val pool = listOf("Drama", "Crime", "Thriller", "Action", "Comedy", "Romance", "Horror", "Mystery")
        val random = java.util.Random(11)
        val seps = listOf(", ", ",", " / ", " ,  ", "; ")
        val raws = HashSet<String>()
        val dtos = ArrayList<VodStreamDto>()
        var id = 1L
        while (raws.size < 3_000) {
            val picked = pool.shuffled(random).take(2 + random.nextInt(4))
            val raw = picked.joinToString(seps[random.nextInt(seps.size)]) + " ".repeat(random.nextInt(3))
            if (raws.add(raw.trim().replace(Regex("\\s+"), " "))) {
                dtos += VodStreamDto(name = "Peli $id", streamId = id, genre = raw)
                id++
            }
        }
        // Pad past the point where a raw-string table would have been full.
        val api = XtreamApiCatalog(dtos, emptyList(), emptyList(), emptyList(), emptyList())
        val catalog = buildVodCatalog(api, XtreamStreamMatcher(emptyList()), { t, i -> "http://fake.example/$t/$i.m3u8" })
        assertTrue(catalog.movies.all { it.genreRef >= 0 })
        // Every ordering of the same set shares one entry: at most 2^8 sets.
        assertTrue(catalog.movieGenres.size <= 256)
        val index = buildCatalogIndex(catalog)
        assertNotNull(index.movies.genres)
        assertEquals(8, index.movies.genres!!.options.size)
    }

    @Test
    fun azKeepsLettersOfEveryScriptAndSortsLatinFirstEmptyLast() {
        val names = listOf("Яблоко", "Zebra", "日本語", "!!!", "Apple", "10 Cosas", "ألف", "Ärger")
        val movies = names.mapIndexed { i, n ->
            VodItem(
                streamId = i + 1L, name = n, posterUrl = null, categoryId = "1", rating = 0.0,
                searchKey = SearchNormalizer.key(n), favoriteKey = "url:$i", playbackUrl = "http://fake.example/m/$i.m3u8"
            )
        }
        val catalog = VodCatalog(movies, emptyList(), emptyList(), emptyList(), schemaVersion = CATALOG_SCHEMA_VERSION)
        val index = buildCatalogIndex(catalog)
        val sorted = filterCatalog(catalog.movies, index.movies, CatalogSort.AZ, null, "", null, null, false)
        assertEquals(listOf("10 Cosas", "Apple", "Ärger", "Zebra"), sorted.take(4).map { it.name })
        // Other scripts sort by their own letters, after Latin; punctuation-only is last.
        assertEquals(listOf("Яблоко", "ألف", "日本語"), sorted.subList(4, 7).map { it.name })
        assertEquals("!!!", sorted.last().name)
        assertEquals("яблоко", alphabeticalKey("Яблоко"))
    }

    @Test
    fun dayMonthOrderIsInferredOncePerField() {
        assertEquals(true, detectDayFirst(sequenceOf("05/06/2023", "25/06/2023", null, "x")))
        assertEquals(false, detectDayFirst(sequenceOf("05/06/2023", "06/25/2023")))
        assertNull(detectDayFirst(sequenceOf("05/06/2023", "07/08/2023")))
        assertNull(detectDayFirst(sequenceOf("25/06/2023", "06/25/2023")))
        val now = 1_731_000_000L
        // Ambiguous alone, resolved by the catalogue-wide order.
        assertEquals(0L, parseEpochSeconds("05/06/2023", now))
        val june5 = daysFromCivil(2023, 6, 5) * 86_400
        assertEquals(june5, parseEpochSeconds("05/06/2023", now, dayFirst = true))
        assertEquals(daysFromCivil(2023, 5, 6) * 86_400, parseEpochSeconds("05/06/2023", now, dayFirst = false))
    }

    @Test
    fun aDayFirstPanelKeepsItsAmbiguousAddedDates() {
        val dtos = listOf("25/06/2023", "05/06/2023", "03/07/2023", "10/08/2023").mapIndexed { i, d ->
            VodStreamDto(name = "P$i", streamId = i + 1L, added = d)
        }
        val api = XtreamApiCatalog(dtos, emptyList(), emptyList(), emptyList(), emptyList())
        val catalog = buildVodCatalog(api, XtreamStreamMatcher(emptyList()), { t, i -> "http://fake.example/$t/$i.m3u8" }, nowEpochSec = 1_731_000_000L)
        assertTrue(catalog.movies.all { it.added > 0 })
    }

    @Test
    fun anOldCacheClampsPercentageRatings() {
        val old = """{"movies":[{"streamId":1,"name":"A","posterUrl":null,"categoryId":"1","rating":85.0,
            "searchKey":"a","favoriteKey":"url:1","playbackUrl":"http://fake.example/m/1.m3u8"},
            {"streamId":2,"name":"B","posterUrl":null,"categoryId":"1","rating":-1.0,"searchKey":"b",
            "favoriteKey":"url:2","playbackUrl":"http://fake.example/m/2.m3u8"}],
            "series":[],"movieCategories":[],"seriesCategories":[]}"""
        val decoded = decodeVodCatalog(old)!!
        assertEquals(8.5, decoded.movies[0].rating, 1e-9)
        assertEquals(0.0, decoded.movies[1].rating, 0.0)
    }

    @Test
    fun theNewTvdbGenresAreUnified() {
        assertEquals(listOf(GenreLabel.Known(CanonicalGenre.TRAVEL)), GenreNormalizer.labels("Travel"))
        assertEquals(listOf(GenreLabel.Known(CanonicalGenre.HOME_GARDEN)), GenreNormalizer.labels("Home and Garden"))
        assertEquals(listOf(GenreLabel.Known(CanonicalGenre.MINI_SERIES)), GenreNormalizer.labels("Mini-Series"))
    }
}
