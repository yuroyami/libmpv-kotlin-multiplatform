package io.github.yuroyami.libmpv

import java.io.File
import java.nio.file.Files

internal actual fun createTestCore(): Mpv = Mpv.create()

internal actual fun writeTestFile(name: String, bytes: ByteArray): String =
    File(Files.createTempDirectory("libmpv-kmp-test").toFile(), name).apply { writeBytes(bytes); deleteOnExit() }.absolutePath

internal actual val testRunsOnAndroid: Boolean = false
