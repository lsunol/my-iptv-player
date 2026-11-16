package dev.lluissunol.iptv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The unreachable-host short-circuit, exercised with real sockets on
 * loopback: a closed port answers "connection refused" exactly like the
 * firewalled image server of the reported provider.
 */
class ImageHostHealthTest {

    private fun closedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    /** A minimal HTTP server answering 200 to every request; returns port + hit counter. */
    private fun startServer(): Pair<ServerSocket, AtomicInteger> {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val hits = AtomicInteger()
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    hits.incrementAndGet()
                    s.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray()
                    )
                    s.getOutputStream().flush()
                }
            }
        }
        return server to hits
    }

    private fun client(health: ImageHostHealth) = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(health)
        .build()

    private fun get(client: OkHttpClient, url: String) =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test
    fun refusedHostIsMarkedAndThenShortCircuited() {
        val port = closedPort()
        val health = ImageHostHealth()
        val client = client(health)
        val url = "http://127.0.0.1:$port/photo/1.jpg"

        try { get(client, url); fail("expected a connection failure") } catch (e: IOException) {
            assertTrue("first attempt hits the network: $e", e is ConnectException)
        }
        assertEquals(setOf("127.0.0.1:$port"), health.unreachableHosts.value)

        // Second request: no socket is opened, the failure is immediate.
        try { get(client, "http://127.0.0.1:$port/photo/2.jpg"); fail() } catch (e: IOException) {
            assertTrue(e is UnreachableImageHostException)
        }
    }

    @Test
    fun hostIsProbedAgainAfterTheWindowAndClearedOnSuccess() {
        val (server, hits) = startServer()
        server.use {
            var now = 0L
            val health = ImageHostHealth(retryAfterMillis = 1_000, clock = { now })
            val key = "127.0.0.1:${server.localPort}"
            // Simulate a past failure of this (now healthy) host.
            val refusedPort = closedPort()
            val client = client(health)
            try { get(client, "http://127.0.0.1:$refusedPort/x") } catch (_: IOException) {}
            assertTrue(health.unreachableHosts.value.contains("127.0.0.1:$refusedPort"))

            assertEquals(200, get(client, "http://$key/a.jpg"))
            assertEquals(1, hits.get())
            assertFalse(key in health.unreachableHosts.value)

            // The refused host stays short-circuited inside the window ...
            try { get(client, "http://127.0.0.1:$refusedPort/x"); fail() } catch (e: IOException) {
                assertTrue(e is UnreachableImageHostException)
            }
            // ... and is probed for real once it has elapsed.
            now = 2_000
            try { get(client, "http://127.0.0.1:$refusedPort/x"); fail() } catch (e: IOException) {
                assertTrue(e is ConnectException)
            }
        }
    }

    @Test
    fun onlyConnectionFailuresCount() {
        assertTrue(ImageHostHealth.isConnectionFailure(ConnectException("Failed to connect to /1.2.3.4:32400")))
        assertTrue(ImageHostHealth.isConnectionFailure(SocketTimeoutException("failed to connect to /1.2.3.4 (port 80) after 10000ms")))
        assertFalse(ImageHostHealth.isConnectionFailure(SocketTimeoutException("timeout")))
        assertFalse(ImageHostHealth.isConnectionFailure(UnknownHostException("no dns")))
        assertFalse(ImageHostHealth.isConnectionFailure(ConnectException("connect failed: ENETUNREACH (Network is unreachable)")))
        assertFalse(ImageHostHealth.isConnectionFailure(IOException("HTTP 404")))
    }

    @Test
    fun countsPostersOnUnreachableHosts() {
        val urls = sequenceOf(
            "http://10.0.0.1:32400/photo/a",
            "http://10.0.0.1:32400/photo/b",
            "https://cdn.example/t/p/w600/c.jpg",
            null,
            "s:308:/images/broken"
        )
        assertEquals(2, countUnreachablePosters(urls, setOf("10.0.0.1:32400")))
        assertEquals(0, countUnreachablePosters(urls, emptySet()))
        assertEquals(1, countUnreachablePosters(urls, setOf("cdn.example:443")))
    }
}
