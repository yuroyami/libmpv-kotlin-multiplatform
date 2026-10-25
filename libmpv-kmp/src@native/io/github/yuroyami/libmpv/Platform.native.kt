package io.github.yuroyami.libmpv

import kotlin.native.concurrent.ThreadLocal
import kotlinx.atomicfu.atomic

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
