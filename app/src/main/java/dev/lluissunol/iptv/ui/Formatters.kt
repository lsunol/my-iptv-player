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

internal fun shortDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault()).format(Date(epochMs))

/** A 0-10 rating with one decimal and the locale's separator; `7.0` reads `7`. */
internal fun formatRating10(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    return format.format(rounded)
}

/** Whole hours and the remaining minutes of a duration in seconds. */
internal fun formatDurationParts(seconds: Int): Pair<Int, Int> {
    val totalMinutes = (seconds + 30) / 60
    return totalMinutes / 60 to totalMinutes % 60
}
