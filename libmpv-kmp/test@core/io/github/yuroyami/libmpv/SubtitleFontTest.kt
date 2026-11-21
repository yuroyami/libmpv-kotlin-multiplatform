package io.github.yuroyami.libmpv

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * libass finds the system fonts with no font from the app: fontconfig on Android and Linux, CoreText on
 * macOS and iOS, DirectWrite on Windows. `vo=image` draws the subtitles into each frame, which makes libass
 * pick a font for every character, and libass logs each pick.
 */
class SubtitleFontTest {

    @Test
    fun subtitlesFindASystemFontForLatinAndArabicText(): Unit = runBlocking {
        // The folder of a test file. Windows paths use backslashes, and mpv stops the video output if this is a file.
        val frames = writeTestFile("frame.bmp", Bmp.gray(64, 64)).let { it.take(it.lastIndexOfAny(charArrayOf('/', '\\'))) }
        val image = writeTestFile("picture.bmp", Bmp.gray(64, 64))
        val subtitles = writeTestFile("text.ass", ASS.encodeToByteArray())
        val mpv = createTestCore().apply {
            setOption("config", "no").getOrThrow()
            setOption("vo", "image").getOrThrow()
            setOption("vo-image-outdir", frames).getOrThrow()
            setOption("ao", "null").getOrThrow()
            setOption("idle", "yes").getOrThrow()
            setOption("image-display-duration", "1").getOrThrow()
            setOption("sub-files", subtitles).getOrThrow()
            initialize().getOrThrow()
        }
        try {
            val fontLines = mutableListOf<String>()
            mpv.requestLogMessages(MpvLogLevel.Verbose).getOrThrow()
            // runBlocking runs every coroutine here on one thread, so the list needs no lock.
            val logging = launch {
                mpv.logs.collect { line -> if ("fontselect" in line.text) fontLines += line.text.trim() }
            }
            val attached = CompletableDeferred<Unit>()
            val ended = async {
                mpv.events.onSubscription { attached.complete(Unit) }.filterIsInstance<MpvEvent.EndFile>().first()
            }
            attached.await()
            mpv.command(MpvCommand.of("loadfile", image)).getOrThrow()
            // The first run on Android builds fontconfig's cache of /system/fonts, which takes a while.
            withTimeoutOrNull(60_000) { ended.await() } ?: fail("the picture never ended")
            logging.cancel()
            val lines = fontLines.toList()
            val failures = lines.filter { "failed to find any fallback" in it }
            assertTrue(failures.isEmpty(), "libass found no font for some characters: $failures")
            assertTrue(lines.any { it.startsWith("fontselect: (") && "->" in it }, "libass picked no font at all; it logged: $lines")
        } finally {
            mpv.close()
        }
    }

    private companion object {
        /** One line of Latin and Arabic text for the whole picture, in a font no system has, so libass falls back. */
        val ASS = """
            |[Script Info]
            |ScriptType: v4.00+
            |PlayResX: 64
            |PlayResY: 64
            |
            |[V4+ Styles]
            |Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Italic, Alignment, MarginL, MarginR, MarginV
            |Style: Default,Arial,12,&H00FFFFFF,0,0,2,0,0,0
            |
            |[Events]
            |Format: Layer, Start, End, Style, Text
            |Dialogue: 0,0:00:00.00,0:00:10.00,Default,Hello مرحبا
            |""".trimMargin()
    }
}

/** An uncompressed 24-bit BMP, which FFmpeg's image demuxer reads as a picture. */
internal object Bmp {
    fun gray(width: Int, height: Int): ByteArray {
        val row = (width * 3 + 3) / 4 * 4
        val pixels = row * height
        fun le(v: Int, n: Int) = ByteArray(n) { (v ushr (8 * it)).toByte() }
        val header = "BM".encodeToByteArray() + le(54 + pixels, 4) + le(0, 4) + le(54, 4) +
            le(40, 4) + le(width, 4) + le(height, 4) + le(1, 2) + le(24, 2) + le(0, 4) + le(pixels, 4) +
            le(2835, 4) + le(2835, 4) + le(0, 4) + le(0, 4)
        return header + ByteArray(pixels) { 0x40 }
    }
}
