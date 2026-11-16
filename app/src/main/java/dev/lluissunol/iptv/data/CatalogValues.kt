package dev.lluissunol.iptv.data

/**
 * Tolerant parsing of the catalogue metadata an Xtream panel sends. Every
 * function is pure (the clock is a parameter) and degrades a bad value to
 * "unknown" (0) instead of throwing, so one malformed field never drops an item
 * nor fails a load. No `java.time` (minSdk 24 without desugaring) and no
 * `SimpleDateFormat` (slow, not thread-safe, 100k calls): dates are plain
 * integer civil-date math.
 */

/**
 * Version of the persisted catalogue shape. A cache written by an older build
 * decodes with `schemaVersion = 0` (the field is absent from its JSON), which is
 * how the app knows to ask for a refresh.
 */
const val CATALOG_SCHEMA_VERSION = 1

private const val SECONDS_PER_DAY = 86_400L
private const val MS_THRESHOLD = 100_000_000_000L
private const val MIN_EPOCH_SECONDS = 100_000_000L
private const val FUTURE_SLACK_SECONDS = 2 * SECONDS_PER_DAY
private const val MIN_YEAR = 1900

private val PLACEHOLDERS = setOf("", "0", "n/a", "na", "null", "none", "-", "undefined", "nan", "false")

/** Trimmed text, or null for a placeholder meaning "no value". */
private fun usable(raw: String?): String? {
    val text = raw?.trim() ?: return null
    if (text.lowercase() in PLACEHOLDERS) return null
    if (text.startsWith("0000-00-00") || text.startsWith("0000/00/00")) return null
    return text
}

/** The current UTC year, for callers that have no injected clock. */
fun currentYearUtc(nowEpochSec: Long = System.currentTimeMillis() / 1000): Int {
    val days = Math.floorDiv(nowEpochSec, SECONDS_PER_DAY)
    return civilFromDays(days).first
}

/** Days since 1970-01-01 of a proleptic Gregorian date (Hinnant's algorithm). */
internal fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val year = if (m <= 2) y - 1 else y
    val era = (if (year >= 0) year else year - 399) / 400
    val yoe = year - era * 400
    val mp = if (m > 2) m - 3 else m + 9
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146_097L + doe - 719_468L
}

/** Inverse of [daysFromCivil]: year, month (1-12), day (1-31). */
internal fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719_468L
    val era = Math.floorDiv(z, 146_097L)
    val doe = (z - era * 146_097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
}

/** A date read from text. [month]/[day] are 0 when only the year is certain. */
internal class ParsedDate(val year: Int, val month: Int, val day: Int, val secondsOfDay: Int) {
    val hasDay: Boolean get() = month in 1..12 && day in 1..31
}

private const val TIME = """(?:[T ](\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(?:Z|[+-]\d{2}:?\d{2})?)?"""
private val YMD = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$TIME$""")
private val ABY = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$TIME$""")
private val YEAR_ONLY = Regex("""^\d{4}$""")
private val YEAR_IN_TEXT = Regex("""(?<!\d)(19|20)\d{2}(?!\d)""")

private fun timeOfDay(h: String?, m: String?, s: String?): Int {
    val hh = h?.toIntOrNull() ?: return 0
    val mm = m?.toIntOrNull() ?: 0
    val ss = s?.toIntOrNull() ?: 0
    return if (hh in 0..23 && mm in 0..59 && ss in 0..60) hh * 3600 + mm * 60 + ss else 0
}

/**
 * Parses a date string. For `a/b/YYYY` the order is decided by the numbers
 * (a > 12 is DD/MM, b > 12 is MM/DD) and an ambiguous pair keeps only the year:
 * a wrong month is worse than an unknown one.
 */
