package dev.lluissunol.iptv.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The streamed load/parse path of [PlaylistRepository]: downloads land in a
 * temp file (never a byte array) and are parsed back from the file, with the
 * charset fallback for Latin-1 lists whose accents live beyond the UTF-8 probe
 * window. Generated playlists are written to temp files at test time and never
 * committed.
 */
class PlaylistRepositoryStreamingTest {

    private val tempFiles = mutableListOf<File>()
    private val cacheDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        cacheDirs.forEach { dir ->
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
        cacheDirs.clear()
    }

    private fun repository(): PlaylistRepository = PlaylistRepository(OkHttpClient())

    private fun tempFile(prefix: String): File =
        File.createTempFile(prefix, ".m3u").also { tempFiles += it }

    /**
     * The correctness-critical fallback: a Latin-1 list whose first ~300 KB
     * (past the 256 KB UTF-8 probe) is pure ASCII, with accented names and
     * groups only in the tail. The probe says UTF-8; the strict parse must hit
     * the malformed byte, re-read the whole file as ISO-8859-1 and come back
     * with real accents, never U+FFFD.
     */
    @Test
    fun `Latin-1 accents after the probe window come out accented`() {
        val file = tempFile("latin1")
        val asciiChannels = file.outputStream().use { output ->
            output.write("#EXTM3U\r\n".toByteArray(Charsets.ISO_8859_1))
            var written = "#EXTM3U\r\n".length
            var index = 0
            while (written < 300 * 1024) {
                val line = "#EXTINF:-1 tvg-id=\"c$index\" group-title=\"ASCII GROUP\",Canal $index\r\n" +
                    "http://host/live/u/p/$index.m3u8\r\n"
                val bytes = line.toByteArray(Charsets.ISO_8859_1)
                output.write(bytes)
                written += bytes.size
                index++
            }
            // The accented tail: 0xD1 (Ñ) and 0xED (í) are invalid UTF-8, and
            // it sits well beyond the 256 KB UTF-8 probe window.
            output.write(
                "#EXTINF:-1 group-title=\"ESPAÑA - Drama\",Película, con coma\r\n".toByteArray(Charsets.ISO_8859_1)
            )
            output.write("http://host/movie/u/p/1.mp4\r\n".toByteArray(Charsets.ISO_8859_1))
            index
        }

        val playlist = repository().parseCached(file, "http://host/get.php")

        assertEquals(asciiChannels + 1, playlist.channels.size)
        assertEquals(file.length(), playlist.bytes)
        val movie = playlist.channels.last()
        assertEquals("Película, con coma", movie.name)
        assertEquals("ESPAÑA - Drama", movie.group)
        assertTrue(movie.searchKey.endsWith("espana drama"))
    }

    /** A UTF-8 list survives the same file path untouched (no false fallback). */
    @Test
    fun `UTF-8 accents survive the file path without the fallback`() {
        val file = tempFile("utf8")
        file.writeText(
            "#EXTM3U\r\n" +
                "#EXTINF:-1 group-title=\"ESPAÑA - Drama\",Película\r\n" +
                "http://host/movie/u/p/1.mp4\r\n",
            Charsets.UTF_8
        )

        val playlist = repository().parseCached(file, "http://host/get.php")

        assertEquals("Película", playlist.channels.single().name)
        assertEquals("ESPAÑA - Drama", playlist.channels.single().group)
    }

    /**
     * The download itself streams to a temp file: progress reports the bytes
     * as they arrive and the parsed count after the parse pass, and the parse
     * consumes the file without the document ever entering the heap. Served by
     * a hand-rolled minimal HTTP responder (no test-only dependency added),
     * writing the body in 1 KB chunks to prove the copy loop accumulates.
     */
    @Test
    fun `streams the download to a file and parses it with progress`() {
        val body = StringBuilder("#EXTM3U\r\n").apply {
            for (i in 1..40) {
                append("#EXTINF:-1,Canal $i\r\nhttp://host/live/u/p/$i.m3u8\r\n")
            }
        }.toString().toByteArray(Charsets.UTF_8)

        val server = TinyHttpServer(body, "application/x-mpegURL")
        server.start()
        try {
            val progress = mutableListOf<Triple<Long, Long, Int>>()
            val loaded = runBlocking {
                repository().load(
                    "http://127.0.0.1:${server.port}/list.m3u",
                    onProgress = { bytes, total, entries ->
                        progress += Triple(bytes, total, entries)
                    }
                )
            }

            assertEquals(40, loaded.playlist.channels.size)
            assertTrue(progress.isNotEmpty())
            assertEquals(40, progress.last().third)
            assertEquals(body.size.toLong(), progress.last().first)
        } finally {
            server.stop()
        }
    }

