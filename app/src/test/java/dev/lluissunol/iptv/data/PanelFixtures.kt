package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.buildVodCatalog

/**
 * Synthetic Xtream panel responses in four shapes, so the golden rule (any panel,
 * not only the measured one) is exercised without a real provider:
 *
 * - [MEASURED]: bulk-imported `added`, integer ratings, series genres but no
 *   release dates, `ESPAÑA - x` / `ENGLISH - x` categories;
 * - [RICH]: release dates under rotating names, comma genres, ms `added`;
 * - [BARE]: only name, id, icon and category;
 * - [MALFORMED]: garbage in every field.
 *
 * Everything is invented (titles, categories, ids); the clock is fixed.
 */
internal object PanelFixtures {
    const val NOW = 1_731_000_000L // 2024-11-07
    const val YEAR = 2024
    private const val DAY = 86_400L
    private val DAY0 = NOW - NOW % DAY

    enum class Variant { MEASURED, RICH, BARE, MALFORMED }

    class Panel(
        val vod: String,
        val series: String,
        val vodCategories: String,
        val seriesCategories: String,
        val movies: Int,
        val seriesCount: Int
    )

    fun panel(variant: Variant, movies: Int = 200, series: Int = 60): Panel = when (variant) {
        Variant.MEASURED -> measured(movies, series)
        Variant.RICH -> rich(movies, series)
        Variant.BARE -> bare(movies, series)
        Variant.MALFORMED -> malformed(movies, series)
    }

    fun catalog(variant: Variant, movies: Int = 200, series: Int = 60): VodCatalog {
        val p = panel(variant, movies, series)
        val warnings = ArrayList<dev.lluissunol.iptv.data.model.CatalogWarning>()
        val api = XtreamApiCatalog(
            vodStreams = decodeVodStreams(p.vod, warnings),
            seriesList = decodeSeriesList(p.series, warnings),
            vodCategories = decodeCategories(p.vodCategories, warnings),
            seriesCategories = decodeCategories(p.seriesCategories, warnings),
            warnings = warnings
        )
        return buildVodCatalog(
            api = api,
            matcher = XtreamStreamMatcher(emptyList()),
            fallbackUrl = { type, id -> "http://fake.example/$type/u/p/$id.m3u8" },
            serverBase = "http://fake.example",
            nowEpochSec = NOW,
            currentYear = YEAR
        )
    }

    private fun categories(vararg names: String) =
        names.mapIndexed { i, n ->
            val name = if (n == NULL_NAME) "null" else quote(n)
            """{"category_id":"${i + 1}","category_name":$name}"""
        }
            .joinToString(",", "[", "]")

    private const val NULL_NAME = "<null>"

    private fun quote(s: String) = "\"" + s.replace("\"", "\\\"") + "\""

    private fun measured(movies: Int, series: Int): Panel {
        val es = listOf("Acción", "Comedia", "Drama", "Terror", "Familia", "Crimen", "Aventura")
        val en = listOf("Action", "Comedy", "Drama", "Horror", "Family", "Crime", "Adventure")
        val movieCats = es.map { "ESPAÑA - $it" } + en.map { "ENGLISH - $it" }
        val vod = (1..movies).joinToString(",", "[", "]") { i ->
            // 55% / 38% of the titles on two bulk-import days, the rest spread.
            val added = when {
                i % 100 < 55 -> DAY0 - 400 * DAY + (i % 1000) * 20
                i % 100 < 93 -> DAY0 - 394 * DAY + (i % 1000) * 20
                else -> DAY0 - (i % 11) * DAY
            }
            val rating = if (i % 33 == 0) "" else ((i * 7) % 10 + 1).toString()
            val five = if (rating.isEmpty()) "0" else (rating.toInt() / 2.0).toString()
            """{"num":$i,"name":"Película de prueba $i","stream_id":$i,"stream_icon":"",""" +
                """"category_id":"${i % 14 + 1}","added":"$added","rating":"$rating","rating_5based":"$five"}"""
        }
        val genres = listOf("Crime /  Drama / Thriller", "Comedy", "Comedia", "Action & Adventure", "Crimen / Drama")
        val seriesCats = (es.take(8) + "Documental").take(8).map { "ESPAÑA - $it" } +
            en.take(7).map { "ENGLISH - $it" }
        val ser = (1..series).joinToString(",", "[", "]") { i ->
            val rating = if (i % 20 == 0) "" else ((i * 3) % 10 + 1).toString()
            """{"name":"Serie de prueba $i","series_id":$i,"cover":"","plot":"Trama $i",""" +
                """"category_id":"${i % 15 + 1}","rating":"$rating","releaseDate":"",""" +
                """"last_modified":"${DAY0 - 390 * DAY}","genre":${quote(genres[i % genres.size])},""" +
                """"episode_run_time":"45"}"""
        }
        return Panel(vod, ser, categories(*movieCats.toTypedArray()), categories(*seriesCats.toTypedArray()), movies, series)
    }

