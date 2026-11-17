package io.github.yuroyami.libmpv.sample

import android.content.Context
import io.github.yuroyami.libmpv.view.MpvOptions
import io.github.yuroyami.libmpv.view.SurfaceType

/** The options every sample screen starts from. Only the surface type differs between them. */
object SampleOptions {
    const val DEFAULT_URL: String = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    fun forApp(context: Context, surfaceType: SurfaceType = SurfaceType.Surface): MpvOptions = MpvOptions(
        configDir = context.filesDir,
        cacheDir = context.cacheDir,
        surfaceType = surfaceType,
    )
}
