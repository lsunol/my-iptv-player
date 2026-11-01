package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.PlaylistKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decoding of the stored lists must never drop data because of one bad field.
 *
 * The raw JSON fields are passed in as a map (see [decodeList]): `org.json` is
 * stubbed out in plain JVM unit tests, and the decoding policy is what matters
 * here. `null` stands for a field that is absent, JSON `null` or of the wrong
 * type, which is exactly what the JSON reader produces for those cases.
 */
class SettingsStoreDecodeTest {

    private fun fields(
        id: String = "list-1",
        name: Any? = "Mi lista",
        kind: Any? = "XTREAM",
        url: Any? = null,
        server: Any? = "http://proveedor.example:8080",
        username: Any? = "usuario",
        password: Any? = "clave",
        lastLoadedAt: Any? = null
    ) = mapOf(
        "id" to id,
        "name" to name,
        "kind" to kind,
        "url" to url,
        "server" to server,
        "username" to username,
        "password" to password,
        "lastLoadedAt" to lastLoadedAt
    )

    @Test
    fun keepsTheListWhenLastLoadedAtIsNotNumeric() {
        val decoded = decodeList(fields(lastLoadedAt = "ayer"))

        assertNotNull(decoded)
        assertNull(decoded!!.lastLoadedAt)
        // The credentials and the URL are what the user cannot retype from memory.
        assertEquals("http://proveedor.example:8080", decoded.server)
        assertEquals("usuario", decoded.username)
        assertEquals("clave", decoded.password)
        assertEquals(PlaylistKind.XTREAM, decoded.kind)
    }

    @Test
    fun keepsTheListForEveryOtherBrokenField() {
        val url = decodeList(fields(kind = "URL", url = "http://proveedor.example/x.m3u", name = 7))
        assertNotNull(url)
        // A name of the wrong type falls back to the id instead of failing.
        assertEquals("list-1", url!!.name)
        assertEquals("http://proveedor.example/x.m3u", url.url)

        val unknownKind = decodeList(fields(kind = "ALGO"))
        assertNotNull(unknownKind)
        assertEquals(PlaylistKind.URL, unknownKind!!.kind)

        val noStamp = decodeList(fields(lastLoadedAt = 0L))
        assertNotNull(noStamp)
        assertNull(noStamp!!.lastLoadedAt)
    }

    @Test
    fun readsAPositiveStamp() {
        val decoded = decodeList(fields(lastLoadedAt = 1_700_000_000_000L))

        assertEquals(1_700_000_000_000L, decoded!!.lastLoadedAt)
    }

    @Test
    fun skipsOnlyEntriesWithoutAnId() {
        assertNull(decodeList(fields(id = "")))
        assertNull(decodeList(mapOf("name" to "sin id")))
    }
}
