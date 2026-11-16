package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.PlaylistConfig

/**
 * Which list to open on a cold start: the persisted active list when it still
 * exists, otherwise the first list, otherwise none (the add-list form shows).
 */
internal fun startupListId(lists: List<PlaylistConfig>, activeId: String?): String? =
    lists.firstOrNull { it.id == activeId }?.id ?: lists.firstOrNull()?.id

/**
 * Back/tap on the active list from the lists screen: return to the playlist
 * untouched when its channels are in memory or a load is running; otherwise it
 * has to be (re)opened from its cache.
 */
internal fun canResumeActiveList(hasPlaylist: Boolean, isLoading: Boolean): Boolean =
    hasPlaylist || isLoading

/** Where a form opened while on [current] returns to; the launch state is not a destination. */
internal fun formReturnScreen(current: Screen): Screen =
    if (current == Screen.STARTING) Screen.LISTS else current

/** A user-typed list URL is usable only with an http(s) scheme and a host. */
internal fun isHttpUrl(value: String): Boolean {
    val trimmed = value.trim()
    val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    return hasScheme && trimmed.substringAfter("://").substringBefore('/').isNotBlank()
}

/**
 * Whether Back/tap on the active list from the lists screen has somewhere to go.
 * A list whose saved URL is not a valid http(s) URL cannot be opened (it would
 * bounce straight back to its edit form), so it must not trap Back in a loop.
 */
internal fun canReturnToActiveList(active: PlaylistConfig?): Boolean =
    active?.effectiveUrl?.let(::isHttpUrl) == true

/**
 * Saving an edit of the ACTIVE list with a changed URL/credentials must stop its
 * running load first: otherwise the load finishes afterwards and restores the
 * old channels (and rewrites the old caches) under the new URL.
 */
internal fun mustCancelLoadsOnEdit(editedIsActive: Boolean, urlChanged: Boolean): Boolean =
    editedIsActive && urlChanged
