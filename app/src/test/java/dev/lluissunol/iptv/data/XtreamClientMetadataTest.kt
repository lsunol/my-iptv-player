package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.CatalogWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The metadata the catalogue sort/filter and the detail screens read, in every
 * shape the Xtream forks send. A bad value is unknown; it never drops an item.
 */
class XtreamClientMetadataTest {

    private val year = 2024
    private val fallback: (String, String) -> String? = { type, id -> "http://fake.example/$type/u/p/$id.m3u8" }

    private fun vod(body: String): List<VodStreamDto> = decodeVodStreams(body, ArrayList())

    // -------------------------------------------------- tolerant primitives

    @Test
    fun anArrayOrObjectInAnyFieldKeepsTheItem() {
        val body = """[
            {"stream_id": 1, "name": "A", "rating": [], "added": {}, "category_id": []},
            {"stream_id": [], "name": "B"},
            {"stream_id": 3, "name": ["x"], "rating": {"a": 1}, "added": ["1701770400"], "genre": {}},
            {"stream_id": 4, "name": "D", "rating": "7,4", "rating_5based": [4], "year": [2016]}
        ]"""
        val warnings = ArrayList<CatalogWarning>()
        val items = decodeVodStreams(body, warnings)

        // Nothing is skipped: every element decodes (an unusable id is 0 and is
        // filtered later by the catalogue build, not here).
        assertEquals(4, items.size)
        assertTrue(warnings.isEmpty())
        assertEquals(0.0, items[0].rating, 0.0)
        assertNull(items[0].added)
        assertEquals(0L, items[1].streamId)
        assertEquals(3L, items[2].streamId)
        // An array of primitives is joined; an object is unknown.
        assertEquals("1701770400", items[2].added)
        assertNull(items[2].genre)
        assertEquals(7.4, items[3].rating, 1e-9)
        assertEquals("4", items[3].rating5Based)
        assertEquals("2016", items[3].year)
    }

    @Test
    fun releaseAliasesAreSeparateProperties() {
        val items = vod(
            """[{"stream_id":1,"releasedate":"2010-01-02","release_date":"2011","releaseDate":"2012","year":2013},
               {"stream_id":2,"release_date":"2011-05-06","year":2013},
               {"stream_id":3,"releaseDate":"2012-07-08"},
               {"stream_id":4,"year":2013},
               {"stream_id":5,"releasedate":"0000-00-00","release_date":"","releaseDate":"N/A","year":"2014"}]"""
        )
        val keys = items.map { firstReleaseKey(it.releaseRaws(), year) }
        assertEquals(listOf(20_100_102, 20_110_506, 20_120_708, 20_130_000, 20_140_000), keys)
    }

    @Test
    fun numbersAsStringsAndNumbersBothWork() {
        val items = vod(
            """[{"stream_id":"9","added":1701770400,"rating":8,"rating_5based":3.5,"genre":"Drama"},
               {"stream_id":9.0,"added":"1701770400","rating":"8","rating_5based":"3.5","genre":["Drama","Crime"]}]"""
        )
        assertEquals(9L, items[0].streamId)
        assertEquals(9L, items[1].streamId)
        assertEquals("1701770400", items[0].added)
        assertEquals("1701770400", items[1].added)
        assertEquals("3.5", items[0].rating5Based)
        assertEquals("Drama/Crime", items[1].genre)
    }

    @Test
    fun seriesMetadataIsReadWithAnyNaming() {
        val items = decodeSeriesList(
            """[{"series_id":1,"name":"S","releaseDate":"1999-03-04","genre":"Crime /  Drama","episode_run_time":"45","rating_5based":"4"},
               {"series_id":2,"name":"T","release_date":"2001","year":"1990","episode_run_time":[]},
               {"series_id":3,"name":"U","year":2005,"episode_run_time":2700}]""",
            ArrayList()
        )
        assertEquals(listOf(19_990_304, 20_010_000, 20_050_000), items.map { firstReleaseKey(it.releaseRaws(), year) })
        assertEquals("Crime /  Drama", items[0].genre)
        assertEquals("45", items[0].episodeRunTime)
        assertNull(items[1].episodeRunTime)
        assertEquals("2700", items[2].episodeRunTime)
    }

    // ------------------------------------------------------------ get_vod_info

    @Test
    fun movieInfoReadsAnInfoObject() {
        val info = decodeVodInfo(
            """{"info":{"releasedate":"2016-10-19","duration_secs":6720,"duration":"01:52:00",
               "genre":"Action, Adventure","director":" Nombre   Apellido ","cast":"A, B,,C, A",
               "plot":"Trama de prueba.","rating":"7,4","movie_image":"http://fake.example/p.jpg"},
               "movie_data":{"stream_id":1,"name":"Otro nombre","container_extension":"mkv"}}"""
        ).toMovieInfo(null, year)

        assertEquals(2016, info.year)
        assertEquals(6720, info.durationSec)
        assertEquals(7.4, info.rating10, 1e-9)
        assertEquals(
            listOf(GenreLabel.Known(CanonicalGenre.ACTION), GenreLabel.Known(CanonicalGenre.ADVENTURE)),
            info.genres
        )
        assertEquals("Nombre Apellido", info.director)
        assertEquals(listOf("A", "B", "C"), info.cast)
        assertEquals("Trama de prueba.", info.plot)
        assertEquals("http://fake.example/p.jpg", info.posterUrl)
        assertTrue(!info.isEmpty)
    }

