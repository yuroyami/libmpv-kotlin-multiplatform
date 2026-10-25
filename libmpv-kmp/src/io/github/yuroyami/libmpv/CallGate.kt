package io.github.yuroyami.libmpv

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/**
 * Counts the calls inside native code, so a close can wait for them before the handle is freed.
 * A counter, not a lock: calls from many threads run at once, and calls after [close] fail at once.
 */
internal class CallGate {
    private val lock = SynchronizedObject()
    private var inside = 0
    /** Completed by the last [leave] after [close] started waiting. */
    private var drained: CompletableDeferred<Unit>? = null

    @Volatile
    var isClosed: Boolean = false
        private set

    /** Enters the gate, or returns false once it is closed. Every true needs one [leave]. */
    fun enter(): Boolean = synchronized(lock) {
        if (isClosed) return false
        inside++
        true
    }

    fun leave() {
        val done = synchronized(lock) { if (--inside == 0) drained else null }
        done?.complete(Unit)
    }

    /** Closes the gate and waits until every call inside has left. True for the first caller only. */
    fun close(): Boolean {
        val wait = synchronized(lock) {
            if (isClosed) return false
            isClosed = true
            if (inside == 0) null else CompletableDeferred<Unit>().also { drained = it }
        }
        if (wait != null) runBlocking { wait.await() }
        return true
    }

    /** Runs [block] inside the gate. Throws [IllegalStateException] once the gate is closed. */
    inline fun <R> call(block: () -> R): R {
        check(enter()) { "this Mpv is closed" }
        try {
            return block()
        } finally {
            leave()
        }
    }

    /** Runs [block] inside the gate, or skips it once the gate is closed. */
    inline fun callIfOpen(block: () -> Unit) {
        if (!enter()) return
        try {
            block()
        } finally {
            leave()
        }
    }
}
