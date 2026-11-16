package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.VodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Every row of the language-detection table of the spec, with invented names. */
class CategoryLanguageTest {

    /** Builds categories (id = position) with the given title counts and runs the detection. */
    private fun detect(vararg named: Pair<String, Int>): LanguageFacet? {
        val categories = named.mapIndexed { i, (name, _) -> VodCategory(i.toString(), name) }
        val titles = named.mapIndexed { i, (_, n) -> i.toString() to n }.toMap()
        return detectLanguageFacet(categories, titles, named.sumOf { it.second })
    }

    private fun prefix(name: String) = extractCategoryPrefix(name)?.token

    // ---------------------------------------------------------------- prefixes

    @Test
    fun extractsEveryPrefixPattern() {
        assertEquals("ES", prefix("|ES| Drama"))
        assertEquals("ES", prefix("[ES] Drama"))
        assertEquals("ES", prefix("(ES) Drama"))
        assertEquals("ESPANA", prefix("ESPAÑA - Acción"))
        assertEquals("ENGLISH", prefix("ENGLISH – Action"))
        assertEquals("ENGLISH", prefix("ENGLISH — Action"))
        assertEquals("ES", prefix("ES | Drama"))
        assertEquals("EN", prefix("EN: Comedy"))
        assertEquals("ES", prefix("ES| Drama"))
        assertEquals("Acción", extractCategoryPrefix("ESPAÑA - Acción")?.stripped)
        assertEquals("Drama", extractCategoryPrefix("|ES| Drama")?.stripped)
    }

    @Test
    fun namesWithoutAValidPrefix() {
        listOf(
            "Acción", "Sci-Fi", "Spider-Man Collection", "4K - Movies", "2023 - Estrenos",
            "Star Wars: Saga", "VOD 4K", "", "ES - "
        ).forEach { assertNull("'$it'", prefix(it)) }
        // 17+ characters before the separator is not a prefix.
        assertNull(prefix("ABCDEFGHIJKLMNOPQ - Drama"))
    }

    @Test
    fun tokensAreNormalised() {
        assertEquals("ESPANA", prefix("españa - Drama"))
        assertEquals("UNITED KINGDOM", prefix("United   Kingdom - Drama"))
    }

    @Test
    fun recognisesLanguageTokensAndNothingElse() {
        assertEquals(LanguageOption.ES, recognizeLanguage("ESPANA"))
        assertEquals(LanguageOption.EN, recognizeLanguage("UK"))
        assertEquals(LanguageOption.EN, recognizeLanguage("US"))
        assertEquals(LanguageOption.PT_BR, recognizeLanguage("BRASIL"))
        assertEquals(LanguageOption.LATINO, recognizeLanguage("LATAM"))
        // `VO` is the original version; every subtitled variant is the same option.
        assertEquals(LanguageOption.VO, recognizeLanguage("VO"))
        assertEquals(LanguageOption.VO, recognizeLanguage("VOSE"))
        assertEquals(LanguageOption.VO, recognizeLanguage("SUBTITULADA"))
        // Ambiguous two-letter tokens and IPTV noise are never recognised.
        listOf("CA", "EU", "IN", "HI", "TV", "HD", "FHD", "4K", "VOD", "VIP", "NETFLIX", "HBO")
            .forEach { assertNull(it, recognizeLanguage(it)) }
    }

    // ------------------------------------------------------------ facet table

