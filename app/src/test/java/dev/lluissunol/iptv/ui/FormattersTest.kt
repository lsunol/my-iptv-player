package dev.lluissunol.iptv.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormattersTest {
    private lateinit var previous: Locale

    @Before
    fun saveLocale() {
        previous = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previous)
    }

    @Test
    fun wholeRatingsHaveNoDecimals() {
        Locale.setDefault(Locale("es", "ES"))
        assertEquals("7", formatRating10(7.0))
        assertEquals("10", formatRating10(10.0))
        assertEquals("0", formatRating10(0.0))
    }

    @Test
    fun fractionalRatingsUseOneDecimalAndTheLocaleSeparator() {
        Locale.setDefault(Locale("es", "ES"))
        assertEquals("7,5", formatRating10(7.5))
        assertEquals("3,8", formatRating10(3.84))
        Locale.setDefault(Locale.US)
        assertEquals("7.5", formatRating10(7.5))
        // Rounds to a whole number rather than printing "8.0".
        assertEquals("8", formatRating10(7.96))
    }
}
