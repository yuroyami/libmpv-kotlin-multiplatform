package io.github.yuroyami.libmpv.view

import io.github.yuroyami.libmpv.InternalLibmpvKmpApi
import io.github.yuroyami.libmpv.Mpv
import kotlin.concurrent.thread

/**
 * Closes the core on a thread of its own. mpv_terminate_destroy waits for mpv to stop, which can take
 * long when a stream is stuck, and the View and Compose wrappers close from the main thread.
 */
@InternalLibmpvKmpApi
public fun Mpv.closeInBackground() {
    if (isClosed) return
    thread(name = "mpv-close-$clientName", isDaemon = true) { close() }
}