    @Test
    fun spanishAndEnglishPrefixesOfSeveralCategoriesAreOffered() {
        val facet = detect(
            "ESPAÑA - Acción" to 10, "ESPAÑA - Drama" to 20,
            "ENGLISH - Action" to 15, "ENGLISH - Drama" to 5
        )
        assertNotNull(facet)
        // By title count: ES (30) before EN (20); no "Otros".
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN), facet!!.options)
        assertEquals(LanguageOption.ES, facet.byCategory["0"])
        assertEquals(LanguageOption.EN, facet.byCategory["3"])
        assertEquals("Acción", facet.strippedNames["0"])
    }

    @Test
    fun bracketedPrefixesWithAnOtrosBucket() {
        val facet = detect(
            "|ES| Drama" to 10, "|ES| Comedia" to 10,
            "|EN| Drama" to 10, "|EN| Comedy" to 10,
            "VOD 4K" to 5
        )
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN, LanguageOption.OTHER), facet!!.options)
        assertEquals(LanguageOption.OTHER, facet.byCategory["4"])
    }

    @Test
    fun unprefixedCategoriesHideTheFilter() {
        assertNull(detect("Acción" to 10, "Comedia" to 10, "Drama" to 10))
    }

    @Test
    fun unrecognisedSharedPrefixesHideTheFilter() {
        assertNull(
            detect("NETFLIX - Series" to 10, "NETFLIX - Kids" to 10, "HBO - Series" to 10, "HBO - Docs" to 10)
        )
    }

    @Test
    fun oneRecognisedOptionIsNotEnough() {
        assertNull(detect("ES - Drama" to 90, "ES - Comedia" to 20, "Netflix" to 10))
    }

    @Test
    fun recognisedCoverageBelowHalfHidesTheFilter() {
        assertNull(
            detect(
                "ES - Drama" to 20, "ES - Comedia" to 10, "EN - Drama" to 10, "EN - Comedy" to 5,
                "Netflix" to 30, "HBO" to 20, "4K" to 5
            )
        )
    }

    @Test
    fun prefixesMustBeSharedBySeveralCategories() {
        // One category per language only repeats the category chips.
        assertNull(detect("ES - Cine" to 10, "EN - Movies" to 10))
        // A single option that spans two categories is enough.
        val facet = detect("ES - Cine" to 10, "ES - Series" to 10, "EN - Movies" to 10)
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN), facet!!.options)
    }

    @Test
    fun differentPrefixesCanMergeIntoOneOption() {
        val facet = detect("UK - Drama" to 10, "US - Drama" to 10, "ES - Drama" to 10, "ES - Acción" to 10)
        // UK and US are one option: English (20 titles) ties with Spanish (20), enum order wins.
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN), facet!!.options)
    }

    @Test
    fun hyphenWithoutSpacesAndDigitsDoNotCreatePrefixes() {
        val facet = detect(
            "Star Wars: Saga" to 10, "ES - Drama" to 10, "ES - Acción" to 10, "EN - Drama" to 10
        )
        assertEquals(listOf(LanguageOption.ES, LanguageOption.EN, LanguageOption.OTHER), facet!!.options)
        assertEquals(LanguageOption.OTHER, facet.byCategory["0"])
        assertNull(detect("4K - Movies" to 10, "2023 - Estrenos" to 10, "Sci-Fi" to 10))
    }

    @Test
    fun dualPrefixFallsIntoOtros() {
        val facet = detect(
            "ES/EN - Dual" to 5, "ES - Drama" to 10, "ES - Acción" to 10, "EN - Drama" to 10
        )
        assertEquals(LanguageOption.OTHER, facet!!.byCategory["0"])
        assertEquals(LanguageOption.OTHER, facet.options.last())
    }

    @Test
    fun titlesOfUnknownCategoriesCountAsOtros() {
        val categories = listOf(
            VodCategory("1", "ES - A"), VodCategory("2", "ES - B"), VodCategory("3", "EN - A")
        )
        val facet = detectLanguageFacet(categories, mapOf("1" to 10, "2" to 10, "3" to 10, "999" to 4), 34)
        assertEquals(LanguageOption.OTHER, facet!!.options.last())
    }

    @Test
    fun emptyCatalogueHasNoFacet() {
        assertNull(detectLanguageFacet(emptyList(), emptyMap(), 0))
    }

    @Test
    fun languageIdsRoundTrip() {
        LanguageOption.entries.forEach { assertEquals(it, LanguageOption.fromId(it.id)) }
        assertNull(LanguageOption.fromId("klingon"))
        assertNull(LanguageOption.fromId(null))
    }
}
