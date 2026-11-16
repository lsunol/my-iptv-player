package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every metadata shape an Xtream panel may send, degrading to "unknown" (0). */
class CatalogValuesTest {

    private val now = 1_731_000_000L // 2024-11-07 UTC
    private val year = 2024

    // ------------------------------------------------------------ civil dates

    @Test
    fun civilDateMathMatchesKnownDays() {
        assertEquals(0L, daysFromCivil(1970, 1, 1))
        assertEquals(11_017L, daysFromCivil(2000, 3, 1))
        assertEquals(19_696L, daysFromCivil(2023, 12, 5))
        assertEquals(Triple(2023, 12, 5), civilFromDays(19_696L))
        assertEquals(Triple(2000, 2, 29), civilFromDays(daysFromCivil(2000, 2, 29)))
        assertEquals(2024, currentYearUtc(now))
    }

    // ------------------------------------------------------------------ added

    @Test
    fun addedAcceptsSecondsMillisAndDateStrings() {
        assertEquals(1_701_770_400L, parseEpochSeconds("1701770400", now))
        assertEquals(1_701_770_400L, parseEpochSeconds("1701770400000", now))
        assertEquals(1_701_770_400L, parseEpochSeconds("1701770400.0", now))
        assertEquals(1_701_770_400L, parseEpochSeconds("2023-12-05 10:00:00", now))
        assertEquals(1_701_770_400L, parseEpochSeconds("2023-12-05T10:00:00Z", now))
        assertEquals(1_701_734_400L, parseEpochSeconds("2023-12-05", now))
        // 25 > 12 can only be the day; 12/25 is MM/DD.
        assertEquals(1_703_462_400L, parseEpochSeconds("25/12/2023", now))
        assertEquals(1_703_462_400L, parseEpochSeconds("12/25/2023", now))
    }

    @Test
    fun addedDegradesToUnknown() {
        listOf(null, "", " ", "0", "N/A", "null", "none", "0000-00-00", "abc", "-5", "42", "0000-00-00 00:00:00")
            .forEach { assertEquals("'$it'", 0L, parseEpochSeconds(it, now)) }
        // More than two days in the future.
        assertEquals(0L, parseEpochSeconds("4102444800", now))
        assertEquals(0L, parseEpochSeconds("2999-01-01", now))
        // Ambiguous day/month for an `added` stamp: no certain day, so unknown.
        assertEquals(0L, parseEpochSeconds("05/06/2023", now))
        // Slightly ahead of the clock is tolerated (time zones).
        assertEquals(now + 3600, parseEpochSeconds((now + 3600).toString(), now))
    }

    // ---------------------------------------------------------------- release

    @Test
    fun releaseAcceptsEveryDocumentedShape() {
        assertEquals(20_161_019, parseReleaseKey("2016-10-19", year))
        assertEquals(20_161_019, parseReleaseKey("2016-10-19 12:30:00", year))
        assertEquals(20_161_019, parseReleaseKey("2016-10-19T00:00:00Z", year))
        assertEquals(20_161_019, parseReleaseKey("2016/10/19", year))
        assertEquals(20_160_000, parseReleaseKey("2016", year))
        assertEquals(20_160_000, parseReleaseKey(" 2016 ", year))
        assertEquals(20_161_019, parseReleaseKey("20161019", year))
        // A timestamp as release (seconds and milliseconds).
        assertEquals(20_231_205, parseReleaseKey("1701770400", year))
        assertEquals(20_231_205, parseReleaseKey("1701770400000", year))
        // Last-resort standalone year inside free text.
        assertEquals(20_160_000, parseReleaseKey("Estrenada en 2016 (EE.UU.)", year))
    }

    @Test
    fun releaseDayMonthOrderIsDecidedByTheNumbers() {
        // a > 12: DD/MM/YYYY.
        assertEquals(20_161_019, parseReleaseKey("19/10/2016", year))
        assertEquals(20_161_019, parseReleaseKey("19-10-2016", year))
        assertEquals(20_161_019, parseReleaseKey("19.10.2016", year))
        // b > 12: MM/DD/YYYY (US panels).
        assertEquals(20_161_019, parseReleaseKey("10/19/2016", year))
        // Both <= 12: ambiguous, so only the year is kept (never a wrong month).
        assertEquals(20_160_000, parseReleaseKey("05/06/2016", year))
    }

