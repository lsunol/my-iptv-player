package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.GroupCount
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.qualityTagOf
import java.net.URI
import java.util.Locale

/**
 * Streaming-friendly M3U/M3U8 parser. It never regexes the whole document and
 * keeps only the channel model, not the raw multi-megabyte text.
 */
object PlaylistParser {

    private val attribute = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
    private val unquotedAttribute = Regex("""([A-Za-z0-9_-]+)\s*=\s*([^"\s]+)""")
    private val movieGroup = Regex("(?i)(vod|pel[ií]cula|movie|cine|film)")
    private val seriesGroup = Regex("(?i)(serie|novela|anime|docuserie)")
    private val liveGroup = Regex("(?i)(tv|live|en vivo|directo|canal|deporte|futbol|fútbol|noticia|infantil|música|musica)")

    private data class Pending(
        val name: String,
        val group: String?,
        val logo: String?,
        val tvgId: String?,
        val tvgName: String?
    )

    fun parse(
        text: String,
        baseUrl: String? = null,
        bytes: Long = 0L,
        onProgress: (parsedEntries: Int) -> Unit = {}
    ): Playlist {
        val channels = ArrayList<Channel>(4096)
        val warnings = ArrayList<String>(8)
        val groupCounts = HashMap<String, Int>(256)
        val groupCountsByType = HashMap<SourceType, HashMap<String, Int>>(4)
        val counts = HashMap<SourceType, Int>(4)
        var pending: Pending? = null
        var lineNumber = 0
        var index = 0

        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            lineNumber++
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTM3U") -> Unit
                line.startsWith("#EXTINF:") -> {
                    val (head, displayName) = splitExtInf(line.substring(8))
                    val attrs = parseAttributes(head)
                    val name = displayName.ifBlank {
                        attrs["tvg-name"] ?: attrs["tvg-id"] ?: "Canal sin nombre"
                    }.trim()
                    pending = Pending(
                        name = name,
                        group = (attrs["group-title"] ?: attrs["group"])?.takeIf { it.isNotBlank() },
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                    )
                }

                line.startsWith("#EXTGRP:") -> {
                    val group = line.substring(8).trim()
                    if (group.isNotEmpty()) pending = pending?.copy(group = group)
                }

                line.startsWith("#") -> Unit

                else -> {
                    val entry = pending
                    if (entry == null) {
                        addWarning(warnings, "Línea $lineNumber: URL sin #EXTINF, ignorada.")
                    } else {
                        val resolved = resolveUrl(line, baseUrl)
                        if (resolved == null) {
                            addWarning(warnings, "Línea $lineNumber: URL no válida, ignorada.")
                        } else {
                            val type = classify(resolved, entry.group)
                            val channel = Channel(
                                id = buildId(resolved, entry.name, index),
                                name = entry.name,
                                url = resolved,
                                group = entry.group,
                                logoUrl = entry.logo,
                                tvgId = entry.tvgId,
                                sourceType = type,
                                searchKey = SearchNormalizer.key(
                                    "${entry.name} ${entry.tvgName.orEmpty()} ${entry.group.orEmpty()}"
                                ),
                                qualityTag = qualityTagOf(entry.name),
                                favoriteKey = buildFavoriteKey(entry.tvgId, resolved)
                            )
                            channels += channel
                            counts[type] = (counts[type] ?: 0) + 1
                            entry.group?.let { group ->
                                groupCounts[group] = (groupCounts[group] ?: 0) + 1
                                val byType = groupCountsByType.getOrPut(type) { HashMap() }
                                byType[group] = (byType[group] ?: 0) + 1
                            }
                            index++
                        }
                    }
                    pending = null
                }
            }

            if (lineNumber % 1000 == 0) onProgress(channels.size)
        }

        onProgress(channels.size)
        val groups = groupCounts.entries
            .sortedByDescending { it.value }
            .map { GroupCount(it.key, it.value) }
        val groupsByType = groupCountsByType.mapValues { (_, typeGroups) ->
            typeGroups.entries
                .sortedByDescending { it.value }
                .map { GroupCount(it.key, it.value) }
        }

        return Playlist(
            channels = channels,
            groups = groups,
            warnings = warnings,
            bytes = bytes,
            counts = counts,
            groupsByType = groupsByType
        )
    }

    private fun addWarning(warnings: MutableList<String>, message: String) {
        if (warnings.size < 20) warnings += message
    }

    /** Splits `-1 tvg-id="x",Name` at the first comma outside quotes. */
    private fun splitExtInf(input: String): Pair<String, String> {
        var inQuotes = false
        for (i in input.indices) {
            when (input[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return input.substring(0, i) to input.substring(i + 1)
            }
        }
        return input to ""
    }

    private fun parseAttributes(input: String): Map<String, String> {
        val attributes = HashMap<String, String>(8)
        attribute.findAll(input).forEach { match ->
            attributes[match.groupValues[1].lowercase(Locale.ROOT)] = match.groupValues[2]
        }
        unquotedAttribute.findAll(input).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            if (key !in attributes) attributes[key] = match.groupValues[2]
        }
        return attributes
    }

    private fun resolveUrl(raw: String, baseUrl: String?): String? {
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        if (baseUrl.isNullOrBlank()) return null
        return try {
            URI(baseUrl).resolve(raw).toString().takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun classify(url: String, group: String?): SourceType {
        val path = try {
            URI(url).path.orEmpty().lowercase(Locale.ROOT)
        } catch (_: Exception) {
            url.lowercase(Locale.ROOT)
        }
        return when {
            "/live/" in path -> SourceType.LIVE
            "/movie/" in path -> SourceType.MOVIE
            "/series/" in path -> SourceType.SERIES
            group == null -> SourceType.OTHER
            movieGroup.containsMatchIn(group) -> SourceType.MOVIE
            seriesGroup.containsMatchIn(group) -> SourceType.SERIES
            liveGroup.containsMatchIn(group) -> SourceType.LIVE
            else -> SourceType.OTHER
        }
    }

    private fun buildFavoriteKey(tvgId: String?, url: String): String {
        val id = tvgId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (id.isNotEmpty()) "tvg:$id" else "url:${Integer.toHexString(url.hashCode())}"
    }

    private fun buildId(url: String, name: String, index: Int): String =
        "${Integer.toHexString(url.hashCode())}-${Integer.toHexString(name.hashCode())}-$index"
}
