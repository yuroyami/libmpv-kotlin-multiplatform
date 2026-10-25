package io.github.yuroyami.libmpv.jni

import io.github.yuroyami.libmpv.cinterop.MPV_EVENT_GET_PROPERTY_REPLY
import io.github.yuroyami.libmpv.cinterop.MPV_EVENT_HOOK
import io.github.yuroyami.libmpv.cinterop.MPV_EVENT_NONE
import io.github.yuroyami.libmpv.cinterop.MPV_EVENT_PROPERTY_CHANGE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_BYTE_ARRAY
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_DOUBLE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_FLAG
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_INT64
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NODE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NODE_ARRAY
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NODE_MAP
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NONE
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_OSD_STRING
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_STRING
import io.github.yuroyami.libmpv.cinterop.mpv_byte_array
import io.github.yuroyami.libmpv.cinterop.mpv_event
import io.github.yuroyami.libmpv.cinterop.mpv_event_hook
import io.github.yuroyami.libmpv.cinterop.mpv_event_property
import io.github.yuroyami.libmpv.cinterop.mpv_event_to_node
import io.github.yuroyami.libmpv.cinterop.mpv_free_node_contents
import io.github.yuroyami.libmpv.cinterop.mpv_node
import io.github.yuroyami.libmpv.cinterop.mpv_node_list
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.posix.strlen

/*
 * The wire format of NodeCodec.kt and node_codec.cpp, for the Kotlin/Native binding. Tags are mpv_format
 * values, integers are little-endian. This file ports node_codec.cpp and the envelopes of mpv_kmp.cpp.
 */

/** A growable little-endian byte buffer, as node_codec.cpp writes into a std::string. */
internal class WireBuffer {
    private var buf = ByteArray(128)
    private var size = 0

    private fun ensure(n: Int) {
        if (buf.size - size < n) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
    }

    fun u8(v: Int) {
        ensure(1)
        buf[size++] = v.toByte()
    }

    fun i32(v: Int) {
        ensure(4)
        for (i in 0 until 4) buf[size++] = (v ushr (8 * i)).toByte()
    }

    fun i64(v: Long) {
        ensure(8)
        for (i in 0 until 8) buf[size++] = (v ushr (8 * i)).toByte()
    }

    fun f64(v: Double) = i64(v.toRawBits())

    /** [length] raw bytes from [src]. */
    fun bytes(src: CPointer<ByteVar>?, length: Int) {
        if (src == null || length == 0) return
        ensure(length)
        src.readBytes(length).copyInto(buf, size)
        size += length
    }

    /** `u32` length, then the bytes of a C string. A null string is empty. */
    fun cString(s: CPointer<ByteVar>?) {
        val n = if (s == null) 0 else strlen(s).toInt()
        i32(n)
        bytes(s, n)
    }

