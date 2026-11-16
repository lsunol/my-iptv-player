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

internal fun shortDay(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epochMs))

internal fun shortDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault()).format(Date(epochMs))

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
