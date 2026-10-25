package io.github.yuroyami.libmpv

/**
 * The byte form nodes take across JNI. Tags are `mpv_format` values, integers are little-endian;
 * the C++ side (`node_codec.cpp`) implements the same table. One buffer per call, no reflection.
 */
public object NodeCodec {
    private const val NONE = 0
    private const val STRING = 1
    private const val FLAG = 3
    private const val INT64 = 4
    private const val DOUBLE = 5
    private const val ARRAY = 7
    private const val MAP = 8
    private const val BYTES = 9

    /** An error code and a node, as `commandNode` and `getPropertyNode` return them. */
    public data class ResultEnvelope(val error: Int, val node: MpvNode)

    /** What `waitEvent` returns: the event id, the reply userdata, the error, and the event's node. */
    public data class EventEnvelope(val eventId: Int, val replyId: Long, val error: Int, val node: MpvNode)

    public fun encode(node: MpvNode): ByteArray {
        val out = Writer()
        out.node(node)
        return out.toByteArray()
    }

    public fun decode(bytes: ByteArray): MpvNode = Reader(bytes).node()

    public fun decodeEnvelope(bytes: ByteArray): ResultEnvelope {
        val r = Reader(bytes)
        return ResultEnvelope(r.i32(), r.node())
    }

    public fun decodeEvent(bytes: ByteArray): EventEnvelope {
        val r = Reader(bytes)
        return EventEnvelope(r.i32(), r.i64(), r.i32(), r.node())
    }

    private class Writer {
        private var buf = ByteArray(256)
        private var size = 0

        private fun ensure(n: Int) {
            if (buf.size - size >= n) return
            buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        private fun u8(v: Int) {
            ensure(1)
            buf[size++] = v.toByte()
        }

        private fun u32(v: Int) {
            ensure(4)
            for (i in 0 until 4) buf[size++] = (v ushr (8 * i)).toByte()
        }

        private fun i64(v: Long) {
            ensure(8)
            for (i in 0 until 8) buf[size++] = (v ushr (8 * i)).toByte()
        }

        private fun raw(b: ByteArray) {
            ensure(b.size)
            b.copyInto(buf, size)
            size += b.size
        }

        private fun str(s: String) {
            val b = s.encodeToByteArray()
            u32(b.size)
            raw(b)
        }

        fun node(n: MpvNode) {
            when (n) {
                MpvNode.None -> u8(NONE)
                is MpvNode.Str -> { u8(STRING); str(n.value) }
                is MpvNode.Flag -> { u8(FLAG); u8(if (n.value) 1 else 0) }
                is MpvNode.Int64 -> { u8(INT64); i64(n.value) }
                is MpvNode.Dbl -> { u8(DOUBLE); i64(n.value.toRawBits()) }
                is MpvNode.Arr -> { u8(ARRAY); u32(n.values.size); n.values.forEach { node(it) } }
                is MpvNode.Dict -> { u8(MAP); u32(n.values.size); n.values.forEach { (k, v) -> str(k); node(v) } }
                is MpvNode.Bytes -> { u8(BYTES); u32(n.value.size); raw(n.value) }
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    /** Deeper than anything mpv sends; a guard against a malformed buffer, not a format limit. */
    private const val MAX_DEPTH = 64

    private class Reader(private val buf: ByteArray) {
        private var pos = 0
        private var depth = 0

        private val remaining: Int get() = buf.size - pos

        private fun need(n: Int) {
            require(remaining >= n) { "truncated node buffer: needed $n bytes at $pos, $remaining left" }
        }

        fun u8(): Int {
            need(1)
            return buf[pos++].toInt() and 0xff
        }

        fun i32(): Int {
            need(4)
            var v = 0
            for (i in 0 until 4) v = v or ((buf[pos++].toInt() and 0xff) shl (8 * i))
            return v
        }

        fun i64(): Long {
            need(8)
            var v = 0L
            for (i in 0 until 8) v = v or ((buf[pos++].toLong() and 0xff) shl (8 * i))
            return v
        }

        private fun bytes(n: Int): ByteArray {
            need(n)
            return buf.copyOfRange(pos, pos + n).also { pos += n }
        }

        private fun u32(): Int {
            val v = i32()
            require(v >= 0) { "negative length" }
            return v
        }

        private fun str(): String {
            // Invalid UTF-8 decodes to U+FFFD, as String(bytes, UTF_8) does on the JVM.
            return bytes(u32()).decodeToString()
        }

        /** A count of entries that each take at least [minBytes], checked before anything is allocated. */
        private fun count(minBytes: Int): Int {
            val n = u32()
            require(n <= remaining / minBytes) { "node count $n cannot fit in $remaining bytes" }
            return n
        }

        fun node(): MpvNode {
            require(depth < MAX_DEPTH) { "nodes nested deeper than $MAX_DEPTH" }
            depth++
            try {
                return when (val tag = u8()) {
                    NONE -> MpvNode.None
                    STRING -> MpvNode.Str(str())
                    FLAG -> MpvNode.Flag(u8() != 0)
                    INT64 -> MpvNode.Int64(i64())
                    DOUBLE -> MpvNode.Dbl(Double.fromBits(i64()))
                    ARRAY -> MpvNode.Arr(List(count(minBytes = 1)) { node() })
                    MAP -> MpvNode.Dict(LinkedHashMap<String, MpvNode>().also { m -> repeat(count(minBytes = 5)) { m[str()] = node() } })
                    BYTES -> MpvNode.Bytes(bytes(u32()))
                    else -> throw IllegalArgumentException("unknown node tag $tag at ${pos - 1}")
                }
            } finally {
                depth--
            }
        }
    }
}
