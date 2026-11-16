package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreNormalizerTest {

    private fun known(vararg genres: CanonicalGenre) = genres.map { GenreLabel.Known(it) }

    @Test
    fun hasThirtyFourCanonicalGenres() {
        assertEquals(34, CanonicalGenre.entries.size)
    }

    @Test
    fun splitsOnIrregularSeparators() {
        assertEquals(listOf("Crime", "Drama", "Thriller"), GenreNormalizer.tokens("Crime /  Drama / Thriller"))
        assertEquals(listOf("A", "B", "C", "D"), GenreNormalizer.tokens("A, B| C ;D"))
        assertEquals(
            known(CanonicalGenre.CRIME, CanonicalGenre.DRAMA, CanonicalGenre.THRILLER),
            GenreNormalizer.labels("Crime /  Drama / Thriller")
        )
    }

    @Test
    fun englishAndSpanishNamesMerge() {
        assertEquals(known(CanonicalGenre.CRIME), GenreNormalizer.labels("Crimen"))
        assertEquals(known(CanonicalGenre.CRIME), GenreNormalizer.labels("crime"))
        assertEquals(known(CanonicalGenre.THRILLER), GenreNormalizer.labels("Suspense"))
        assertEquals(known(CanonicalGenre.THRILLER), GenreNormalizer.labels("THRILLER"))
        assertEquals(known(CanonicalGenre.ANIMATION), GenreNormalizer.labels("Animación"))
        assertEquals(known(CanonicalGenre.SCIFI), GenreNormalizer.labels("Sci-Fi"))
        assertEquals(known(CanonicalGenre.SCIFI), GenreNormalizer.labels("Ciencia ficción"))
    }

    @Test
    fun compoundGenresAreLookedUpBeforeSplittingOnAmpersand() {
        assertEquals(
            known(CanonicalGenre.ACTION, CanonicalGenre.ADVENTURE),
            GenreNormalizer.labels("Action & Adventure")
        )
        assertEquals(
            known(CanonicalGenre.ACTION, CanonicalGenre.ADVENTURE),
            GenreNormalizer.labels("Acción y Aventura")
        )
        assertEquals(
            known(CanonicalGenre.SCIFI, CanonicalGenre.FANTASY),
            GenreNormalizer.labels("Sci-Fi & Fantasy")
        )
        assertEquals(
            known(CanonicalGenre.WAR, CanonicalGenre.POLITICS),
            GenreNormalizer.labels("War & Politics")
        )
        // A recognised pair that is not a table entry still splits on `&`.
        assertEquals(
            known(CanonicalGenre.DRAMA, CanonicalGenre.MYSTERY),
            GenreNormalizer.labels("Drama & Mystery")
        )
    }

    @Test
    fun unknownGenresPassThroughOnceAndFirstLetterUppercased() {
        assertEquals(listOf(GenreLabel.Other("Anime")), GenreNormalizer.labels("anime"))
        // Same genre in two spellings inside one string: one label.
        assertEquals(listOf(GenreLabel.Other("Anime")), GenreNormalizer.labels("anime / ANIME / Ánime"))
        assertEquals(
            known(CanonicalGenre.DRAMA) + GenreLabel.Other("Foo"),
            GenreNormalizer.labels("Drama & Foo")
        )
        // `R&B` is not two genres.
        assertEquals(listOf(GenreLabel.Other("R&B")), GenreNormalizer.labels("R&B"))
    }

    @Test
    fun junkTokensAreDropped() {
        listOf(null, "", "  ", "N/A", "na", "null", "None", "-", "0", "123", "a".repeat(31) + " b")
            .forEach { assertTrue("'$it'", GenreNormalizer.labels(it).isEmpty()) }
        // A 30-character token is still fine.
        assertEquals(1, GenreNormalizer.labels("x".repeat(30)).size)
        assertEquals(known(CanonicalGenre.DRAMA), GenreNormalizer.labels("N/A, Drama, 2023"))
    }

    @Test
    fun foldKeyIgnoresAccentsCaseAndDashes() {
        assertEquals("ciencia ficcion", GenreNormalizer.foldKey("  Ciencia-Ficción "))
        assertEquals("sci fi", GenreNormalizer.foldKey("Sci_Fi"))
    }
}
