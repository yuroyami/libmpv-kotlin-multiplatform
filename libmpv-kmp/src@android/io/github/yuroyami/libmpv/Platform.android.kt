package io.github.yuroyami.libmpv

import android.util.Log
import kotlin.concurrent.Volatile

internal object AndroidPlatform {
    /** Set once `Mpv.create(context)` has handed the JVM and the context to FFmpeg. */
    @Volatile
    var ready: Boolean = false
}

@Suppress("DEPRECATION") // Thread.threadId() starts at API 36.
internal actual fun currentThreadKey(): Long = Thread.currentThread().id

internal actual fun logError(message: String, error: Throwable) {
    Log.e("libmpv-kmp", message, error)
}

internal actual fun requirePlatformReady() {
    check(AndroidPlatform.ready) { "On Android, create the first core with Mpv.create(context)" }
}
