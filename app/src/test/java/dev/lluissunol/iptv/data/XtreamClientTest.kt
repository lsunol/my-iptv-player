package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.decodeVodCatalog
import dev.lluissunol.iptv.data.model.encodeVodCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the two `player_api.php` JSON shapes (flat catalogues and
 * `get_series_info`), with only fake credentials and hosts: never a real
 * provider.
 *
 * The fixtures mirror real Xtream fork quirks: numeric ids sent as strings,
 * `info` sent as a JSON-encoded string, missing `episodes`/`seasons`, episodes
 * without `container_extension`, and malformed items that must be skipped
 * without killing the catalogue.
 */
class XtreamClientTest {

    private fun warnings() = ArrayList<CatalogWarning>()

    // ------------------------------------------------------------- catalogue

    @Test
    fun decodesVodStreamsWithNumbersSentAsStrings() {
        val body = """
            [
              {"num": 1, "name": "Película Uno", "stream_id": 123,
               "stream_icon": "http://fake.example:8080/posters/uno.jpg",
               "rating": "7.5", "category_id": "5", "container_extension": "mp4"},
              {"num": 2, "name": "Película Dos", "stream_id": "456",
               "stream_icon": null, "rating": 8, "category_id": 5,
               "container_extension": "mkv"}
            ]
        """.trimIndent()

        val items = decodeVodStreams(body, warnings())

        assertEquals(2, items.size)
        assertEquals(123L, items[0].streamId)
        assertEquals("Película Uno", items[0].name)
        assertEquals(7.5, items[0].rating, 1e-9)
        assertEquals(456L, items[1].streamId)
        // A JSON null on a defaulted field coerces to the default.
        assertEquals("", items[1].streamIcon)
    }

    @Test
    fun decodesVodStreamsMissingOptionalFields() {
        val body = """[{"stream_id": 9}]"""

        val items = decodeVodStreams(body, warnings())

        assertEquals(1, items.size)
        assertEquals(9L, items[0].streamId)
        assertEquals("", items[0].name)
        assertEquals(0.0, items[0].rating, 1e-9)
        assertNull(items[0].containerExtension)
    }

    @Test
    fun skipsMalformedCatalogueElementsWithAWarning() {
        val body = """
            [
              "no soy un objeto",
              {"name": "Válida", "stream_id": 1},
              {"name": "Sin id"},
              ["tampoco", "un objeto"]
            ]
        """.trimIndent()

        val warnings = warnings()
        val items = decodeVodStreams(body, warnings)

        // "Sin id" decodes (id 0) and is kept; the string and array do not.
        assertEquals(2, items.size)
        assertEquals(listOf("Válida", "Sin id"), items.map { it.name })
        assertEquals(listOf(CatalogWarning.SkippedItems(2)), warnings)
    }

    @Test
    fun degradesNonArrayAnswersToAnEmptyCatalogue() {
        // Several forks answer with a user_info object instead of an array.
        val body = """{"user_info": {"auth": 0}, "server_info": {}}"""
        val warnings = warnings()

        assertTrue(decodeVodStreams(body, warnings).isEmpty())
        assertEquals(listOf(CatalogWarning.UnexpectedFormat), warnings)
    }

    @Test
    fun degradesInvalidJsonToAnEmptyCatalogue() {
        val warnings = warnings()

        assertTrue(decodeVodStreams("{no es json", warnings).isEmpty())
        assertEquals(listOf(CatalogWarning.InvalidJson), warnings)
        assertTrue(decodeVodStreams("", warnings).isEmpty())
    }

    @Test
    fun decodesCategoriesWithNumericAndStringIds() {
        val body = """
            [
              {"category_id": 1, "category_name": "Acción", "parent_id": 0},
              {"category_id": "2", "category_name": "Comedia"}
            ]
        """.trimIndent()

        val items = decodeCategories(body, warnings())

        assertEquals(2, items.size)
        assertEquals("1", items[0].categoryId)
        assertEquals("Acción", items[0].categoryName)
        assertEquals("2", items[1].categoryId)
    }

    @Test
    fun decodesSeriesEntriesWithMissingFields() {
        val body = """[{"series_id": "77", "name": "Serie Buena"}, {"name": "Sin id"}]"""

        val items = decodeSeriesList(body, warnings())

        assertEquals(2, items.size)
        assertEquals(77L, items[0].seriesId)
        assertEquals("", items[0].plot)
        assertEquals(0L, items[1].seriesId)
    }

    // --------------------------------------------------------- series info

    private val matcher = XtreamStreamMatcher(
        listOf(
            channel(
                name = "Serie Buena S01E01",
                url = "http://fake.example:8080/series/user/pass/1001.m3u8"
            )
        )
    )

