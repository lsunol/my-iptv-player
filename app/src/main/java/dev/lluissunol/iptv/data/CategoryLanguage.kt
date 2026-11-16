package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.VodCategory
import java.text.Normalizer
import java.util.Locale

/**
 * Language/region options derived from provider category prefixes
 * (`ESPAÑA - Acción`, `|ES| Drama`). The recognised tokens are a locale lookup
 * table, not content: nothing here names a provider or a channel.
 */
enum class LanguageOption(val id: String) {
    ES("es"), LATINO("latino"), EN("en"), FR("fr"), DE("de"), IT("it"), PT("pt"),
    PT_BR("pt_br"), CA("ca"), GL("gl"), EU("eu"), AR("ar"), TR("tr"), NL("nl"),
    PL("pl"), RU("ru"), RO("ro"), HI("hi"), VO("vo"), OTHER("other");

    companion object {
        fun fromId(id: String?): LanguageOption? = entries.firstOrNull { it.id == id }
    }
}

/** A category name split into its normalized prefix token and the rest. */
data class CategoryPrefix(val token: String, val stripped: String)

/**
 * The detected language filter of one tab: the offered [options] (recognised
 * ones by title count, "Otros" last), the option of every category and the
 * category names without their prefix.
 */
data class LanguageFacet(
    val options: List<LanguageOption>,
    val byCategory: Map<String, LanguageOption>,
    val strippedNames: Map<String, String>
)

private val MARKS = Regex("""\p{M}+""")
private val SPACES = Regex("""\s+""")
private val BRACKETED = Regex("""^\s*(?:\|([^|]{1,20})\||\[([^\]]{1,20})]|\(([^)]{1,20})\))\s*(.*)$""")
private val SPACED_SEPARATOR = Regex("""^(.+?)\s+[-–—|]\s+(.+)$""")
private val COLON = Regex("""^([A-Za-z]{2,3}):\s+(.+)$""")
private val PIPE = Regex("""^([^|\s]{2,16})\|\s*(.+)$""")

private fun normalizeToken(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .uppercase(Locale.ROOT)
        .replace(SPACES, " ")
        .trim()

/**
 * The prefix of a category name, or null when it has none. Patterns, first
 * match wins: a bracketed start (`|ES|`, `[ES]`, `(ES)`), a token followed by a
 * **spaced** separator (` - `, ` – `, ` — `, ` | `), a 2-3 letter token before
 * `: `, and `ES| `. The token must be 2-16 characters with no digits, so
 * `Sci-Fi`, `4K - Movies` and `Star Wars: Saga` have no prefix.
 */
fun extractCategoryPrefix(name: String): CategoryPrefix? {
    val trimmed = name.trim()
    val (rawToken, rest) = BRACKETED.matchEntire(trimmed)?.let { m ->
        (m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }) to m.groupValues[4]
    } ?: SPACED_SEPARATOR.matchEntire(trimmed)?.let { m -> m.groupValues[1] to m.groupValues[2] }
    ?: COLON.matchEntire(trimmed)?.let { m -> m.groupValues[1] to m.groupValues[2] }
    ?: PIPE.matchEntire(trimmed)?.let { m -> m.groupValues[1] to m.groupValues[2] }
    ?: return null
    val token = normalizeToken(rawToken)
    if (token.length !in 2..16 || token.any { it.isDigit() }) return null
    val stripped = rest.trim()
    if (stripped.isEmpty()) return null
    return CategoryPrefix(token, stripped)
}

