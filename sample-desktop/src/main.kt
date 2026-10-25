package io.github.yuroyami.libmpv.sample.desktop

import io.github.yuroyami.libmpv.Mpv
import io.github.yuroyami.libmpv.MpvCommands
import io.github.yuroyami.libmpv.MpvPlaybackState
import io.github.yuroyami.libmpv.getOrThrow
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit = runBlocking {
    val file = args.firstOrNull() ?: silence().absolutePath
    val mpv = Mpv.create()
    try {
        // No window yet on the desktop. keep-open=yes holds the file at its end, which the playback reports as Ended.
        mpv.setOption("vo", "null").getOrThrow()
        mpv.setOption("keep-open", "yes").getOrThrow()
        mpv.setOption("idle", "yes").getOrThrow()
        mpv.initialize().getOrThrow()
        println("mpv ${mpv.getString("mpv-version")}, playing $file")
        val states = mpv.playback.map { it.status }.distinctUntilChanged().onEach { println("status: $it") }
        mpv.command(MpvCommands.loadFile(file)).getOrThrow()
        states.first { it == MpvPlaybackState.Status.Ended }
    } finally {
        mpv.close()
    }
}

/** Two seconds of 8 kHz mono 16-bit silence: a 44 byte WAV header and zero samples. */
private fun silence(): File {
    val sampleRate = 8000
    val dataSize = sampleRate * 2 * 2
    fun le(v: Int, n: Int) = ByteArray(n) { (v ushr (8 * it)).toByte() }
    val header = "RIFF".toByteArray() + le(36 + dataSize, 4) + "WAVE".toByteArray() +
        "fmt ".toByteArray() + le(16, 4) + le(1, 2) + le(1, 2) +
        le(sampleRate, 4) + le(sampleRate * 2, 4) + le(2, 2) + le(16, 2) +
        "data".toByteArray() + le(dataSize, 4)
    return File.createTempFile("libmpv-kmp-silence", ".wav").apply {
        writeBytes(header + ByteArray(dataSize))
        deleteOnExit()
    }
}
