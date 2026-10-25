package io.github.yuroyami.libmpv

import io.github.yuroyami.libmpv.stream.MpvStream
import io.github.yuroyami.libmpv.stream.MpvStreamProvider
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A Kotlin stream provider on the desktop JVM: the stream callbacks of stream_cb.cpp run outside Android. */
class DesktopStreamTest {
    @Test
    fun aKotlinStreamPlaysToTheEnd(): Unit = runBlocking {
        val mpv = createTestCore().apply {
            setOption("vo", "null").getOrThrow(); setOption("ao", "null").getOrThrow(); setOption("idle", "yes").getOrThrow()
            initialize().getOrThrow()
        }
        val wav = SilentWav.bytes(seconds = 1)
        try {
            mpv.addStreamProtocol("silence", object : MpvStreamProvider {
                override fun open(uri: String): MpvStream = object : MpvStream {
                    var pos = 0
                    override val isSeekable = true
                    override fun read(into: ByteBuffer): Int {
                        val n = minOf(into.remaining(), wav.size - pos)
                        if (n <= 0) return 0
                        into.put(wav, pos, n); pos += n; return n
                    }
                    override fun seek(offset: Long): Long { pos = offset.toInt().coerceIn(0, wav.size); return pos.toLong() }
                    override fun size(): Long = wav.size.toLong()
                    override fun close() {}
                }
            }).getOrThrow()
            // events has no replay, so the collector is attached before the command.
            val attached = CompletableDeferred<Unit>()
            val ended = async { mpv.events.onSubscription { attached.complete(Unit) }.filterIsInstance<MpvEvent.EndFile>().first() }
            attached.await()
            mpv.command(MpvCommands.loadFile("silence://one")).getOrThrow()
            assertEquals(EndFileReason.Eof, withTimeout(10_000) { ended.await() }.reason)
        } finally {
            mpv.close()
        }
    }
}
