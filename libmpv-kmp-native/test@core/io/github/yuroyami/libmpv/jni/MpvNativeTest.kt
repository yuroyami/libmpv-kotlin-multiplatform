package io.github.yuroyami.libmpv.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The raw binding on a real core: every call family once, plus a node round trip through the C++ codec. */
@OptIn(MpvNativeApi::class)
class MpvNativeTest {

    private fun start(): Long {
        val h = createTestHandle()
        assertNotEquals(0L, h)
        assertEquals(0, MpvNative.setOptionString(h, "vo", "null"))
        assertEquals(0, MpvNative.setOptionString(h, "ao", "null"))
        assertEquals(0, MpvNative.setOptionString(h, "idle", "yes"))
        assertEquals(0, MpvNative.initialize(h))
        return h
    }

    private fun le32(v: Int) = ByteArray(4) { (v ushr (8 * it)).toByte() }
    private fun le64(v: Long) = ByteArray(8) { (v ushr (8 * it)).toByte() }

    /** Tag 1 STRING with a u32 length. */
    private fun str(s: String): ByteArray {
        val b = s.encodeToByteArray()
        return byteArrayOf(1) + le32(b.size) + b
    }

    /** Tag 7 ARRAY of the given encoded nodes. */
    private fun arr(vararg nodes: ByteArray): ByteArray = byteArrayOf(7) + le32(nodes.size) + nodes.fold(ByteArray(0)) { acc, n -> acc + n }

    /** Tag 4 INT64, little-endian. */
    private fun int64(v: Long): ByteArray = byteArrayOf(4) + le64(v)

    /** Tag 5 DOUBLE, as its IEEE bits. */
    private fun double(v: Double): ByteArray = byteArrayOf(5) + le64(v.toRawBits())

    /** Tag 9 BYTE_ARRAY with a u32 length. */
    private fun bytes(b: ByteArray): ByteArray = byteArrayOf(9) + le32(b.size) + b

    /** Tag 8 MAP: a u32 count, then each key as a u32 length and UTF-8 bytes, followed by its value node. */
    private fun map(vararg entries: Pair<String, ByteArray>): ByteArray =
        byteArrayOf(8) + le32(entries.size) + entries.fold(ByteArray(0)) { acc, (key, value) ->
            val k = key.encodeToByteArray()
            acc + le32(k.size) + k + value
        }

    private fun readLe(bytes: ByteArray, at: Int, count: Int): Long =
        (0 until count).fold(0L) { v, i -> v or ((bytes[at + i].toLong() and 0xff) shl (8 * i)) }

    /** The error and the payload start of a result envelope. */
    private fun envelope(bytes: ByteArray): Pair<Int, ByteArray> = readLe(bytes, 0, 4).toInt() to bytes.copyOfRange(4, bytes.size)