    @Test
    fun releaseDegradesToUnknown() {
        listOf(null, "", "0", "N/A", "0000-00-00", "0000", "1800", "2999", "abc", "[]", "1899")
            .forEach { assertEquals("'$it'", 0, parseReleaseKey(it, year)) }
        // Year bounds: 1900 up to next year.
        assertEquals(19_000_000, parseReleaseKey("1900", year))
        assertEquals(20_250_000, parseReleaseKey("2025", year))
        assertEquals(0, parseReleaseKey("2026", year))
    }

    @Test
    fun invalidMonthOrDayKeepsOnlyTheYear() {
        assertEquals(20_160_000, parseReleaseKey("2016-13-45", year))
    }

    @Test
    fun firstReleaseKeyTakesTheFirstKnownAlias() {
        assertEquals(20_100_000, firstReleaseKey(listOf(null, "0000-00-00", "2010", "2015"), year))
        assertEquals(0, firstReleaseKey(listOf(null, "", "N/A"), year))
        assertEquals(0, firstReleaseKey(emptyList(), year))
        assertEquals(2016, releaseYear(20_161_019))
        assertEquals(0, releaseYear(0))
    }

    // ----------------------------------------------------------------- rating

    @Test
    fun ratingTextAcceptsDecimalCommas() {
        assertEquals(7.4, parseRatingText("7,4"), 1e-9)
        assertEquals(7.4, parseRatingText("7.4"), 1e-9)
        assertEquals(0.0, parseRatingText("N/A"), 0.0)
        assertEquals(0.0, parseRatingText("abc"), 0.0)
        assertEquals(0.0, parseRatingText(null), 0.0)
        assertEquals(0.0, parseRatingText("NaN"), 0.0)
    }

    @Test
    fun ratingNormalisationPrefersTheFinerFiveBasedScale() {
        assertEquals(7.4, normalizeRating10(7.0, "3.7"), 1e-9)
        assertEquals(10.0, normalizeRating10(0.0, "5"), 1e-9)
        // 5-based out of range falls back to the 10 scale.
        assertEquals(7.0, normalizeRating10(7.0, "0"), 1e-9)
        assertEquals(7.0, normalizeRating10(7.0, "9"), 1e-9)
        assertEquals(7.0, normalizeRating10(7.0, null), 1e-9)
        // Percentages.
        assertEquals(8.5, normalizeRating10(85.0, null), 1e-9)
        assertEquals(10.0, normalizeRating10(10.0, null), 1e-9)
        // Unknowns.
        assertEquals(0.0, normalizeRating10(-1.0, null), 0.0)
        assertEquals(0.0, normalizeRating10(0.0, null), 0.0)
        assertEquals(0.0, normalizeRating10(101.0, null), 0.0)
        assertEquals(0.0, normalizeRating10(Double.NaN, null), 0.0)
    }

    // --------------------------------------------------------------- durations

    @Test
    fun runtimeAcceptsMinutesAndSeconds() {
        assertEquals(45, parseRuntimeMinutes("45"))
        assertEquals(45, parseRuntimeMinutes("45 min"))
        assertEquals(45, parseRuntimeMinutes("2700"))
        assertEquals(300, parseRuntimeMinutes("300"))
        assertEquals(90, parseRuntimeMinutes("01:30:00"))
        assertEquals(0, parseRuntimeMinutes("0"))
        assertEquals(0, parseRuntimeMinutes("N/A"))
        assertEquals(0, parseRuntimeMinutes(null))
        assertEquals(0, parseRuntimeMinutes("sin dato"))
    }

    @Test
    fun movieDurationAcceptsSecondsAndClockFormats() {
        assertEquals(6720, parseDurationSeconds("6720", null))
        assertEquals(6720, parseDurationSeconds("6720.0", "irrelevant"))
        assertEquals(6720, parseDurationSeconds(null, "01:52:00"))
        assertEquals(3120, parseDurationSeconds("0", "52:00"))
        assertEquals(6720, parseDurationSeconds("", "6720"))
        // A small bare number is minutes.
        assertEquals(6720, parseDurationSeconds(null, "112"))
        assertEquals(0, parseDurationSeconds(null, "N/A"))
        assertEquals(0, parseDurationSeconds(null, "aa:bb"))
        assertEquals(0, parseDurationSeconds(null, null))
    }
}