internal fun parseDateText(text: String, dayFirst: Boolean? = null): ParsedDate? {
    YMD.matchEntire(text)?.let { m ->
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        val sod = timeOfDay(m.groups[4]?.value, m.groups[5]?.value, m.groups[6]?.value)
        return if (mo in 1..12 && d in 1..31) ParsedDate(y, mo, d, sod) else ParsedDate(y, 0, 0, 0)
    }
    ABY.matchEntire(text)?.let { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val y = m.groupValues[3].toInt()
        val sod = timeOfDay(m.groups[4]?.value, m.groups[5]?.value, m.groups[6]?.value)
        return when {
            a > 12 && b in 1..12 && a <= 31 -> ParsedDate(y, b, a, sod)
            b > 12 && a in 1..12 && b <= 31 -> ParsedDate(y, a, b, sod)
            // Both <= 12: only a catalogue-wide inference ([detectDayFirst]) may decide.
            dayFirst == true && a in 1..12 && b in 1..12 -> ParsedDate(y, b, a, sod)
            dayFirst == false && a in 1..12 && b in 1..12 -> ParsedDate(y, a, b, sod)
            else -> ParsedDate(y, 0, 0, 0)
        }
    }
    if (YEAR_ONLY.matches(text)) return ParsedDate(text.toInt(), 0, 0, 0)
    YEAR_IN_TEXT.find(text)?.let { return ParsedDate(it.value.toInt(), 0, 0, 0) }
    return null
}

/** Digits of a numeric value (`"1700000000"`, `"1700000000.0"`), or null. */
private fun integerText(text: String): String? {
    val whole = text.substringBefore('.')
    return whole.takeIf { it.isNotEmpty() && it.all(Char::isDigit) &&
        text.substringAfter('.', "").all(Char::isDigit) }
}

