package io.github.yuroyami.libmpv

@Suppress("DEPRECATION") // Thread.threadId() starts at Java 19, and the library targets Java 11.
internal actual fun currentThreadKey(): Long = Thread.currentThread().id

internal actual fun logError(message: String, error: Throwable) {
    System.err.println("libmpv-kmp: $message")
    error.printStackTrace()
}

internal actual fun requirePlatformReady() {}
