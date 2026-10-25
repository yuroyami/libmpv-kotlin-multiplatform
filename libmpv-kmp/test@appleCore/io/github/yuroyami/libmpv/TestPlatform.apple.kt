package io.github.yuroyami.libmpv

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getpid
import kotlin.experimental.ExperimentalNativeApi

internal actual fun createTestCore(): Mpv = Mpv.create()

internal actual fun writeTestFile(name: String, bytes: ByteArray): String {
    val path = "${NSTemporaryDirectory()}libmpv-kmp-${getpid()}-$name"
    val file = fopen(path, "wb") ?: error("cannot write $path")
    try {
        if (bytes.isNotEmpty()) bytes.usePinned { check(fwrite(it.addressOf(0), 1u, bytes.size.toULong(), file) == bytes.size.toULong()) }
    } finally {
        fclose(file)
    }
    return path
}

internal actual val testRunsOnAndroid: Boolean = false

@OptIn(ExperimentalNativeApi::class)
internal actual val testCoreHasLua: Boolean = Platform.osFamily != OsFamily.IOS
