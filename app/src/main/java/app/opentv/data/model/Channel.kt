package app.opentv.data.model

enum class SourceType {
    LIVE,
    MOVIE,
    SERIES,
    OTHER
}

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val group: String?,
    val logoUrl: String?,
    val tvgId: String?,
    val sourceType: SourceType,
    val searchKey: String,
    val qualityTag: String?
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
