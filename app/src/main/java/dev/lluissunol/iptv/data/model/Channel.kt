package dev.lluissunol.iptv.data.model

enum class SourceType {
    LIVE,
    MOVIE,
    SERIES,
    OTHER
}

/**
 * Quality tokens providers append to channel and catalogue names (`FHD`, `HD`,
 * `HEVC`, ...). Shared by the M3U parser and the Xtream catalogue merge so a
 * movie shows the same badge whichever side detected it.
 */
private val qualityToken = Regex("(?i)(4K|UHD|FHD|HD|SD|HEVC|HDR|H\\.265|H265)")

/** The quality badge text of a name, or null when the name carries none. */
internal fun qualityTagOf(name: String): String? =
    qualityToken.find(name)?.value?.uppercase(java.util.Locale.ROOT)

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val group: String?,
    val logoUrl: String?,
    val tvgId: String?,
    val sourceType: SourceType,
    val searchKey: String,
    val qualityTag: String?,
    val favoriteKey: String
)

data class GroupCount(
    val name: String,
    val count: Int
)

data class Playlist(
    val channels: List<Channel>,
    val groups: List<GroupCount>,
    val warnings: List<String>,
    val bytes: Long,
    val counts: Map<SourceType, Int>,
    val groupsByType: Map<SourceType, List<GroupCount>> = emptyMap()
) {
    fun count(type: SourceType): Int = counts[type] ?: 0

    fun groupsFor(type: SourceType?): List<GroupCount> =
        if (type == null) groups else groupsByType[type].orEmpty()
}
