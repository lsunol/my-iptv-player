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