    private fun rich(movies: Int, series: Int): Panel {
        val cats = arrayOf("|ES| Drama", "|ES| Comedia", "|EN| Drama", "|EN| Comedy", "[DE] Krimi", "[DE] Komödie", "VOD 4K")
        val genres = listOf("Action, Comedy", "Drama", "Horror, Thriller", "Sci-Fi & Fantasy", "Animación")
        val vod = (1..movies).joinToString(",", "[", "]") { i ->
            val release = when (i % 3) {
                0 -> """"releasedate":"${1990 + i % 30}-0${i % 9 + 1}-1${i % 9}""""
                1 -> """"release_date":"${1990 + i % 30}""""
                else -> """"year":${1990 + i % 30}"""
            }
            """{"name":"Película rica $i","stream_id":"$i","stream_icon":"","category_id":"${i % 7 + 1}",""" +
                """"added":${(NOW - (i % 400) * DAY) * 1000},"rating":${(i * 7) % 10 + 1},""" +
                """"rating_5based":${((i * 7) % 10 + 1) / 2.0 + 0.1 * (i % 2)},$release,""" +
                """"genre":${quote(genres[i % genres.size])}}"""
        }
        val ser = (1..series).joinToString(",", "[", "]") { i ->
            val release = when (i % 3) {
                0 -> """"releaseDate":"${1990 + i % 30}-05-01""""
                1 -> """"release_date":"${1990 + i % 30}""""
                else -> """"year":"${1990 + i % 30}""""
            }
            """{"name":"Serie rica $i","series_id":$i,"cover":"","plot":"","category_id":"${i % 7 + 1}",""" +
                """"rating":"${(i * 3) % 10 + 1}",$release,"genre":${quote(genres[i % genres.size])},""" +
                """"episode_run_time":${30 + i % 30}}"""
        }
        return Panel(vod, ser, categories(*cats), categories(*cats), movies, series)
    }

    private fun bare(movies: Int, series: Int): Panel {
        val vod = (1..movies).joinToString(",", "[", "]") { i ->
            """{"name":"Película básica $i","stream_id":$i,"stream_icon":"","category_id":"${i % 3 + 1}"}"""
        }
        val ser = (1..series).joinToString(",", "[", "]") { i ->
            """{"name":"Serie básica $i","series_id":$i,"category_id":"${i % 3 + 1}"}"""
        }
        val cats = categories("Acción", "Comedia", "Drama")
        return Panel(vod, ser, cats, cats, movies, series)
    }

    /** Garbage in every field; the item ids stay valid except where noted. */
    private fun malformed(movies: Int, series: Int): Panel {
        val ratings = listOf("\"N/A\"", "\"7,4\"", "[]", "{}", "-1", "85", "\"abc\"", "\"8\"")
        val added = listOf("\"\"", "\"0\"", "${NOW * 1000}", "\"2023-12-05 10:00:00\"", "99999999999999", "\"abc\"", "[]", "null")
        val release = listOf(
            "\"0000-00-00\"", "\"19/10/2016\"", "\"10/19/2016\"", "\"05/06/2016\"", "2016", "\"1800\"", "\"2999\"", "{}"
        )
        val genre = listOf(
            "[\"Drama\",\"Crime\"]", "\"Action & Adventure\"", "\"Sci-Fi & Fantasy\"",
            quote("x".repeat(60)), "\"123\"", "null", "{}", "\"Drama\""
        )
        val catNames = arrayOf(NULL_NAME, "NETFLIX - Series", "4K - Movies", "Sci-Fi", "Star Wars: Saga", "ES/EN - Dual")
        val vod = ArrayList<String>()
        for (i in 1..movies) {
            val id = when {
                i == 5 -> "6" // duplicate id: first occurrence wins
                i == 7 -> "\"abc\"" // unusable id: skipped
                else -> "$i"
            }
            val name = if (i == 9) "null" else quote("Película rara $i")
            val category = if (i % 4 == 0) "${i % 6 + 1}" else "\"${i % 6 + 1}\""
            vod += """{"name":$name,"stream_id":$id,"stream_icon":null,"category_id":$category,""" +
                """"rating":${ratings[i % ratings.size]},"added":${added[i % added.size]},""" +
                """"releasedate":${release[i % release.size]},"genre":${genre[i % genre.size]}}"""
        }
        val ser = (1..series).joinToString(",", "[", "]") { i ->
            """{"name":"Serie rara $i","series_id":$i,"category_id":"${i % 6 + 1}",""" +
                """"rating":${ratings[i % ratings.size]},"releaseDate":${release[i % release.size]},""" +
                """"genre":${genre[i % genre.size]},"episode_run_time":${if (i % 2 == 0) "\"45 min\"" else "\"2700\""}}"""
        }
        val cats = categories(*catNames)
        return Panel(vod.joinToString(",", "[", "]"), ser, cats, cats, movies, series)
    }
}