    private val fallback: (String, String) -> String? = { type, id ->
        "http://fake.example:8080/$type/user/pass/$id.m3u8"
    }

    @Test
    fun groupsEpisodesIntoSeasonsAndResolvesUrls() {
        val body = """
            {
              "info": {"name": "Serie Buena", "plot": "Una trama", "cover": "http://fake.example:8080/c.jpg"},
              "episodes": {
                "2": [
                  {"id": "ep-2001", "episode_num": 1, "title": "Segunda", "container_extension": "mkv"},
                  {"id": 2002, "episode_num": 2, "title": "Tercera"}
                ],
                "1": [
                  {"id": 1001, "episode_num": 1, "title": "Primera", "container_extension": "mp4"},
                  {"id": "no-digits", "episode_num": 2, "title": "Sin id utilizable"}
                ]
              }
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Serie Buena", matcher, fallback)

        assertEquals("Serie Buena", info.name)
        assertEquals("Una trama", info.plot)
        assertEquals(2, info.seasons.size)
        // Season keys are numbers, so ordering is by season, not insertion.
        assertEquals(1, info.seasons[0].number)
        assertEquals(2, info.seasons[1].number)

        val season1 = info.seasons[0].episodes
        // "no-digits" is skipped: it cannot match anything and has no usable id.
        assertEquals(1, season1.size)
        // The M3U match wins: the exact playlist URL is used, not the fallback,
        // and the matched channel's favorite key is preserved.
        assertEquals(
            "http://fake.example:8080/series/user/pass/1001.m3u8",
            season1[0].playbackUrl
        )
        assertEquals(matcher.seriesEpisode("1001")?.favoriteKey, season1[0].favoriteKey)

        // Unmatched episodes fall back to the provider's own .m3u8 pattern.
        val season2 = info.seasons[1].episodes
        assertEquals(2, season2.size)
        assertEquals(
            "http://fake.example:8080/series/user/pass/2001.m3u8",
            season2[0].playbackUrl
        )
        // Missing container_extension is irrelevant: never used for URLs.
        assertTrue(season2[0].playbackUrl.endsWith(".m3u8"))
        assertTrue(season2[1].playbackUrl.endsWith(".m3u8"))
    }

    @Test
    fun acceptsInfoEncodedAsString() {
        // A known Xtream fork quirk: `info` arrives as a JSON-encoded string.
        val body = """
            {
              "info": "{\"name\": \"Desde texto\", \"plot\": \"Trama\"}",
              "episodes": {"1": [{"id": "1001", "episode_num": 1}]}
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Fallback", matcher, fallback)

        assertEquals("Desde texto", info.name)
        assertEquals("Trama", info.plot)
        assertEquals(1, info.seasons.size)
    }

    @Test
    fun aSeriesWithNoSeasonsYieldsAnEmptySeasonList() {
        val body = """{"info": {"name": "Vacía"}}"""

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Vacía", matcher, fallback)

        assertEquals("Vacía", info.name)
        assertTrue(info.seasons.isEmpty())
        assertEquals(0, info.episodeCount)
    }

    @Test
    fun derivesSeasonPillsFromTheSeasonsArrayWhenEpisodesAreMissing() {
        val body = """
            {
              "info": {"name": "Solo temporadas"},
              "seasons": [{"season": 2}, {"season": 1}]
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Solo temporadas", matcher, fallback)

        assertEquals(listOf(1, 2), info.seasons.map { it.number })
        assertTrue(info.seasons.all { it.episodes.isEmpty() })
    }

    @Test
    fun mergesSeasonKeysThatNormalizeToTheSameNumber() {
        // A provider sending the same season under both "1" and "01" used to
        // build two Season(1) objects, which crashed the drill-down: the season
        // pills key their LazyRow on the season number. The two groups now merge
        // into one season (first occurrence wins) and no episode is dropped.
        val body = """
            {
              "info": {"name": "Serie Duplicada"},
              "episodes": {
                "1": [{"id": 1001, "episode_num": 1, "title": "Primera"}],
                "01": [{"id": 2001, "episode_num": 1, "title": "Gemela"}]
              }
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Serie Duplicada", matcher, fallback)

        // One season, not two with the same number.
        assertEquals(listOf(1), info.seasons.map { it.number })
        assertEquals(2, info.episodeCount)
        // Both groups' episodes survive the merge, in arrival order, with
        // their ids still unique inside the season.
        assertEquals(listOf("1001", "2001"), info.seasons[0].episodes.map { it.id })
        assertEquals(listOf("Primera", "Gemela"), info.seasons[0].episodes.map { it.title })
        // The same composite the drill-down keys its rows on stays unique.
        val rowKeys = info.seasons[0].episodes.map {
            it.season.toString() + ":" + it.episodeNum + ":" + it.id
        }
        assertEquals(rowKeys.size, rowKeys.toSet().size)
    }

    @Test
    fun repeatedEpisodeIdsInsideASeasonAreDisambiguatedNotDropped() {
        val body = """
            {
              "info": {"name": "Serie Buena"},
              "episodes": {
                "1": [
                  {"id": 1001, "episode_num": 1, "title": "Piloto"},
                  {"id": "1001", "episode_num": 2, "title": "Copia del id"},
                  {"id": 1001, "episode_num": 3, "title": "Tercera copia"}
                ],
                "2": [{"id": "1001", "episode_num": 1, "title": "Mismo id, otra temporada"}]
              }
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Serie Buena", matcher, fallback)

        val season1 = info.seasons.first { it.number == 1 }
        // All three survive: a repeated provider id is disambiguated with a
        // suffix, never silently dropped, so the episode rows keep unique keys.
        assertEquals(listOf("1001", "1001#2", "1001#3"), season1.episodes.map { it.id })
        // Uniqueness is per season: the same raw id in another season keeps
        // its raw form.
        assertEquals(
            listOf("1001"),
            info.seasons.first { it.number == 2 }.episodes.map { it.id }
        )
        // Every episode still resolves a playback URL: the matched one takes
        // the exact M3U entry, the copies the provider fallback.
        assertEquals(
            "http://fake.example:8080/series/user/pass/1001.m3u8",
            season1.episodes[0].playbackUrl
        )
        assertTrue(season1.episodes.all { it.playbackUrl.endsWith(".m3u8") })
    }

    @Test
    fun skipsARepeatedSeasonNumberInTheSeasonsArray() {
        // The seasons-array fallback used to append whatever the provider sent,
        // duplicates included; a repeated number would crash the season pills.
        val body = """
            {
              "info": {"name": "Solo temporadas"},
              "seasons": [{"season": 2}, {"season": 2}, {"season": 1}]
            }
        """.trimIndent()

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Solo temporadas", matcher, fallback)

        // First occurrence wins; the duplicate is skipped and the pills stay sorted.
        assertEquals(listOf(1, 2), info.seasons.map { it.number })
    }

    @Test
    fun rejectsNonObjectSeriesInfoAnswers() {
        org.junit.Assert.assertThrows(PlaylistException.Malformed::class.java) {
            decodeSeriesInfo("[1, 2, 3]")
        }
    }

    @Test
    fun fallsBackToTheDefaultNameWhenInfoHasNoName() {
        val body = """{"info": {"plot": "Solo trama"}, "episodes": {"1": [{"id": "1001"}]}}"""

        val info = decodeSeriesInfo(body).toSeriesInfo(77L, "Nombre por defecto", matcher, fallback)

        assertEquals("Nombre por defecto", info.name)
        // An episode without episode_num still plays, titled by its number.
        assertEquals("Episodio 0", info.seasons[0].episodes[0].title)
    }

    // -------------------------------------------------------- catalog cache

    @Test
    fun catalogSurvivesAnEncodeDecodeRoundTrip() {
        val catalog = VodCatalog(
            movies = listOf(
                dev.lluissunol.iptv.data.model.VodItem(
                    streamId = 1,
                    name = "Película",
                    posterUrl = "http://fake.example:8080/p.jpg",
                    categoryId = "5",
                    rating = 7.5,
                    searchKey = "pelicula",
                    favoriteKey = "tvg:1",
                    playbackUrl = "http://fake.example:8080/movie/user/pass/1.m3u8"
                )
            ),
            series = listOf(
                dev.lluissunol.iptv.data.model.SeriesCard(
                    seriesId = 77,
                    name = "Serie",
                    posterUrl = null,
                    plot = "Trama",
                    categoryId = "3",
                    rating = 0.0,
                    searchKey = "serie",
                    favoriteKey = "series:77"
                )
            ),
            movieCategories = listOf(dev.lluissunol.iptv.data.model.VodCategory("5", "Acción")),
            seriesCategories = emptyList()
        )

        assertEquals(catalog, decodeVodCatalog(encodeVodCatalog(catalog)))
    }

    @Test
    fun corruptCachedCatalogsDegradeToNull() {
        assertNull(decodeVodCatalog("{corrupto"))
        assertNull(decodeVodCatalog("""{"movies": "no soy una lista"}"""))
    }

    // ------------------------------------------------------------- helpers

    private fun channel(name: String, url: String) = dev.lluissunol.iptv.data.model.Channel(
        id = "test",
        name = name,
        url = url,
        group = null,
        logoUrl = null,
        tvgId = null,
        sourceType = dev.lluissunol.iptv.data.model.SourceType.SERIES,
        searchKey = "",
        qualityTag = null,
        favoriteKey = "tvg:test-$name"
    )
}
