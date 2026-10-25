package io.github.yuroyami.libmpv

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** A [HashMap] behind one lock, for maps that the event thread and the app's threads share. */
internal class LockedMap<K, V> {
    private val lock = SynchronizedObject()
    private val map = HashMap<K, V>()

    operator fun get(key: K): V? = synchronized(lock) { map[key] }
    operator fun set(key: K, value: V) { synchronized(lock) { map[key] = value } }
    fun remove(key: K): V? = synchronized(lock) { map.remove(key) }
    fun clear() { synchronized(lock) { map.clear() } }
    /** A copy of the keys, safe to walk while others change the map. */
    fun keys(): List<K> = synchronized(lock) { map.keys.toList() }
}
