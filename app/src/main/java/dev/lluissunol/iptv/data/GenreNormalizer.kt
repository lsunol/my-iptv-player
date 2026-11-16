package dev.lluissunol.iptv.data

import java.text.Normalizer
import java.util.Locale

/** Canonical genres shown in Spanish; the labels live in `strings.xml`. */
enum class CanonicalGenre {
    ACTION, ADVENTURE, ANIMATION, COMEDY, CRIME, DOCUMENTARY, DRAMA, FAMILY, FANTASY,
    HISTORY, HORROR, MUSIC, MUSICAL, MYSTERY, ROMANCE, SCIFI, TV_MOVIE, THRILLER, WAR,
    POLITICS, WESTERN, KIDS, NEWS, REALITY, SOAP, TALK, BIOGRAPHY, SPORT, GAME_SHOW,
    FOOD, HOME_GARDEN, MINI_SERIES, SHORT, TRAVEL
}

/** One genre of a title: a unified Spanish genre, or a provider label passed through. */
sealed interface GenreLabel {
    /** A recognised genre; the UI maps it to a string resource. */
    data class Known(val genre: CanonicalGenre) : GenreLabel

    /** An unrecognised provider label (first letter uppercased), shown as is. */
    data class Other(val label: String) : GenreLabel
}

/**
 * Unifies the genres of English/Spanish panels. Pure: a raw `genre` string goes
 * in, canonical ids and pass-through labels come out. Unknown tokens are never
 * dropped (they pass through) unless they are junk.
 */
object GenreNormalizer {
    private const val MAX_TOKEN_CHARS = 30
    private val SPLIT = Regex("""[/,|;]""")
    private val MARKS = Regex("""\p{M}+""")
    private val SPACES = Regex("""\s+""")
    private val DASHES = Regex("""[-_]""")
    private val JUNK = setOf("n/a", "na", "null", "none", "-", "0", "")
    // `N/A` would otherwise be split on its slash into two one-letter genres.
    private val NOT_AVAILABLE = Regex("""(?i)(?<![\p{L}\p{N}])n/a(?![\p{L}\p{N}])""")

    /** Lookup form: lowercase, accent-free, `-`/`_` as space, single spaces. */
    fun foldKey(token: String): String =
        Normalizer.normalize(token, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(DASHES, " ")
            .replace(SPACES, " ")
            .trim()

    private fun table(): Map<String, List<CanonicalGenre>> = TABLE

    /** Splits a raw genre string into cleaned tokens (junk removed). */
    fun tokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<String>(4)
        for (part in raw.replace(NOT_AVAILABLE, " ").split(SPLIT)) {
            val token = part.trim().replace(SPACES, " ")
            if (token.isEmpty() || token.length > MAX_TOKEN_CHARS) continue
            if (token.lowercase(Locale.ROOT) in JUNK) continue
            if (token.all { it.isDigit() }) continue
            out += token
        }
        return out
    }

    /**
     * Canonical genres of one token. The whole token is looked up first (so
     * `Action & Adventure` stays one compound entry); only then is it split on
     * `&`. A token with no recognised part yields an empty list.
     */
    fun canonical(token: String): List<CanonicalGenre> {
        val key = foldKey(token)
        table()[key]?.let { return it }
        if ('&' !in key) return emptyList()
        return key.split('&').flatMap { table()[it.trim()].orEmpty() }.distinct()
    }

    /** All labels of a raw `genre` string, deduplicated, in order of appearance. */
    fun labels(raw: String?): List<GenreLabel> {
        val out = LinkedHashSet<GenreLabel>(4)
        val seenOthers = HashSet<String>(4)
        for (token in tokens(raw)) {
            val known = canonical(token)
            if (known.isNotEmpty()) {
                // `Drama & Foo`: the recognised half is kept, the rest passes through.
                known.forEach { out += GenreLabel.Known(it) }
                if ('&' in token && table()[foldKey(token)] == null) {
                    token.split('&').map { it.trim() }
                        .filter { it.length >= 2 && table()[foldKey(it)] == null }
                        .forEach { addOther(it, out, seenOthers) }
                }
            } else {
                addOther(token, out, seenOthers)
            }
        }
        return out.toList()
    }