    @Test
    fun versionsAndClientsAndNodes() {
        val h = start()
        try {
            assertTrue(MpvNative.getPropertyString(h, "mpv-version")!!.startsWith("mpv "))
            assertEquals(2L shl 16, MpvNative.clientApiVersion() and 0xffff0000L, "client API major 2")
            val client = MpvNative.createClient(h, "second")
            assertNotEquals(0L, client)
            assertEquals("second", MpvNative.clientName(client))
            MpvNative.destroy(client)

            // A string round trip through user-data: Kotlin bytes in, C++ decodes, mpv stores, C++ encodes, Kotlin bytes out.
            assertEquals(0, MpvNative.setPropertyNode(h, "user-data/libmpv-kmp", str("round trip ✓")))
            val (error, payload) = envelope(MpvNative.getPropertyNode(h, "user-data/libmpv-kmp"))
            assertEquals(0, error)
            assertTrue(payload.contentEquals(str("round trip ✓")))

            // A command that returns a value.
            val (cmdError, cmdPayload) = envelope(MpvNative.commandNode(h, arr(str("expand-text"), str("\${mpv-version}"))))
            assertEquals(0, cmdError)
            assertEquals(1, cmdPayload[0].toInt(), "a STRING node")
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }

    /** Every node kind crosses the C++ codec both ways: numbers, a map, a byte array and an array, stored by mpv and read back. */
    @Test
    fun numbersMapsAndBytesRoundTrip() {
        val h = start()
        try {
            val node = map(
                "count" to int64(-7),
                "ratio" to double(2.5),
                "raw" to bytes(byteArrayOf(0, 1, 2, -1)),
                "names" to arr(str("a"), str("b")),
            )
            assertEquals(0, MpvNative.setPropertyNode(h, "user-data/libmpv-kmp-kinds", node))
            val (error, payload) = envelope(MpvNative.getPropertyNode(h, "user-data/libmpv-kmp-kinds"))
            assertEquals(0, error)
            assertTrue(payload.contentEquals(node), "the node came back changed")
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }

    /**
     * mpv speaks standard UTF-8. 🎬 must arrive as one 4-byte sequence, and bytes that are not
     * UTF-8 (an old Latin-1 tag, say) must come back as U+FFFD, not abort a debuggable app.
     */
    @Test
    fun stringsCrossAsStandardUtf8() {
        val h = start()
        try {
            val text = "ü 🎬"
            assertEquals(0, MpvNative.setPropertyString(h, "user-data/libmpv-kmp-set", text))
            val (error, payload) = envelope(MpvNative.getPropertyNode(h, "user-data/libmpv-kmp-set"))
            assertEquals(0, error)
            assertTrue(payload.contentEquals(str(text)), "mpv stored ${payload.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }}")

            assertEquals(0, MpvNative.command(h, arrayOf("set", "user-data/libmpv-kmp-command", text)))
            val (_, viaCommand) = envelope(MpvNative.getPropertyNode(h, "user-data/libmpv-kmp-command"))
            assertTrue(viaCommand.contentEquals(str(text)), "the string command path stored something else")

            // A STRING node holding Latin-1 "é" then "x". user-data reads back as JSON, hence the quotes.
            val latin1 = byteArrayOf(1, 2, 0, 0, 0, 0xE9.toByte(), 'x'.code.toByte())
            assertEquals(0, MpvNative.setPropertyNode(h, "user-data/libmpv-kmp-latin1", latin1))
            assertEquals("\"\uFFFDx\"", MpvNative.getPropertyString(h, "user-data/libmpv-kmp-latin1"))
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }

    @Test
    fun observeAsyncHookAndLogArriveThroughWaitEvent() {
        val h = start()
        try {
            assertEquals(0, MpvNative.requestLogMessages(h, "v"))
            assertEquals(0, MpvNative.observeProperty(h, 42L, "pause", 3))
            assertEquals(0, MpvNative.hookAdd(h, 77L, "on_load", 0))
            assertEquals(0, MpvNative.commandNodeAsync(h, 99L, arr(str("expand-text"), str("x"))))
            assertEquals(0, MpvNative.setPropertyString(h, "pause", "yes"))

            val seen = mutableSetOf<String>()
            val deadline = TimeSource.Monotonic.markNow() + 10.seconds
            while (deadline.hasNotPassedNow() && !seen.containsAll(listOf("log", "observe", "reply"))) {
                val raw = MpvNative.waitEvent(h, 0.5)
                val id = readLe(raw, 0, 4).toInt()
                val reply = readLe(raw, 4, 8)
                when {
                    id == 2 -> seen += "log"
                    id == 22 && reply == 42L -> seen += "observe"
                    id == 5 && reply == 99L -> seen += "reply"
                }
            }
            assertEquals(setOf("log", "observe", "reply"), seen)
            // mpv_unobserve_property answers with how many it removed, and one was observed.
            assertEquals(1, MpvNative.unobserveProperty(h, 42L))
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }
}
