package app.opentv.data

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes channel names and search queries:
 * "Fútbol" -> "futbol", "M+ LaLiga TV" -> "m laliga tv".
 */
object SearchNormalizer {
    private val marks = Regex("\\p{M}+")
    private val nonAlphanumeric = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    fun key(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphanumeric, " ")
            .trim()
            .replace(whitespace, " ")

    fun query(input: String): String = key(input)
}