    private fun addOther(token: String, out: MutableSet<GenreLabel>, seen: MutableSet<String>) {
        if (token.isEmpty() || token.all { it.isDigit() }) return
        if (seen.add(foldKey(token))) {
            out += GenreLabel.Other(token.replaceFirstChar { it.uppercaseChar() })
        }
    }

    private val TABLE: Map<String, List<CanonicalGenre>> = buildMap {
        fun add(genre: CanonicalGenre, vararg keys: String) =
            keys.forEach { put(it, listOf(genre)) }
        fun compound(first: CanonicalGenre, second: CanonicalGenre, vararg keys: String) =
            keys.forEach { put(it, listOf(first, second)) }

        add(CanonicalGenre.ACTION, "action", "accion")
        add(CanonicalGenre.ADVENTURE, "adventure", "aventura", "aventuras")
        compound(
            CanonicalGenre.ACTION, CanonicalGenre.ADVENTURE,
            "action & adventure", "action and adventure", "accion y aventura"
        )
        add(CanonicalGenre.ANIMATION, "animation", "animated", "animacion", "dibujos animados")
        add(CanonicalGenre.COMEDY, "comedy", "comedia")
        add(CanonicalGenre.CRIME, "crime", "crimen", "policiaco", "policiaca")
        add(CanonicalGenre.DOCUMENTARY, "documentary", "documental", "documentales", "docu")
        add(CanonicalGenre.DRAMA, "drama")
        add(CanonicalGenre.FAMILY, "family", "familia", "familiar")
        add(CanonicalGenre.FANTASY, "fantasy", "fantasia")
        add(CanonicalGenre.HISTORY, "history", "historical", "historia", "historico")
        add(CanonicalGenre.HORROR, "horror", "terror")
        add(CanonicalGenre.MUSIC, "music", "musica")
        add(CanonicalGenre.MUSICAL, "musical")
        add(CanonicalGenre.MYSTERY, "mystery", "misterio")
        add(CanonicalGenre.ROMANCE, "romance", "romantic", "romantica", "romantico")
        add(CanonicalGenre.SCIFI, "science fiction", "sci fi", "scifi", "ciencia ficcion")
        compound(
            CanonicalGenre.SCIFI, CanonicalGenre.FANTASY,
            "sci fi & fantasy", "science fiction & fantasy", "ciencia ficcion y fantasia"
        )
        add(CanonicalGenre.TV_MOVIE, "tv movie", "pelicula de tv", "pelicula de television", "telefilm")
        add(CanonicalGenre.THRILLER, "thriller", "suspense", "suspenso", "intriga")
        add(CanonicalGenre.WAR, "war", "belica", "belico", "guerra")
        add(CanonicalGenre.POLITICS, "politics", "politica")
        compound(
            CanonicalGenre.WAR, CanonicalGenre.POLITICS,
            "war & politics", "belica y politica", "guerra y politica"
        )
        add(CanonicalGenre.WESTERN, "western", "oeste", "del oeste")
        add(CanonicalGenre.KIDS, "kids", "children", "infantil", "ninos")
        add(CanonicalGenre.NEWS, "news", "noticias")
        add(CanonicalGenre.REALITY, "reality", "reality show", "reality tv")
        add(CanonicalGenre.SOAP, "soap", "telenovela", "culebron")
        add(CanonicalGenre.TALK, "talk", "talk show", "entrevistas")
        add(CanonicalGenre.BIOGRAPHY, "biography", "biografia")
        add(CanonicalGenre.SPORT, "sport", "sports", "deporte", "deportes")
        add(CanonicalGenre.GAME_SHOW, "game show", "concurso")
        add(CanonicalGenre.FOOD, "food", "cocina", "gastronomia")
        add(CanonicalGenre.HOME_GARDEN, "home and garden", "home & garden", "hogar y jardin")
        add(CanonicalGenre.MINI_SERIES, "mini series", "miniseries", "miniserie")
        add(CanonicalGenre.SHORT, "short", "cortometraje", "corto")
        add(CanonicalGenre.TRAVEL, "travel", "viajes", "viaje")
    }
}