private val NUMERIC_DATE = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.]\d{4}""")

/**
 * Infers the order of `a/b/YYYY` dates ONCE for a whole field: if any value has
 * a first number above 12 the field is DD/MM (true), if any has a second number
 * above 12 it is MM/DD (false), otherwise (or when they disagree) unknown. This
 * keeps the ~40% of dates whose day is 12 or less instead of dropping them.
 */
fun detectDayFirst(samples: Sequence<String?>): Boolean? {
    var dayFirst = false
    var monthFirst = false
    for (raw in samples) {
        val m = NUMERIC_DATE.find(raw?.trim() ?: continue) ?: continue
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        if (a > 12 && b <= 12) dayFirst = true
        if (b > 12 && a <= 12) monthFirst = true
        if (dayFirst && monthFirst) return null
    }
    return if (dayFirst) true else if (monthFirst) false else null
}

/**
 * An `added` value as epoch seconds; 0 means unknown. Accepts epoch seconds,
 * epoch milliseconds (> 1e11) and date strings (read as UTC). A value more than
 * two days in the future, a date with no certain day, or garbage is unknown.
 */
fun parseEpochSeconds(raw: String?, nowEpochSec: Long, dayFirst: Boolean? = null): Long {
    val text = usable(raw) ?: return 0L
    val digits = integerText(text)
    val seconds: Long = if (digits != null) {
        if (digits.length == 8) {
            // yyyymmdd
            val y = digits.substring(0, 4).toInt()
            val mo = digits.substring(4, 6).toInt()
            val d = digits.substring(6, 8).toInt()
            if (y in MIN_YEAR..2200 && mo in 1..12 && d in 1..31) {
                daysFromCivil(y, mo, d) * SECONDS_PER_DAY
            } else {
                return 0L
            }
        } else {
            val value = digits.toLongOrNull() ?: return 0L
            val s = if (value > MS_THRESHOLD) value / 1000 else value
            if (s < MIN_EPOCH_SECONDS) return 0L
            s
        }
    } else {
        val date = parseDateText(text, dayFirst) ?: return 0L
        if (!date.hasDay || date.year < 1970) return 0L
        daysFromCivil(date.year, date.month, date.day) * SECONDS_PER_DAY + date.secondsOfDay
    }
    return if (seconds > nowEpochSec + FUTURE_SLACK_SECONDS) 0L else seconds
}

/**
 * A release date as `yyyymmdd`, `yyyymm00` or `yyyy0000` (year only); 0 means
 * unknown. A year outside 1900..[currentYear]+1 is unknown, which drops `0000`,
 * `1800` and `2999` placeholders.
 */
fun parseReleaseKey(raw: String?, currentYear: Int): Int {
    val text = usable(raw) ?: return 0
    val digits = integerText(text)
    var key = 0
    if (digits != null) {
        key = when {
            digits.length == 4 -> digits.toInt() * 10_000
            digits.length == 8 -> {
                val y = digits.substring(0, 4).toInt()
                val mo = digits.substring(4, 6).toInt()
                val d = digits.substring(6, 8).toInt()
                if (mo in 1..12 && d in 1..31) y * 10_000 + mo * 100 + d else y * 10_000
            }
            else -> {
                // A release given as a timestamp.
                val value = digits.toLongOrNull() ?: return 0
                val s = if (value > MS_THRESHOLD) value / 1000 else value
                if (s < MIN_EPOCH_SECONDS) return 0
                val (y, m, d) = civilFromDays(Math.floorDiv(s, SECONDS_PER_DAY))
                y * 10_000 + m * 100 + d
            }
        }
    } else {
        val date = parseDateText(text) ?: return 0
        key = if (date.hasDay) date.year * 10_000 + date.month * 100 + date.day else date.year * 10_000
    }
    val year = key / 10_000
    return if (year in MIN_YEAR..(currentYear + 1)) key else 0
}

/** The first known release key among [raws] (the aliases of one field), or 0. */
fun firstReleaseKey(raws: List<String?>, currentYear: Int): Int {
    for (raw in raws) {
        val key = parseReleaseKey(raw, currentYear)
        if (key != 0) return key
    }
    return 0
}

/** The year of a release key, or 0 when unknown. */
fun releaseYear(key: Int): Int = key / 10_000

/** A rating written as text (`"7.4"`, `"7,4"`); 0.0 means unknown. */
fun parseRatingText(raw: String?): Double {
    val text = usable(raw) ?: return 0.0
    val value = text.replace(',', '.').toDoubleOrNull() ?: return 0.0
    return if (value.isFinite()) value else 0.0
}

/**
 * A rating on a 0-10 scale; 0.0 means unknown. A `rating_5based` in (0, 5] wins
 * because it is finer-grained than an integer `rating`; a `rating` in (10, 100]
 * is a percentage and is divided by 10; anything else is unknown.
 */
fun normalizeRating10(rating: Double, rating5Based: String?): Double {
    val five = parseRatingText(rating5Based)
    if (five > 0.0 && five <= 5.0) return five * 2.0
    if (!rating.isFinite() || rating <= 0.0 || rating > 100.0) return 0.0
    return if (rating > 10.0) rating / 10.0 else rating
}

private val FIRST_NUMBER = Regex("""\d+""")

/**
 * Episode runtime in minutes: `"45"`, `"45 min"`, or seconds (`"2700"`, any
 * value above 300 is taken as seconds). 0 means unknown.
 */
fun parseRuntimeMinutes(raw: String?): Int {
    val text = usable(raw) ?: return 0
    if (':' in text) return parseDurationSeconds(null, text) / 60
    val n = FIRST_NUMBER.find(text)?.value?.toLongOrNull() ?: return 0
    val minutes = if (n > 300) n / 60 else n
    return if (minutes in 1..10_000) minutes.toInt() else 0
}

/**
 * A movie duration in seconds from `duration_secs` (seconds) or `duration`
 * (`HH:MM:SS`, `MM:SS`, or a bare number: seconds above 300, else minutes).
 */
fun parseDurationSeconds(durationSecs: String?, duration: String?): Int {
    usable(durationSecs)?.let { text ->
        integerText(text)?.toLongOrNull()?.takeIf { it > 0 }?.let { return it.coerceAtMost(1_000_000L).toInt() }
    }
    val text = usable(duration) ?: return 0
    if (':' in text) {
        val parts = text.split(':').map { it.trim().toIntOrNull() ?: return 0 }
        val seconds = when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
        return seconds.coerceIn(0, 1_000_000)
    }
    val n = integerText(text)?.toLongOrNull() ?: return 0
    val seconds = if (n > 300) n else n * 60
    return seconds.coerceIn(0, 1_000_000).toInt()
}
