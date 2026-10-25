package io.github.yuroyami.libmpv

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred

/** The async calls waiting for mpv's reply, by request id. Once [end] runs, no reply can come. */
internal class PendingReplies {
    private val lock = SynchronizedObject()
    private val waiting = HashMap<Long, CompletableDeferred<MpvEvent?>>()
    private var ended = false

    /** A reply slot for [id], or null once replies have ended. */
    fun register(id: Long): CompletableDeferred<MpvEvent?>? = synchronized(lock) {
        if (ended) return null
        CompletableDeferred<MpvEvent?>().also { waiting[id] = it }
    }

    fun remove(id: Long) {
        synchronized(lock) { waiting.remove(id) }
    }

    /** Hands [event] to the call waiting for [id], if it still waits. */
    fun complete(id: Long, event: MpvEvent) {
        synchronized(lock) { waiting.remove(id) }?.complete(event)
    }

    /** No reply can come any more: every waiting call gets null now, and [register] answers null from here on. */
    fun end() {
        val all = synchronized(lock) {
            ended = true
            waiting.values.toList().also { waiting.clear() }
        }
        all.forEach { it.complete(null) }
    }
}