    @Test
    fun movieInfoAcceptsTheForkAliases() {
        val info = decodeVodInfo(
            """{"info":{"description":"Texto","actors":"X / Y","release_date":"19/10/2016",
               "duration":"52:00","genre":["Drama"]}}"""
        ).toMovieInfo(null, year)
        assertEquals("Texto", info.plot)
        assertEquals(listOf("X", "Y"), info.cast)
        assertEquals(2016, info.year)
        assertEquals(3120, info.durationSec)
        assertEquals(listOf<GenreLabel>(GenreLabel.Known(CanonicalGenre.DRAMA)), info.genres)
    }

    @Test
    fun movieInfoCapsTheCastAtEightNames() {
        val cast = (1..20).joinToString(", ") { "Actor $it" }
        val info = decodeVodInfo("""{"info":{"cast":"$cast"}}""").toMovieInfo(null, year)
        assertEquals(8, info.cast.size)
    }

    @Test
    fun movieInfoToleratesEveryInfoShape() {
        listOf(
            """{"info":[]}""",
            """{"info":{}}""",
            """{"info":null}""",
            """{}""",
            """{"info":"not json"}""",
            """{"info":"{}"}""",
            """{"info":[],"movie_data":{"name":"x"}}"""
        ).forEach { body ->
            val info = decodeVodInfo(body).toMovieInfo(null, year)
            assertTrue(body, info.isEmpty)
            assertEquals(0, info.year)
        }
        // `info` as a JSON-encoded string.
        val encoded = decodeVodInfo("""{"info":"{\"plot\":\"Desde texto\",\"releasedate\":\"2010\"}"}""")
            .toMovieInfo(null, year)
        assertEquals("Desde texto", encoded.plot)
        assertEquals(2010, encoded.year)
    }

    @Test
    fun movieInfoNeverUsesMovieDataForAnything() {
        val info = decodeVodInfo(
            """{"info":{"plot":"P"},"movie_data":{"name":"Intruso","releasedate":"2001","genre":"Drama",
               "container_extension":"mkv","direct_source":"http://fake.example/secret.mkv"}}"""
        ).toMovieInfo(null, year)
        assertEquals("P", info.plot)
        assertEquals(0, info.year)
        assertTrue(info.genres.isEmpty())
    }

    @Test
    fun anEmptyArrayOrNullRootIsTheEmptyState() {
        listOf("[]", "null").forEach { assertTrue(it, decodeVodInfo(it).toMovieInfo(null, year).isEmpty) }
    }

    @Test
    fun aNonObjectRootIsMalformed() {
        listOf("\"x\"", "42", "not json", "").forEach { body ->
            try {
                decodeVodInfo(body)
                fail("expected Malformed for '$body'")
            } catch (e: PlaylistException.Malformed) {
                // expected
            }
        }
    }

    @Test
    fun junkMovieInfoFieldsAreUnknownNotFatal() {
        val info = decodeVodInfo(
            """{"info":{"releasedate":"0000-00-00","duration_secs":"abc","duration":"N/A","rating":"N/A",
               "genre":"123","director":"","cast":[],"plot":"   "}}"""
        ).toMovieInfo(null, year)
        assertTrue(info.isEmpty)
    }

    // -------------------------------------------------------- series years

    private fun seriesInfo(body: String) = decodeSeriesInfo(body).toSeriesInfo(
        seriesId = 5L,
        defaultName = "Serie de prueba",
        matcher = null,
        fallbackUrl = fallback,
        currentYear = year
    )

    @Test
    fun seriesYearsComeOnlyFromEpisodeReleaseDates() {
        val info = seriesInfo(
            """{"info":{},"episodes":{
                "1":[{"id":"101","episode_num":1,"title":"E1","info":{"releaseDate":"2015-03-04"}}],
                "2":[{"id":"201","episode_num":1,"title":"E2","info":"{\"release_date\":\"2021-05-06\"}"},
                     {"id":"202","episode_num":2,"title":"E3","info":{"releasedate":"2018"}}]}}"""
        )
        assertEquals(2015, info.firstYear)
        assertEquals(2021, info.lastYear)
    }

    @Test
    fun aBulkImportAddedStampNeverBecomesAYear() {
        val info = seriesInfo(
            """{"info":{},"episodes":{"1":[
                {"id":"101","episode_num":1,"title":"E1","added":"1701770400","info":{}},
                {"id":"102","episode_num":2,"title":"E2","added":"1701770400"}]}}"""
        )
        assertEquals(0, info.firstYear)
        assertEquals(0, info.lastYear)
    }

    @Test
    fun implausibleEpisodeYearsAreIgnored() {
        val info = seriesInfo(
            """{"episodes":{"1":[
                {"id":"101","episode_num":1,"title":"E1","info":{"releaseDate":"0000-00-00"}},
                {"id":"102","episode_num":2,"title":"E2","info":{"releaseDate":"2999-01-01"}},
                {"id":"103","episode_num":3,"title":"E3","info":{"releaseDate":"2019-01-01"}}]}}"""
        )
        assertEquals(2019, info.firstYear)
        assertEquals(2019, info.lastYear)
    }

    @Test
    fun seriesWithoutEpisodesHasNoYears() {
        val info = seriesInfo("""{"info":{},"episodes":[]}""")
        assertEquals(0, info.firstYear)
        assertEquals(0, info.lastYear)
        // And a series whose `info` is a string still works.
        assertEquals(0, seriesInfo("""{"info":"{}","episodes":{}}""").firstYear)
    }
}
