package dev.lluissunol.iptv.data

/**
 * One row of the group / category picker sheet. [key] is the normalized label,
 * computed once when the entries are built, never per keystroke or per row.
 */
class PickerEntry(
    val id: String,
    val label: String,
    val count: Int?,
    val key: String
) {
    companion object {
        fun of(id: String, label: String, count: Int?) =
            PickerEntry(id, label, count, SearchNormalizer.key(label))
    }
}

/**
 * Filters picker [entries] by a typed [query], accent/case/punctuation
 * insensitive ("es-futbol" matches "ES - FUTBOL"). A blank query returns the
 * same list instance; otherwise order is preserved.
 */
fun filterPicker(entries: List<PickerEntry>, query: String): List<PickerEntry> {
    val q = SearchNormalizer.query(query)
    if (q.isEmpty()) return entries
    return entries.filter { it.key.contains(q) }
}
