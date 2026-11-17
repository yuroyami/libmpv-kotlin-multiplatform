package io.github.yuroyami.libmpv

/** A number that names the calling thread, so [Mpv.close] can tell whether it runs on the event thread. */
internal expect fun currentThreadKey(): Long

/** Reports an exception that the library catches so the app keeps running: a failing listener or hook. */
internal expect fun logError(message: String, error: Throwable)

/** Throws when the platform needs a setup step before the first core. Android needs `Mpv.create(context)`. */
internal expect fun requirePlatformReady()

/** Sets the options every new core starts with on this platform: `tls-verify=yes` and the trusted CAs. */
internal expect fun applyPlatformDefaults(mpv: Mpv)
