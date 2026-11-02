package dev.lluissunol.iptv.ui.series

import dev.lluissunol.iptv.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drill-down's `LazyColumn` keys. A duplicate key is a composition crash,
 * not a cosmetic glitch, and the episode data comes from the provider, so the
 * helper must stay unique whatever it is handed.
 */
class EpisodeKeysTest {

    private fun episode(id: String, season: Int, number: Int, title: String = "Capítulo") = Episode(
        id = id,
        season = season,
        episodeNum = number,
        title = title,
        playbackUrl = "http://fake.example/series/u/p/$id.m3u8",
        favoriteKey = "url:$id"
    )

    @Test
    fun keysAreUniqueWhenTheProviderRepeatsAnEpisode() {
        val episodes = listOf(
            episode("1001", 1, 1),
            // Same season, same number, same id: a provider repeating itself.
            episode("1001", 1, 1),
            episode("1002", 1, 2)
        )

        val keys = uniqueEpisodeKeys(episodes)

        assertEquals(episodes.size, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("1:1:1001", keys[0])
        assertEquals("1:2:1002", keys[2])
        // The repeated one is disambiguated, never dropped.
        assertEquals(listOf("1:1:1001", "1:1:1001#1", "1:2:1002"), keys)
    }

    @Test
    fun keysStayCollisionFreeAcrossSeasons() {
        val episodes = listOf(
            episode("7", 1, 1),
            episode("7", 2, 1)
        )

        val keys = uniqueEpisodeKeys(episodes)

        assertEquals(listOf("1:1:7", "2:1:7"), keys)
    }

    @Test
    fun anIdThatLooksLikeADisambiguatedKeyDoesNotCollide() {
        val episodes = listOf(
            episode("7", 1, 1),
            episode("7", 1, 1),
            // Its own id already carries the `#` suffix the helper appends.
            episode("7#1", 1, 1)
        )

        val keys = uniqueEpisodeKeys(episodes)

        assertEquals(listOf("1:1:7", "1:1:7#1", "1:1:7#1#2"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun anEmptySeasonProducesNoKeys() {
        assertEquals(emptyList<String>(), uniqueEpisodeKeys(emptyList()))
    }
}
