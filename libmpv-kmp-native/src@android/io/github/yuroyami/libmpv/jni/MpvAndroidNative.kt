package io.github.yuroyami.libmpv.jni

import android.content.Context
import android.view.Surface

/** The calls only Android has: FFmpeg's JVM hand-off and the surface references mpv's `wid` option takes. */
@MpvNativeApi
public object MpvAndroidNative {
    init {
        loadMpvJni()
    }

    /** Hands the JVM and the application context to FFmpeg (MediaCodec needs both). Once per process; later calls are ignored. */
    public external fun initAndroid(appContext: Context)

    /** A global reference to [surface], as the `int64` mpv's `wid` option takes. Release it with [releaseSurfaceHandle]. */
    public external fun surfaceHandle(surface: Surface): Long
    public external fun releaseSurfaceHandle(surfaceHandle: Long)
}
