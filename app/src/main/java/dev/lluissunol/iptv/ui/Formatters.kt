package dev.lluissunol.iptv.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared number/date formatting so list rows and channel rows agree. */
internal fun formatCompact(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", value / 1_000.0)
    else -> value.toString()
}

internal fun formatNumber(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

/**
 * Resolves the locale's own ordering/separators for a date skeleton such as
 * `ddMM`. Injectable because `android.text.format.DateFormat` is not usable in
 * plain JVM unit tests.
 */
internal fun interface DatePatterns {
    fun best(locale: Locale, skeleton: String): String
}

internal object AndroidDatePatterns : DatePatterns {
    override fun best(locale: Locale, skeleton: String): String =
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
}

private fun timeSkeleton(is24Hour: Boolean) = if (is24Hour) "Hm" else "hm"

private fun format(pattern: String, locale: Locale, epochMs: Long): String =
    SimpleDateFormat(pattern, locale).format(Date(epochMs))

internal fun shortDay(epochMs: Long, patterns: DatePatterns = AndroidDatePatterns): String {
    val locale = Locale.getDefault()
    return format(patterns.best(locale, "ddMMyyyy"), locale, epochMs)
}

internal fun shortDate(
    epochMs: Long,
    is24Hour: Boolean = true,
    patterns: DatePatterns = AndroidDatePatterns
): String {
    val locale = Locale.getDefault()
    val pattern = patterns.best(locale, "ddMM") + " · " + patterns.best(locale, timeSkeleton(is24Hour))
    return format(pattern, locale, epochMs)
}

/** A 0-10 rating with one decimal and the locale's separator; `7.0` reads `7`. */
internal fun formatRating10(value: Double): String {
    // No NumberFormat here: this runs for every poster cell that binds, so it
    // must be allocation-light. Only the decimal separator is locale-specific.
    val tenths = Math.round(value * 10).toInt()
    val fraction = tenths % 10
    return if (fraction == 0) {
        (tenths / 10).toString()
    } else {
        "${tenths / 10}${decimalSeparator()}$fraction"
    }
}

@Volatile private var cachedSeparator: Pair<Locale, Char>? = null

private fun decimalSeparator(): Char {
    val locale = Locale.getDefault()
    cachedSeparator?.let { if (it.first == locale) return it.second }
    val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    cachedSeparator = locale to separator
    return separator
}

/** Whole hours and the remaining minutes of a duration in seconds. */
internal fun formatDurationParts(seconds: Int): Pair<Int, Int> {
    val totalMinutes = (seconds + 30) / 60
    return totalMinutes / 60 to totalMinutes % 60
}

/** How long ago a list was refreshed, as a calendar bucket plus ready-to-show parts. */
internal enum class UpdatedDay { TODAY, YESTERDAY, DATE, OLD_YEAR }

internal data class UpdatedWhen(
    val day: UpdatedDay,
    /** Time of day in the locale's pattern, 24-hour or not as the device is set. */
    val time: String,
    /** Day and month in the locale's order, plus the year for [UpdatedDay.OLD_YEAR]. */
    val date: String
)

/** Buckets [epochMs] against [nowMs] in [zone]; pure so midnight edges are testable. */
internal fun updatedWhen(
    epochMs: Long,
    nowMs: Long,
    locale: Locale = Locale.getDefault(),
    zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    is24Hour: Boolean = true,
    patterns: DatePatterns = AndroidDatePatterns
): UpdatedWhen {
    fun cal(ms: Long) = java.util.Calendar.getInstance(zone, locale).apply { timeInMillis = ms }
    val then = cal(epochMs)
    val now = cal(nowMs)
    val yesterday = cal(nowMs).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    val sameYear = then.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
    val day = when {
        sameDay(then, now) -> UpdatedDay.TODAY
        sameDay(then, yesterday) -> UpdatedDay.YESTERDAY
        sameYear -> UpdatedDay.DATE
        else -> UpdatedDay.OLD_YEAR
    }
    fun fmt(pattern: String) = SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(epochMs))
    return UpdatedWhen(
        day = day,
        time = fmt(patterns.best(locale, timeSkeleton(is24Hour))),
        date = fmt(patterns.best(locale, if (day == UpdatedDay.OLD_YEAR) "ddMMyyyy" else "ddMM"))
    )
}

/** Result of fitting the header's subtitle and actions into the width available. */
internal data class HeaderLayout(val useShortSubtitle: Boolean, val favoritesInOverflow: Boolean)

/**
 * Picks the longest subtitle that fits: long, then short; if neither fits next
 * to the Favoritos button, the button moves to the overflow menu and the
 * measurement is repeated with its 48 dp freed. Widths are in the same unit.
 */
internal fun chooseHeaderLayout(
    longWidth: Float,
    shortWidth: Float,
    availableWithFavorites: Float,
    availableWithoutFavorites: Float
): HeaderLayout = when {
    longWidth <= availableWithFavorites -> HeaderLayout(false, false)
    shortWidth <= availableWithFavorites -> HeaderLayout(true, false)
    longWidth <= availableWithoutFavorites -> HeaderLayout(false, true)
    else -> HeaderLayout(true, true)
}
