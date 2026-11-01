package dev.lluissunol.iptv.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ByteSinkTest {

    @Test
    fun `returns the payload unchanged`() {
        val payload = ByteArray(1_000) { it.toByte() }
        val sink = ByteSink(payload.size)
        sink.write(payload, 0, payload.size)

        assertEquals(payload.size, sink.size)
        assertArrayEquals(payload, sink.take())
    }

    @Test
    fun `grows past the initial capacity across several writes`() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val sink = ByteSink(1)
        sink.write(payload, 0, 100_000)
        sink.write(payload, 100_000, 100_000)

        assertEquals(200_000, sink.size)
        assertArrayEquals(payload, sink.take())
    }

    @Test
    fun `hands over the buffer itself when it is exactly full`() {
        val sink = ByteSink(MIN_CAPACITY)
        sink.write(ByteArray(MIN_CAPACITY), 0, MIN_CAPACITY)

        // Same instance twice: nothing was copied on the way out.
        assertSame(sink.take(), sink.take())
    }

    @Test
    fun `trims a buffer that was over-allocated`() {
        val sink = ByteSink(MIN_CAPACITY)
        sink.write(ByteArray(10), 0, 10)

        assertEquals(10, sink.take().size)
    }

    @Test
    fun `honours offset and length`() {
        val sink = ByteSink(16)
        sink.write(byteArrayOf(9, 9, 1, 2, 3, 9), 2, 3)

        assertArrayEquals(byteArrayOf(1, 2, 3), sink.take())
    }

    private companion object {
        const val MIN_CAPACITY = 65_536
    }
}
