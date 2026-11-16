package dev.lluissunol.iptv.data

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
