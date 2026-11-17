package io.github.yuroyami.libmpv

import kotlin.native.concurrent.ThreadLocal
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getpid
import platform.posix.remove
import platform.posix.rename

private val nextThreadKey = atomic(1L)

/** Taken from [nextThreadKey] the first time a thread asks: Kotlin/Native has no common thread id. */
@ThreadLocal
private object ThreadKey {
    val value: Long = nextThreadKey.getAndIncrement()
}

internal actual fun currentThreadKey(): Long = ThreadKey.value

internal actual fun logError(message: String, error: Throwable) {
    println("libmpv-kmp: $message")
    println(error.stackTraceToString())
}

internal actual fun requirePlatformReady() {}

/**
 * Mozilla's CA list in this user's temporary folder, which no other user can write to. On iOS that folder is
 * inside the app's sandbox. Null when the file cannot be written; the core then fails every certificate check.
 */
@OptIn(ExperimentalForeignApi::class)
private val caFile: String? by lazy {
    val name = "libmpv-kmp-ca-" + MozillaCaBundle.DATE.filter { it.isLetterOrDigit() } + ".pem"
    val path = NSTemporaryDirectory() + name
    if (access(path, F_OK) == 0) return@lazy path
    // Another process can write the same file at the same time, so a rename finishes it.
    val partial = "$path.${getpid()}.partial"
    val bytes = MozillaCaBundle.PEM.encodeToByteArray()
    val file = fopen(partial, "wb")
    if (file == null) {
        logError("cannot write $partial", IllegalStateException("no CA file for https"))
        return@lazy null
    }
    val written = bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.toULong(), file) }
    fclose(file)
    if (written != bytes.size.toULong() || rename(partial, path) != 0) {
        remove(partial)
        if (access(path, F_OK) != 0) {
            logError("cannot write $path", IllegalStateException("no CA file for https"))
            return@lazy null
        }
    }
    path
}

internal actual fun applyPlatformDefaults(mpv: Mpv) {
    mpv.setOption("tls-verify", "yes")
    caFile?.let { mpv.setOption("tls-ca-file", it) }
}