    /** `u32` length, then the UTF-8 bytes of a map key that this file writes by hand. */
    fun key(s: String) {
        val b = s.encodeToByteArray()
        i32(b.size)
        ensure(b.size)
        b.copyInto(buf, size)
        size += b.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

internal fun WireBuffer.none() = u8(MPV_FORMAT_NONE.toInt())

private fun WireBuffer.string(s: CPointer<ByteVar>?) {
    u8(MPV_FORMAT_STRING.toInt())
    cString(s)
}

private fun WireBuffer.int64Node(v: Long) {
    u8(MPV_FORMAT_INT64.toInt())
    i64(v)
}

private fun WireBuffer.mapHeader(count: Int) {
    u8(MPV_FORMAT_NODE_MAP.toInt())
    i32(count)
}

/** Writes [node] as node_codec.cpp's `encode_node` does. Formats the table lacks become NONE. */
internal fun WireBuffer.node(node: CPointer<mpv_node>?) {
    val n = node?.pointed ?: return none()
    when (n.format) {
        MPV_FORMAT_STRING, MPV_FORMAT_OSD_STRING -> string(n.u.string)
        MPV_FORMAT_FLAG -> { u8(MPV_FORMAT_FLAG.toInt()); u8(if (n.u.flag != 0) 1 else 0) }
        MPV_FORMAT_INT64 -> int64Node(n.u.int64)
        MPV_FORMAT_DOUBLE -> { u8(MPV_FORMAT_DOUBLE.toInt()); f64(n.u.double_) }
        MPV_FORMAT_NODE_ARRAY -> {
            u8(MPV_FORMAT_NODE_ARRAY.toInt())
            val list = n.u.list?.pointed
            val count = list?.num ?: 0
            i32(count)
            for (i in 0 until count) node(list!!.values!![i].ptr)
        }
        MPV_FORMAT_NODE_MAP -> {
            u8(MPV_FORMAT_NODE_MAP.toInt())
            val list = n.u.list?.pointed
            val count = list?.num ?: 0
            i32(count)
            for (i in 0 until count) {
                cString(list!!.keys!![i])
                node(list.values!![i].ptr)
            }
        }
        MPV_FORMAT_BYTE_ARRAY -> {
            u8(MPV_FORMAT_BYTE_ARRAY.toInt())
            val ba = n.u.ba?.pointed
            val length = ba?.size?.toInt() ?: 0
            i32(length)
            bytes(ba?.data?.reinterpret(), length)
        }
        else -> none()
    }
}

/** Reads the bytes of one node, as node_codec.cpp's `decode_node` does. */
private class WireReader(private val data: ByteArray) {
    var pos = 0
    val remaining: Int get() = data.size - pos

    fun u8(): Int? = if (remaining < 1) null else data[pos++].toInt() and 0xff

    /** An unsigned 32-bit count or length, as a Long so that a huge value cannot turn negative. */
    fun u32(): Long? {
        if (remaining < 4) return null
        var v = 0L
        for (i in 0 until 4) v = v or ((data[pos++].toLong() and 0xff) shl (8 * i))
        return v
    }

    fun i64(): Long? {
        if (remaining < 8) return null
        var v = 0L
        for (i in 0 until 8) v = v or ((data[pos++].toLong() and 0xff) shl (8 * i))
        return v
    }

    fun bytes(n: Long): ByteArray? {
        if (n > remaining) return null
        return data.copyOfRange(pos, pos + n.toInt()).also { pos += n.toInt() }
    }
}

/** A NUL-terminated copy of [bytes] in this placement. */
private fun NativePlacement.cBytes(bytes: ByteArray): CPointer<ByteVar> {
    val out = allocArray<ByteVar>(bytes.size + 1)
    for (i in bytes.indices) out[i] = bytes[i]
    out[bytes.size] = 0
    return out
}

private fun NativePlacement.decodeInto(r: WireReader, out: mpv_node, depth: Int): Boolean {
    if (depth > MAX_DEPTH) return false
    val tag = r.u8() ?: return false
    when (tag.toUInt()) {
        MPV_FORMAT_NONE -> out.format = MPV_FORMAT_NONE
        MPV_FORMAT_STRING -> {
            val b = r.u32()?.let(r::bytes) ?: return false
            out.format = MPV_FORMAT_STRING
            out.u.string = cBytes(b)
        }
        MPV_FORMAT_FLAG -> {
            val v = r.u8() ?: return false
            out.format = MPV_FORMAT_FLAG
            out.u.flag = if (v != 0) 1 else 0
        }
        MPV_FORMAT_INT64 -> {
            out.u.int64 = r.i64() ?: return false
            out.format = MPV_FORMAT_INT64
        }
        MPV_FORMAT_DOUBLE -> {
            out.u.double_ = Double.fromBits(r.i64() ?: return false)
            out.format = MPV_FORMAT_DOUBLE
        }
        MPV_FORMAT_NODE_ARRAY, MPV_FORMAT_NODE_MAP -> {
            val isMap = tag.toUInt() == MPV_FORMAT_NODE_MAP
            val n = r.u32() ?: return false
            // Each entry takes at least one byte, a map entry five; this also keeps n within an Int.
            if (n > r.remaining / (if (isMap) 5 else 1)) return false
            val count = n.toInt()
            val list = alloc<mpv_node_list>()
            list.num = count
            val values = allocArray<mpv_node>(maxOf(count, 1))
            list.values = values
            val keys = if (isMap) allocArray<CPointerVar<ByteVar>>(maxOf(count, 1)) else null
            list.keys = keys
            for (i in 0 until count) {
                if (keys != null) keys[i] = cBytes(r.u32()?.let(r::bytes) ?: return false)
                if (!decodeInto(r, values[i], depth + 1)) return false
            }
            out.format = tag.toUInt()
            out.u.list = list.ptr
        }
        MPV_FORMAT_BYTE_ARRAY -> {
            val b = r.u32()?.let(r::bytes) ?: return false
            val ba = alloc<mpv_byte_array>()
            ba.data = cBytes(b)
            ba.size = b.size.toULong()
            out.format = MPV_FORMAT_BYTE_ARRAY
            out.u.ba = ba.ptr
        }
        else -> return false
    }
    return true
}

/** Nesting deeper than this is malformed, as in node_codec.cpp. */
private const val MAX_DEPTH = 64

/** Decodes [bytes] into a node that lives as long as this placement. Null when the bytes are malformed. */
internal fun NativePlacement.decodeNode(bytes: ByteArray): CPointer<mpv_node>? {
    val out = alloc<mpv_node>()
    return if (decodeInto(WireReader(bytes), out, 0)) out.ptr else null
}

/** `i32` error, then [node], or NONE when the call failed. */
internal fun resultEnvelope(error: Int, node: CPointer<mpv_node>?): ByteArray = WireBuffer().apply {
    i32(error)
    if (node != null && error >= 0) node(node) else none()
}.toByteArray()

/** {name, data} for a property event. mpv_event_to_node writes no data for a get-property reply or an INT64 value. */
private fun WireBuffer.propertyEvent(p: mpv_event_property) {
    mapHeader(2)
    key("name")
    string(p.name)
    key("data")
    val data = p.data
    when (p.format) {
        MPV_FORMAT_STRING, MPV_FORMAT_OSD_STRING -> string(data?.reinterpret<CPointerVar<ByteVar>>()?.pointed?.value)
        MPV_FORMAT_FLAG -> { u8(MPV_FORMAT_FLAG.toInt()); u8(if ((data?.reinterpret<IntVar>()?.pointed?.value ?: 0) != 0) 1 else 0) }
        MPV_FORMAT_INT64 -> int64Node(data?.reinterpret<LongVar>()?.pointed?.value ?: 0)
        MPV_FORMAT_DOUBLE -> { u8(MPV_FORMAT_DOUBLE.toInt()); f64(data?.reinterpret<DoubleVar>()?.pointed?.value ?: 0.0) }
        MPV_FORMAT_NODE -> node(data?.reinterpret())
        else -> none()
    }
}

/** {name, hook_id}. mpv_event_to_node leaves the name out. */
private fun WireBuffer.hookEvent(hook: mpv_event_hook) {
    mapHeader(2)
    key("name")
    string(hook.name)
    key("hook_id")
    int64Node(hook.id.toLong())
}

/** `i32` event id, `i64` reply id, `i32` error, then the event's node, as mpv_kmp.cpp's `waitEvent` writes it. */
internal fun eventEnvelope(event: CPointer<mpv_event>): ByteArray {
    val e = event.pointed
    val out = WireBuffer()
    out.i32(e.event_id.toInt())
    out.i64(e.reply_userdata.toLong())
    out.i32(e.error)
    when (e.event_id) {
        MPV_EVENT_NONE -> out.none()
        MPV_EVENT_PROPERTY_CHANGE, MPV_EVENT_GET_PROPERTY_REPLY ->
            e.data?.reinterpret<mpv_event_property>()?.pointed?.let { out.propertyEvent(it) } ?: out.none()
        MPV_EVENT_HOOK -> e.data?.reinterpret<mpv_event_hook>()?.pointed?.let { out.hookEvent(it) } ?: out.none()
        else -> memScoped {
            val n = alloc<mpv_node>()
            if (mpv_event_to_node(n.ptr, event) >= 0) {
                out.node(n.ptr)
                mpv_free_node_contents(n.ptr)
            } else {
                out.none()
            }
        }
    }
    return out.toByteArray()
}
