package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRulesTest {
    private fun list(id: String) = PlaylistConfig(id = id, name = id, kind = PlaylistKind.URL, url = "http://h/$id")

    @Test
    fun startupOpensTheActiveList() {
        assertEquals("b", startupListId(listOf(list("a"), list("b")), "b"))
    }

    @Test
    fun startupFallsBackToFirstWhenActiveIsGoneOrUnset() {
        assertEquals("a", startupListId(listOf(list("a"), list("b")), "zzz"))
        assertEquals("a", startupListId(listOf(list("a"), list("b")), null))
    }

    @Test
    fun startupWithNoListsShowsTheForm() {
        assertNull(startupListId(emptyList(), null))
        assertNull(startupListId(emptyList(), "a"))
    }

    @Test
    fun resumeOnlyWhenLoadedOrLoading() {
        assertTrue(canResumeActiveList(hasPlaylist = true, isLoading = false))
        assertTrue(canResumeActiveList(hasPlaylist = false, isLoading = true))
        assertFalse(canResumeActiveList(hasPlaylist = false, isLoading = false))
    }

    @Test
    fun formNeverReturnsToTheLaunchState() {
        assertEquals(Screen.LISTS, formReturnScreen(Screen.STARTING))
        assertEquals(Screen.PLAYLIST, formReturnScreen(Screen.PLAYLIST))
        assertEquals(Screen.LISTS, formReturnScreen(Screen.LISTS))
    }

    @Test
    fun searchStaysWhileQueryIsTyped() {
        assertFalse(UiState().searchMode)
        assertTrue(UiState(searchActive = true).searchMode)
        assertTrue(UiState(query = "x").searchMode)
    }

    @Test
    fun cannotReturnToAnActiveListWithAnInvalidUrl() {
        assertFalse(canReturnToActiveList(null))
        assertFalse(canReturnToActiveList(PlaylistConfig(id = "a", name = "a", kind = PlaylistKind.URL, url = "not a url")))
        assertFalse(canReturnToActiveList(PlaylistConfig(id = "a", name = "a", kind = PlaylistKind.URL, url = "ftp://h/x")))
        assertTrue(canReturnToActiveList(list("a")))
    }

    @Test
    fun isHttpUrlNeedsSchemeAndHost() {
        assertTrue(isHttpUrl(" HTTPS://host/x "))
        assertFalse(isHttpUrl("http:///x"))
        assertFalse(isHttpUrl("host/x"))
    }

    @Test
    fun editingTheActiveListWithANewUrlCancelsItsLoads() {
        assertTrue(mustCancelLoadsOnEdit(editedIsActive = true, urlChanged = true))
        assertFalse(mustCancelLoadsOnEdit(editedIsActive = true, urlChanged = false))
        assertFalse(mustCancelLoadsOnEdit(editedIsActive = false, urlChanged = true))
    }
}
