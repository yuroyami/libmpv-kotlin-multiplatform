package io.github.yuroyami.libmpv

import android.util.Log
import kotlin.concurrent.Volatile

internal object AndroidPlatform {
    /** Set once `Mpv.create(context)` has handed the JVM and the context to FFmpeg. */
    @Volatile
    var ready: Boolean = false

    /** The app's no-backup folder, from `Mpv.create(context)`. Only this app can write to it. */
    @Volatile
    var privateDir: java.io.File? = null

    val trust = DefaultTrust {
        TrustedCertificates.writeNamedByContent(java.io.File(checkNotNull(privateDir), "libmpv-kmp"), TrustedCertificates.pem())
    }
}

@Suppress("DEPRECATION") // Thread.threadId() starts at API 36.
internal actual fun currentThreadKey(): Long = Thread.currentThread().id

internal actual fun logError(message: String, error: Throwable) {
    Log.e("libmpv-kmp", message, error)
}

internal actual fun requirePlatformReady() {
    check(AndroidPlatform.ready) { "On Android, create the first core with Mpv.create(context)" }
}

internal actual fun applyPlatformDefaults(mpv: Mpv): Unit = AndroidPlatform.trust.applyTo(mpv)