private val TOKENS: Map<String, LanguageOption> = buildMap {
    fun add(option: LanguageOption, vararg tokens: String) = tokens.forEach { put(it, option) }
    add(LanguageOption.ES, "ES", "ESP", "SPA", "SPAIN", "SPANISH", "ESPANA", "ESPANOL", "CASTELLANO", "CAST")
    add(LanguageOption.LATINO, "LAT", "LATAM", "LATINO", "LATINOAMERICA", "MX", "MEX", "MEXICO")
    add(LanguageOption.EN, "EN", "ENG", "ENGLISH", "INGLES", "UK", "GB", "US", "USA")
    add(LanguageOption.FR, "FR", "FRA", "FRE", "FRANCE", "FRENCH", "FRANCAIS", "FRANCES")
    add(LanguageOption.DE, "DE", "GER", "DEU", "GERMAN", "GERMANY", "DEUTSCH", "ALEMAN")
    add(LanguageOption.IT, "IT", "ITA", "ITALY", "ITALIAN", "ITALIANO", "ITALIA")
    add(LanguageOption.PT, "PT", "POR", "PORTUGAL", "PORTUGUESE", "PORTUGUES")
    add(LanguageOption.PT_BR, "BR", "BRA", "BRAZIL", "BRASIL")
    add(LanguageOption.CA, "CAT", "CATALA", "CATALAN")
    add(LanguageOption.GL, "GALEGO", "GALLEGO")
    add(LanguageOption.EU, "EUSKERA", "EUSKARA")
    add(LanguageOption.AR, "AR", "ARA", "ARAB", "ARABIC", "ARABE")
    add(LanguageOption.TR, "TR", "TUR", "TURK", "TURKEY", "TURKISH", "TURCO")
    add(LanguageOption.NL, "NL", "NLD", "DUTCH", "NEDERLANDS")
    add(LanguageOption.PL, "PL", "POL", "POLISH", "POLSKA")
    add(LanguageOption.RU, "RU", "RUS", "RUSSIAN")
    add(LanguageOption.RO, "RO", "ROM", "ROMANIA", "ROMANIAN")
    add(LanguageOption.HI, "HINDI", "INDIA")
    // `VO` is the original version, not necessarily subtitled (so not "VOSE" only).
    add(LanguageOption.VO, "VOSE", "VOS", "VO", "SUBTITULADO", "SUBTITULADA")
}

/**
 * The language of a normalized prefix token. `CA`, `EU`, `IN` and `HI` are
 * deliberately absent as two-letter tokens (Canada/Europe/India ambiguity), and
 * IPTV noise (`HD`, `4K`, `VOD`, `VIP`, ...) is never recognised.
 */
fun recognizeLanguage(token: String): LanguageOption? = TOKENS[token]

private const val MIN_RECOGNISED_COVERAGE = 0.50

/**
 * Decides whether a tab offers the language filter. Counted over the titles of
 * categories that have at least one:
 * 1. at least two distinct recognised options have titles;
 * 2. recognised categories hold at least half of the tab's titles;
 * 3. at least one recognised option spans two or more categories (a prefix
 *    shared by several categories; otherwise the filter would only repeat the
 *    category chips).
 * "Otros" is an option only when it holds titles; titles of a category id that
 * is not in [categories] count as "Otros".
 */
fun detectLanguageFacet(
    categories: List<VodCategory>,
    titlesPerCategory: Map<String, Int>,
    totalTitles: Int
): LanguageFacet? {
    if (totalTitles <= 0) return null
    val byCategory = HashMap<String, LanguageOption>(categories.size * 2)
    val stripped = HashMap<String, String>(categories.size * 2)
    for (category in categories) {
        val prefix = extractCategoryPrefix(category.name)
        byCategory[category.id] = prefix?.let { recognizeLanguage(it.token) } ?: LanguageOption.OTHER
        stripped[category.id] = prefix?.stripped ?: category.name
    }

    val titlesByOption = HashMap<LanguageOption, Int>()
    val categoriesByOption = HashMap<LanguageOption, Int>()
    for ((id, titles) in titlesPerCategory) {
        if (titles <= 0) continue
        val option = byCategory[id] ?: LanguageOption.OTHER
        titlesByOption.merge(option, titles, Int::plus)
        if (id in byCategory) categoriesByOption.merge(option, 1, Int::plus)
    }

    val recognised = titlesByOption.filterKeys { it != LanguageOption.OTHER }
    if (recognised.size < 2) return null
    if (recognised.values.sum().toDouble() / totalTitles < MIN_RECOGNISED_COVERAGE) return null
    if (recognised.keys.none { (categoriesByOption[it] ?: 0) >= 2 }) return null

    val options = ArrayList<LanguageOption>(recognised.size + 1)
    recognised.entries
        .sortedWith(compareByDescending<Map.Entry<LanguageOption, Int>> { it.value }.thenBy { it.key.ordinal })
        .mapTo(options) { it.key }
    if ((titlesByOption[LanguageOption.OTHER] ?: 0) > 0) options += LanguageOption.OTHER
    return LanguageFacet(options, byCategory, stripped)
}
