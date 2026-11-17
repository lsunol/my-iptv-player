package dev.lluissunol.iptv.data

/**
 * Stored placeholders for entries the provider sent without a name. They are
 * data, not UI text: the channel id and favorite key are derived from the
 * name, so the values must not change between releases or users would lose
 * favorites. The UI maps them to a localized string when rendering
 * (see `cleanChannelName`).
 */
const val UNNAMED_CHANNEL = "Canal sin nombre"
const val UNNAMED_TITLE = "Sin nombre"

/** Not part of any key; a control-character prefix cannot collide with a real title. */
const val UNNAMED_SERIES = "series"

/** Providers often prefix the name with `| Country | Category |`; strip it. */
private val displayNamePrefix = Regex("""^\s*\|[^|]*\|\s*""")

/**
 * The title as the user sees it: the provider's `| ES | ` style prefix removed
 * (an all-prefix name keeps its original text). Pure so the data layer can use
 * it for the A-Z key without depending on the UI package.
 */
fun cleanDisplayName(name: String): String {
    val cleaned = name.replace(displayNamePrefix, "").trim()
    return cleaned.ifEmpty { name.trim() }
}
