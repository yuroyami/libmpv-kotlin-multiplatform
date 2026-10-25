package io.github.yuroyami.libmpv.jni

import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_BYTE_ARRAY
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_DOUBLE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_FLAG
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_INT64
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NODE_ARRAY
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NONE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_STRING
import io.github.yuroyami.libmpv.cinterop.mpv_byte_array
import io.github.yuroyami.libmpv.cinterop.mpv_node
import io.github.yuroyami.libmpv.cinterop.mpv_node_list
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set

/** The native codec writes and reads the bytes that NodeCodec.kt and node_codec.cpp use. */
class WireNodesTest {
    private fun le32(v: Int) = List(4) { (v ushr (8 * it)) and 0xff }
    private fun le64(v: Long) = List(8) { ((v ushr (8 * it)) and 0xff).toInt() }
    private fun bytes(vararg parts: List<Int>) = parts.flatMap { it }.map { it.toByte() }.toByteArray()
    private fun encode(node: mpv_node) = WireBuffer().apply { node(node.ptr) }.toByteArray()

    private fun MemScope.node(init: mpv_node.() -> Unit): mpv_node = alloc<mpv_node>().apply(init)

    @Test
    fun encodesEveryFormatLikeTheJniCodec(): Unit = memScoped {
        assertContentEquals(bytes(listOf(0)), encode(node { format = MPV_FORMAT_NONE }))
        assertContentEquals(bytes(listOf(1), le32(2), listOf('h'.code, 'i'.code)), encode(node { format = MPV_FORMAT_STRING; u.string = "hi".cstr.ptr }))
        assertContentEquals(bytes(listOf(3, 1)), encode(node { format = MPV_FORMAT_FLAG; u.flag = 1 }))
        assertContentEquals(bytes(listOf(4), le64(1)), encode(node { format = MPV_FORMAT_INT64; u.int64 = 1 }))
        assertContentEquals(bytes(listOf(5), le64(1.5.toRawBits())), encode(node { format = MPV_FORMAT_DOUBLE; u.double_ = 1.5 }))
        val values = allocArray<mpv_node>(2)
        values[0].format = MPV_FORMAT_INT64; values[0].u.int64 = 7
        values[1].format = MPV_FORMAT_NONE
        val list = alloc<mpv_node_list> { num = 2; this.values = values }
        assertContentEquals(bytes(listOf(7), le32(2), listOf(4), le64(7), listOf(0)), encode(node { format = MPV_FORMAT_NODE_ARRAY; u.list = list.ptr }))
        val data = allocArray<ByteVar>(3).also { it[0] = 1; it[1] = 2; it[2] = 3 }
        val ba = alloc<mpv_byte_array> { this.data = data; size = 3u }
        assertContentEquals(bytes(listOf(9), le32(3), listOf(1, 2, 3)), encode(node { format = MPV_FORMAT_BYTE_ARRAY; u.ba = ba.ptr }))
    }

    @Test
    fun decodeRejectsMalformedInput(): Unit = memScoped {
        val nested = bytes(*Array(65) { listOf(7) + le32(1) }, listOf(0))
        assertNull(decodeNode(nested), "65 nested arrays")
        assertNotNull(decodeNode(bytes(*Array(64) { listOf(7) + le32(1) }, listOf(0))), "64 nested arrays")
        assertNull(decodeNode(bytes(listOf(1), le32(5), listOf('h'.code))), "a truncated string")
        assertNull(decodeNode(bytes(listOf(8), le32(Int.MAX_VALUE))), "a map count larger than the bytes")
        assertNull(decodeNode(bytes(listOf(2))), "an unknown tag")
    }

    @Test
    fun decodeThenEncodeIsTheIdentity(): Unit = memScoped {
        val keys = listOf("none", "str", "flag", "int", "dbl", "arr", "bytes")
        val values = listOf(
            listOf(0),
            listOf(1) + le32(3) + "日本".encodeToByteArray().take(3).map { it.toInt() and 0xff },
            listOf(3, 0),
            listOf(4) + le64(-2),
            listOf(5) + le64((-0.25).toRawBits()),
            listOf(7) + le32(1) + listOf(1) + le32(0),
            listOf(9) + le32(2) + listOf(0, 255),
        )
        val map = listOf(8) + le32(keys.size) + keys.zip(values).flatMap { (k, v) ->
            le32(k.length) + k.encodeToByteArray().map { it.toInt() } + v
        }
        val wire = bytes(map)
        val decoded = assertNotNull(decodeNode(wire))
        assertContentEquals(wire, WireBuffer().apply { node(decoded.reinterpret()) }.toByteArray())
    }
}
