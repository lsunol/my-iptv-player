package dev.lluissunol.iptv.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HeaderFormattersTest {
    private val zone = TimeZone.getTimeZone("Europe/Madrid")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private val patterns = DatePatterns { _, skeleton ->
        when (skeleton) {
            "ddMM" -> "dd/MM"
            "ddMMyyyy" -> "dd/MM/yyyy"
            "Hm" -> "HH:mm"
            else -> "h:mm a"
        }
    }

    private fun whenOf(then: Long, now: Long, is24Hour: Boolean = true) =
        updatedWhen(then, now, Locale.US, zone, is24Hour, patterns)

    @Test
    fun patternsComeFromTheLocaleAndTheClockSetting() {
        val us = DatePatterns { _, skeleton -> if (skeleton == "ddMM") "MM/dd" else "h:mm a" }
        val r = updatedWhen(at(2026, 3, 5, 18, 7), at(2026, 11, 16, 10, 0), Locale.US, zone, false, us)
        assertEquals("03/05", r.date)
        assertEquals("6:07 PM", r.time)
        assertEquals("6:07 PM", whenOf(at(2026, 3, 5, 18, 7), at(2026, 3, 5, 20, 0), false).time)
    }

    @Test
    fun today() {
        val r = whenOf(at(2026, 11, 16, 18, 33), at(2026, 11, 16, 21, 0))
        assertEquals(UpdatedDay.TODAY, r.day)
        assertEquals("18:33", r.time)
    }

    @Test
    fun yesterday() {
        val r = whenOf(at(2026, 11, 15, 23, 59), at(2026, 11, 16, 0, 1))
        assertEquals(UpdatedDay.YESTERDAY, r.day)
    }

    @Test
    fun sameYearOlder() {
        val r = whenOf(at(2026, 3, 5, 8, 7), at(2026, 11, 16, 10, 0))
        assertEquals(UpdatedDay.DATE, r.day)
        assertEquals("05/03", r.date)
        assertEquals("08:07", r.time)
    }

    @Test
    fun olderYear() {
        val r = whenOf(at(2024, 12, 31, 12, 0), at(2026, 1, 1, 12, 0))
        assertEquals(UpdatedDay.OLD_YEAR, r.day)
        assertEquals("31/12/2024", r.date)
    }

    @Test
    fun yesterdayAcrossYearBoundary() {
        val r = whenOf(at(2025, 12, 31, 22, 0), at(2026, 1, 1, 9, 0))
        assertEquals(UpdatedDay.YESTERDAY, r.day)
    }

    @Test
    fun midnightDependsOnTimeZone() {
        // 23:30 UTC on the 15th is 00:30 on the 16th in Madrid (CET, UTC+1).
        val then = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, 10, 15, 23, 30)
        }.timeInMillis
        val now = at(2026, 11, 16, 12, 0)
        assertEquals(UpdatedDay.TODAY, updatedWhen(then, now, Locale.US, zone, true, patterns).day)
        assertEquals(
            UpdatedDay.YESTERDAY,
            updatedWhen(then, now, Locale.US, TimeZone.getTimeZone("UTC"), true, patterns).day
        )
    }

    @Test
    fun headerLayoutPrefersLongThenShortThenOverflow() {
        assertEquals(HeaderLayout(false, false), chooseHeaderLayout(100f, 60f, 120f, 170f))
        assertEquals(HeaderLayout(true, false), chooseHeaderLayout(100f, 60f, 80f, 130f))
        assertEquals(HeaderLayout(false, true), chooseHeaderLayout(100f, 60f, 50f, 110f))
        assertEquals(HeaderLayout(true, true), chooseHeaderLayout(100f, 60f, 40f, 70f))
        // Short does not fit even without Favoritos: still short, ellipsized as a last resort.
        assertEquals(HeaderLayout(true, true), chooseHeaderLayout(100f, 60f, 20f, 40f))
    }
}
