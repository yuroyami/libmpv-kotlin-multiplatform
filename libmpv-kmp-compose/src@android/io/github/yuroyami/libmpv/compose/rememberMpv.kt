package io.github.yuroyami.libmpv.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.yuroyami.libmpv.InternalLibmpvKmpApi
import io.github.yuroyami.libmpv.Mpv
import io.github.yuroyami.libmpv.getOrThrow
import io.github.yuroyami.libmpv.view.MpvOptions
import io.github.yuroyami.libmpv.view.closeInBackground

/**
 * One initialised core for the life of the composition. It closes on a thread of its own when the
 * composition forgets it, and also when Compose abandons the composition before it commits.
 */
@Composable
public fun rememberMpv(options: MpvOptions = MpvOptions()): Mpv {
    val context = LocalContext.current.applicationContext
    return remember { RememberedMpv(context, options) }.mpv
}

/** A DisposableEffect never runs for an abandoned composition; a RememberObserver hears about it. */
@OptIn(InternalLibmpvKmpApi::class)
private class RememberedMpv(context: Context, options: MpvOptions) : RememberObserver {
    val mpv: Mpv = Mpv.create(context).also { core ->
        try {
            options.applyTo(core)
            core.initialize().getOrThrow()
        } catch (t: Throwable) {
            core.close()
            throw t
        }
    }

    override fun onRemembered() = Unit
    override fun onForgotten() = mpv.closeInBackground()
    override fun onAbandoned() = mpv.closeInBackground()
}
