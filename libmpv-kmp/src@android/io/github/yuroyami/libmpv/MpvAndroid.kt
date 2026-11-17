package io.github.yuroyami.libmpv

import android.content.Context
import android.view.Surface
import io.github.yuroyami.libmpv.jni.MpvAndroidNative
import io.github.yuroyami.libmpv.jni.MpvNativeApi

/**
 * A new core. Nothing runs until [Mpv.initialize]. The first call hands the JVM and the app's context to FFmpeg,
 * and points libass at the system fonts.
 */
@OptIn(MpvNativeApi::class)
public fun Mpv.Companion.create(context: Context): Mpv {
    if (!AndroidPlatform.ready) {
        val app = context.applicationContext
        AndroidPlatform.privateDir = app.noBackupFilesDir
        AndroidFonts.install(app)
        MpvAndroidNative.initAndroid(app)
        AndroidPlatform.ready = true
    }
    return create()
}

/** The surface mpv draws into, or null. The surface handshake checks it, so a stale surface cannot detach a newer one. */
public val Mpv.attachedSurface: Surface? get() = attachedWindow as? Surface

/**
 * Gives mpv [surface] to render into: sets `wid`, then `force-window=yes`, then `vo` to
 * [vo]. Call [detachSurface] before the surface is destroyed.
 */
@OptIn(MpvNativeApi::class)
public fun Mpv.attachSurface(surface: Surface, vo: String = "gpu"): MpvResult<Unit> =
    attachWindow(surface, vo, makeRef = { MpvAndroidNative.surfaceHandle(surface) }, releaseRef = MpvAndroidNative::releaseSurfaceHandle)

/** Takes the surface away: `vo=null`, `force-window=no`, `wid=0`, and the reference is released. */
public fun Mpv.detachSurface(): Unit = detachWindow()