    /**
     * Minimal HTTP/1.1 responder: enough HEAD/GET handling for one OkHttp
     * client. The extra parameters default to the behaviour the pre-existing
     * streaming tests rely on (200, 1 KB chunks, no delay, finite body):
     * [status] serves failures, [chunkDelayMillis]/[chunkBytes] make a body
     * slow enough to cancel mid-download, and [endless] feeds an unbounded
     * stream (64 KB chunks until the client aborts) to exercise the MAX_BYTES
     * ceiling without materialising it.
     */
    private class TinyHttpServer(
        private val body: ByteArray,
        private val contentType: String,
        private val status: Int = 200,
        private val chunkBytes: Int = 1024,
        private val chunkDelayMillis: Long = 0,
        private val endless: Boolean = false
    ) {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val thread = Thread { serve() }

        val port: Int get() = serverSocket.localPort

        fun start() {
            thread.start()
        }

        fun stop() {
            serverSocket.close()
        }

        private fun serve() {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    socket.use { drainRequestAndRespond(it) }
                }
            } catch (_: Exception) {
                // Stopped.
            }
        }

        private fun drainRequestAndRespond(socket: Socket) {
            val input = socket.getInputStream()
            // Drain the request head up to its blank line (\r\n\r\n).
            val lastFour = StringBuilder(4)
            while (true) {
                val byte = input.read()
                if (byte < 0) return
                if (lastFour.length == 4) lastFour.deleteCharAt(0)
                lastFour.append((byte and 0xFF).toChar())
                if (lastFour.toString() == "\r\n\r\n") break
            }
            socket.getOutputStream().use { output ->
                output.write(
                    ("HTTP/1.1 $status X\r\n" +
                        "Content-Type: $contentType\r\n" +
                        (if (endless) "" else "Content-Length: ${body.size}\r\n") +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
                )
                if (endless) {
                    // Feed until the client aborts (the MAX_BYTES guard throws
                    // on the client side and closes the socket).
                    val chunk = ByteArray(64 * 1024) { (it % 251).toByte() }
                    try {
                        while (true) {
                            output.write(chunk)
                            output.flush()
                            if (chunkDelayMillis > 0) Thread.sleep(chunkDelayMillis)
                        }
                    } catch (_: Exception) {
                        // Client aborted the download.
                    }
                } else {
                    var offset = 0
                    while (offset < body.size) {
                        val count = minOf(chunkBytes, body.size - offset)
                        output.write(body, offset, count)
                        offset += count
                        output.flush()
                        if (chunkDelayMillis > 0) Thread.sleep(chunkDelayMillis)
                    }
                }
            }
        }
    }

    /**
     * A large generated playlist (~15 MB, tens of thousands of entries) parses
     * through the file path into the exact expected channel count; the file is
     * generated into a temp file at test time and never committed.
     */
    @Test
    fun `parses a large generated playlist from a file`() {
        val file = tempFile("large")
        val expected = file.outputStream().use { output ->
            val builder = StringBuilder("#EXTM3U\r\n")
            var index = 0
            while (builder.length < 15 * 1024 * 1024) {
                builder.append(
                    "#EXTINF:-1 tvg-id=\"c$index\" tvg-logo=\"http://logo/x$index.png\" " +
                        "group-title=\"GRUPO ${index % 40}\",Canal $index FHD\r\n" +
                        "http://host/live/u/p/$index.m3u8\r\n"
                )
                index++
            }
            output.write(builder.toString().toByteArray(Charsets.UTF_8))
            index
        }

        val playlist = repository().parseCached(file, "http://host/get.php")

        assertEquals(expected, playlist.channels.size)
        assertEquals(40, playlist.groups.size)
        assertEquals(file.length(), playlist.bytes)
        // Spot check on the LAST entry so a bug that only mangles the tail of
        // the stream (truncation, a broken reader boundary, a dropped final
        // line) is caught and not hidden behind the aggregate counts.
        val tail = playlist.channels.last()
        val lastIndex = expected - 1
        assertEquals("c$lastIndex", tail.tvgId)
        assertEquals("http://logo/x$lastIndex.png", tail.logoUrl)
        assertEquals("Canal $lastIndex FHD", tail.name)
        assertEquals("GRUPO ${lastIndex % 40}", tail.group)
        assertEquals("http://host/live/u/p/$lastIndex.m3u8", tail.url)
    }

    // ------------------------------------------------- staging / commit / discard

    /** A real [PlaylistCache] over a throwaway directory standing in for filesDir. */
    private fun newCache(): Pair<PlaylistCache, File> {
        val dir = File.createTempFile("iptv-cache-dir-", ".dir")
        assertTrue(dir.delete())
        assertTrue(dir.mkdirs())
        cacheDirs += dir
        return PlaylistCache(dir) to dir
    }

    /** Staging files (unique staged temps and commit siblings) left in [dir]. */
    private fun stagingFiles(dir: File): List<String> =
        dir.listFiles()
            ?.map { it.name }
            ?.filter { it.endsWith(".tmp") || it.endsWith(".copy") }
            ?: emptyList()

    private fun m3uBody(namePrefix: String, count: Int): ByteArray =
        StringBuilder("#EXTM3U\r\n").apply {
            for (i in 1..count) {
                append("#EXTINF:-1 tvg-id=\"$namePrefix-$i\",${namePrefix}_$i\r\n")
                append("http://host/live/u/p/$i.m3u8\r\n")
            }
        }.toString().toByteArray(Charsets.UTF_8)

    /**
     * After every failed-load assertion: the previously committed cache is
     * byte-for-byte intact and no staging file leaked into the directory.
     */
    private fun assertCacheIntact(cache: PlaylistCache, dir: File, snapshot: ByteArray) {
        val cached = runBlocking { cache.read("list-1") }
        assertTrue("the committed cache entry vanished", cached != null)
        assertTrue("the committed cache changed", snapshot.contentEquals(cached!!.readBytes()))
        assertTrue("staging files leaked: ${stagingFiles(dir)}", stagingFiles(dir).isEmpty())
    }

    /**
     * Finding 2: an HTML error page served with a UTF-8 BOM (EF BB BF) and a
     * non-`text/html` Content-Type must be reported as `Html`, not slip past
     * the sniff and surface as a misleading `Empty` error.
     */
    @Test
    fun `an HTML error page with a UTF-8 BOM and a non-HTML content type is reported as Html`() {
        val html = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "<!DOCTYPE html><html><body>error</body></html>".toByteArray(Charsets.UTF_8)
        val server = TinyHttpServer(html, "application/octet-stream")
        server.start()
        try {
            val thrown = runCatching {
                runBlocking {
                    repository().load("http://127.0.0.1:${server.port}/list.m3u", { _, _, _ -> })
                }
            }
            assertTrue(
                "expected Html, got ${thrown.exceptionOrNull()}",
                thrown.exceptionOrNull() is PlaylistException.Html
            )
        } finally {
            server.stop()
        }
    }

    /**
     * A successful load installs the staged temp as the cache entry and leaves
     * no staging file behind: the temp was renamed into place, not copied
     * next to the target.
     */
    @Test
    fun `a successful load installs the staged file as the cache and leaves no staging files`() {
        val (cache, dir) = newCache()
        val body = m3uBody("Canal", 30)
        val server = TinyHttpServer(body, "application/x-mpegURL")
        server.start()
        try {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${server.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
            val cached = runBlocking { cache.read("list-1") }
            assertTrue("no cache entry after a successful load", cached != null)
            assertTrue("cache content differs from the download", body.contentEquals(cached!!.readBytes()))
            assertTrue("staging files leaked: ${stagingFiles(dir)}", stagingFiles(dir).isEmpty())
        } finally {
            server.stop()
        }
    }

    /**
     * A second load of the same list replaces the committed entry. On JVMs
     * where rename does not replace an existing target this walks the commit
     * fallback (sibling copy -> swap), proving that path installs the new
     * content instead of emptying the cache; elsewhere it walks the direct
     * rename. The postconditions are the same either way.
     */
    @Test
    fun `a second load replaces the committed cache and still leaves no staging files`() {
        val (cache, dir) = newCache()
        val firstBody = m3uBody("First", 10)
        val secondBody = m3uBody("Second", 10)
        val first = TinyHttpServer(firstBody, "application/x-mpegURL")
        val second = TinyHttpServer(secondBody, "application/x-mpegURL")
        first.start()
        second.start()
        try {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${first.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
                repository().load(
                    "http://127.0.0.1:${second.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
            val cached = runBlocking { cache.read("list-1")!!.readBytes() }
            assertTrue("cache kept the stale first load", secondBody.contentEquals(cached))
            assertTrue("staging files leaked: ${stagingFiles(dir)}", stagingFiles(dir).isEmpty())
        } finally {
            first.stop()
            second.stop()
        }
    }

    /**
     * The commit fallback must never empty the working cache when it cannot
     * install: a staged source that vanished (rename impossible, copy source
     * missing) makes commit return false and leaves the old entry untouched.
     */
    @Test
    fun `a commit whose staged source vanished returns false and keeps the old cache`() {
        val (cache, dir) = newCache()
        val target = File(dir, "playlist-list-1.cache")
        target.writeBytes("old cache".toByteArray())
        val staged = runBlocking { cache.stage("list-1").stageTempFile() }
        tempFiles += staged
        staged.delete() // the staged file disappears before the commit

        val committed = runBlocking { cache.stage("list-1").commit(staged) }

        assertFalse("commit claimed success without a source", committed)
        assertEquals("old cache", target.readText())
        assertTrue("staging files leaked: ${stagingFiles(dir)}", stagingFiles(dir).isEmpty())
    }

    /**
     * Every load failure mode (network error, HTTP error status, HTML body,
     * empty playlist) leaves the previously committed cache byte-for-byte
     * intact and deletes its staged temp.
     */
    @Test
    fun `failed loads leave the committed cache intact and delete the staged temp`() {
        val (cache, dir) = newCache()
        val seedBody = m3uBody("Seed", 20)
        val seed = TinyHttpServer(seedBody, "application/x-mpegURL")
        seed.start()
        runBlocking {
            repository().load(
                "http://127.0.0.1:${seed.port}/list.m3u",
                { _, _, _ -> },
                cache.stage("list-1")
            )
        }
        seed.stop()
        val snapshot = runBlocking { cache.read("list-1")!!.readBytes() }

        fun assertFailure(expected: String, block: () -> Unit) {
            val thrown = runCatching { block() }
            assertTrue(
                "expected $expected, got ${thrown.exceptionOrNull()}",
                thrown.exceptionOrNull()?.javaClass?.simpleName == expected
            )
            assertCacheIntact(cache, dir, snapshot)
        }

        // HTTP error status: rejected before a byte is downloaded.
        val http404 = TinyHttpServer(ByteArray(0), "text/plain", status = 404)
        http404.start()
        assertFailure("Http") {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${http404.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
        }
        http404.stop()

        // A 200 HTML page with a non-HTML content type: caught by the sniff.
        val html = TinyHttpServer(
            "<html><body>error</body></html>".toByteArray(Charsets.UTF_8),
            "application/octet-stream"
        )
        html.start()
        assertFailure("Html") {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${html.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
        }
        html.stop()

        // A syntactically valid M3U with zero channels.
        val empty = TinyHttpServer("#EXTM3U\r\n".toByteArray(Charsets.UTF_8), "application/x-mpegURL")
        empty.start()
        assertFailure("Empty") {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${empty.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
        }
        empty.stop()

        // Connection refused: no listener at the port at all.
        val deadPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertFailure("Network") {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:$deadPort/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
        }
    }

    /**
     * The MAX_BYTES ceiling is a disk guard now: an endless 200 body is
     * aborted past 128 MB, reported as `TooLarge`, and -- like every other
     * failure -- leaves the committed cache intact and deletes the (large)
     * staged temp.
     */
    @Test
    fun `a TooLarge download leaves the committed cache intact and deletes the staged temp`() {
        val (cache, dir) = newCache()
        val seedBody = m3uBody("Seed", 20)
        val seed = TinyHttpServer(seedBody, "application/x-mpegURL")
        seed.start()
        runBlocking {
            repository().load(
                "http://127.0.0.1:${seed.port}/list.m3u",
                { _, _, _ -> },
                cache.stage("list-1")
            )
        }
        seed.stop()
        val snapshot = runBlocking { cache.read("list-1")!!.readBytes() }

        val endless = TinyHttpServer("x".toByteArray(), "application/octet-stream", endless = true)
        endless.start()
        val thrown = runCatching {
            runBlocking {
                repository().load(
                    "http://127.0.0.1:${endless.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
            }
        }
        endless.stop()

        assertTrue(
            "expected TooLarge, got ${thrown.exceptionOrNull()}",
            thrown.exceptionOrNull() is PlaylistException.TooLarge
        )
        assertCacheIntact(cache, dir, snapshot)
    }

    /**
     * Finding 1's cancelled-job path: cancelling a load mid-download (the
     * OkHttp read is not interruptible, so it drains to completion and the
     * parse succeeds) must NOT commit over the working cache, and its unique
     * staged temp must be discarded, not leaked.
     */
    @Test
    fun `a cancelled load discards its own staged temp and never commits`() {
        val (cache, dir) = newCache()
        val seedBody = m3uBody("Seed", 20)
        val seed = TinyHttpServer(seedBody, "application/x-mpegURL")
        seed.start()
        runBlocking {
            repository().load(
                "http://127.0.0.1:${seed.port}/list.m3u",
                { _, _, _ -> },
                cache.stage("list-1")
            )
        }
        seed.stop()
        val snapshot = runBlocking { cache.read("list-1")!!.readBytes() }

        // ~200 KB in 16 KB chunks with a delay: long enough to cancel while
        // the download is provably in flight.
        val replacementBody = m3uBody("Replacement", 3500)
        val slow = TinyHttpServer(replacementBody, "application/x-mpegURL", chunkBytes = 16 * 1024, chunkDelayMillis = 60)
        slow.start()
        try {
            val started = AtomicBoolean(false)
            runBlocking {
                val job = launch {
                    repository().load(
                        "http://127.0.0.1:${slow.port}/list.m3u",
                        { _, _, _ -> started.set(true) },
                        cache.stage("list-1")
                    )
                }
                while (!started.get()) delay(10)
                job.cancel()
                job.join() // the uninterruptible read drains, parses, then discards
            }
            assertCacheIntact(cache, dir, snapshot)
        } finally {
            slow.stop()
        }
    }

    /**
     * The uniqueness half of finding 1: while a cancelled (still draining)
     * load of a list is in flight, a new load of the SAME list stages its own
     * distinct temp and commits it; the stale load can neither touch the new
     * load's staged file nor overwrite the new load's committed cache.
     */
    @Test
    fun `a stale cancelled load cannot touch the new load's staged file`() {
        val (cache, dir) = newCache()
        val staleBody = m3uBody("Stale", 3500)
        val freshBody = m3uBody("Fresh", 100)
        val staleServer = TinyHttpServer(staleBody, "application/x-mpegURL", chunkBytes = 16 * 1024, chunkDelayMillis = 60)
        val freshServer = TinyHttpServer(freshBody, "application/x-mpegURL")
        staleServer.start()
        freshServer.start()
        try {
            val staleStarted = AtomicBoolean(false)
            runBlocking {
                val stale = launch {
                    repository().load(
                        "http://127.0.0.1:${staleServer.port}/list.m3u",
                        { _, _, _ -> staleStarted.set(true) },
                        cache.stage("list-1")
                    )
                }
                // Wait until the stale download is provably in flight, then
                // cancel it -- its uninterruptible read keeps draining.
                while (!staleStarted.get()) delay(10)
                stale.cancel()
                // The new load of the same list stages a different temp and
                // commits while the stale load is still draining.
                repository().load(
                    "http://127.0.0.1:${freshServer.port}/list.m3u",
                    { _, _, _ -> },
                    cache.stage("list-1")
                )
                stale.join()
            }
            val cached = runBlocking { cache.read("list-1") }
            assertTrue("no cache entry after the new load committed", cached != null)
            assertTrue(
                "the stale load overwrote the new load's cache",
                freshBody.contentEquals(cached!!.readBytes())
            )
            assertTrue("staging files leaked: ${stagingFiles(dir)}", stagingFiles(dir).isEmpty())
        } finally {
            staleServer.stop()
            freshServer.stop()
        }
    }

    /**
     * The load-level uniqueness guarantee behind the previous two tests: two
     * stagings of the same list hand out two different, existing temp paths,
     * so no two loads can ever share a staged file.
     */
    @Test
    fun `two stagings of the same list stage distinct temp files`() {
        val (cache, _) = newCache()
        val first = runBlocking { cache.stage("list-1").stageTempFile() }
        tempFiles += first
        val second = runBlocking { cache.stage("list-1").stageTempFile() }
        tempFiles += second

        assertTrue("two stagings shared one temp file", first.absolutePath != second.absolutePath)
        assertTrue(first.exists() && second.exists())
    }
}
