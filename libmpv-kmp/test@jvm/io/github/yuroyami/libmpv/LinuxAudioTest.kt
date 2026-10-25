package io.github.yuroyami.libmpv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The static alsa-lib of the Linux trees plays through ALSA's null device. Skipped off Linux. The CI job sets
 * `ALSA_CONFIG_PATH`, because a JVM cannot set its own environment, and the test writes the device there.
 */
class LinuxAudioTest {
    @Test
    fun alsaPlaysSilenceToTheEnd(): Unit = runBlocking {
        if (!System.getProperty("os.name").startsWith("Linux")) return@runBlocking println("skipped: not Linux")
        val config = System.getenv("ALSA_CONFIG_PATH") ?: error("Set ALSA_CONFIG_PATH to a writable file for this test.")
        File(config).writeText("pcm.!default { type null }\n")
        val mpv = createTestCore().apply {
            setOption("vo", "null").getOrThrow(); setOption("ao", "alsa").getOrThrow()
            setOption("keep-open", "yes").getOrThrow(); setOption("idle", "yes").getOrThrow()
            initialize().getOrThrow()
        }
        try {
            mpv.command(MpvCommands.loadFile(writeTestFile("alsa.wav", SilentWav.bytes(seconds = 1)))).getOrThrow()
            withTimeout(10_000) { mpv.playback.first { it.status == MpvPlaybackState.Status.Ended } }
            assertEquals("alsa", mpv.getString("current-ao"))
        } finally { mpv.close() }
    }
}
