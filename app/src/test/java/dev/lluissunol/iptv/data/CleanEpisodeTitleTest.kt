package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The provider is the one filling `title`, and the panels are wildly
 * inconsistent: some send the episode name, some the whole line the M3U's
 * `tvg-name` also carries. The row renders this string verbatim, so it has to
 * survive both shapes without eating a legitimate dash or colon.
 */
class CleanEpisodeTitleTest {

    @Test
    fun `full provider line keeps only the episode name`() {
        assertEquals(
            "Super Power Tech",
            cleanEpisodeTitle(
                "White Rabbit Project - S01E01 - Super Power Tech",
                "White Rabbit Project",
                1
            )
        )
    }

    @Test
    fun `leading series name without a code is dropped`() {
        assertEquals(
            "Heist!",
            cleanEpisodeTitle("White Rabbit Project - Heist!", "White Rabbit Project", 5)
        )
    }

    @Test
    fun `leading code without the series name is dropped`() {
        assertEquals("Heist!", cleanEpisodeTitle("S01E05 - Heist!", "White Rabbit Project", 5))
    }

    @Test
    fun `already clean titles are untouched`() {
        assertEquals("Heist!", cleanEpisodeTitle("Heist!", "White Rabbit Project", 5))
    }

    @Test
    fun `a shared prefix that is not the series name is kept`() {
        // The name is a prefix of the title but is not followed by a separator,
        // so it is not the series name and nothing may be stripped.
        assertEquals(
            "White Rabbit Projection Theory",
            cleanEpisodeTitle("White Rabbit Projection Theory", "White Rabbit Project", 3)
        )
    }

    @Test
    fun `a title that is only the series name falls back`() {
        assertEquals(
            "Episodio 2",
            cleanEpisodeTitle("White Rabbit Project", "White Rabbit Project", 2)
        )
    }

    @Test
    fun `a separator inside a legitimate title is kept`() {
        assertEquals(
            "Spider-Man: Homecoming",
            cleanEpisodeTitle("Spider-Man: Homecoming", "Other Show", 1)
        )
    }

    @Test
    fun `the 1x01 spelling is handled`() {
        assertEquals("Pilot", cleanEpisodeTitle("Some Show 1x01 - Pilot", "Some Show", 1))
    }

    @Test
    fun `a trailing code with nothing left falls back to the episode number`() {
        assertEquals(
            "Episodio 7",
            cleanEpisodeTitle("White Rabbit Project - S01E07", "White Rabbit Project", 7)
        )
    }

    @Test
    fun `blank and missing titles fall back to the episode number`() {
        assertEquals("Episodio 4", cleanEpisodeTitle("", "Some Show", 4))
        assertEquals("Episodio 4", cleanEpisodeTitle("   ", "Some Show", 4))
        assertEquals("Episodio 4", cleanEpisodeTitle("Some Show - ", "Some Show", 4))
    }

    @Test
    fun `a null series name still strips the code`() {
        assertEquals("Pilot", cleanEpisodeTitle("S02E01 - Pilot", null, 1))
    }
}
