package io.github.yuroyami.libmpv

import io.github.yuroyami.libmpv.jni.MpvNative
import io.github.yuroyami.libmpv.jni.MpvNativeApi
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One libmpv core, or one client handle on a shared core (see [createClient]).
 *
 * Create with [create], set options, call [initialize], then use properties and commands from any
 * thread. Events, log lines and observed properties arrive as flows, delivered from this
 * instance's own event thread; collect them on whatever dispatcher suits the caller. [close]
 * ends the core; a `quit` command does the same and [events] then ends with [MpvEvent.Shutdown].
 *
 * mpv errors are [MpvResult] values. [close] waits for calls already inside native code on other
 * threads; calling anything after it throws [IllegalStateException].
 */
@OptIn(MpvNativeApi::class, ExperimentalCoroutinesApi::class)
public class Mpv private constructor(
    private val handle: Long,
    private val ownsCore: Boolean,
    /** The handle this client was made from, which closes it first. Null for a core. */
    private val parent: Mpv? = null,
) : AutoCloseable {

    public val clientName: String = MpvNative.clientName(handle)

    private val closing = atomic(false)

    /** Every native call on [handle] runs inside this gate, so [close] can wait for it. */
    private val gate = CallGate()

    /** Clients made with [createClient]. mpv_terminate_destroy waits for every one, so [close] closes them first. */
    private val clients = LockedMap<Mpv, Unit>()

    @Volatile
    private var initialized = false

    private val replyIds = atomic(1L)
    private val replies = PendingReplies()
    private val observers = LockedMap<Long, (MpvNode) -> Unit>()
    private val hooks = LockedMap<Long, suspend (MpvEvent.Hook) -> Unit>()
    private val hookScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Replaced on every add, so the event thread walks a list that never changes under it. */
    private val listeners = atomic(emptyList<(MpvEvent) -> Unit>())

    /** Every event of this handle except log lines, in order. Slow collectors lose the oldest events, never the newest. */
    public val events: SharedFlow<MpvEvent>
        field = MutableSharedFlow(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Log lines, once [requestLogMessages] asked for them. They arrive here only, so a burst cannot push events out of [events]. */
    public val logs: SharedFlow<MpvEvent.LogMessage>
        field = MutableSharedFlow(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The event thread and its loop, from [initialize] or [createClient] on. */
    private class Pump(val dispatcher: CloseableCoroutineDispatcher, val job: Job)

    @Volatile
    private var eventPump: Pump? = null

    /** [currentThreadKey] of the event thread, once its loop runs. */
    private val pumpThreadKey = atomic(Long.MIN_VALUE)

    /** The raw `mpv_handle`, for the library's own renderer. Not for apps. */
    @InternalLibmpvKmpApi
    public val nativeHandle: Long get() = handle

    /**
     * Runs [block] with the raw handle while the core is certainly alive: [close] waits for it, and a
     * closed core throws [IllegalStateException] instead. For the library's own renderer.
     */
    @InternalLibmpvKmpApi
    public fun <R> withNativeHandle(block: (Long) -> R): R = gate.call { block(handle) }

    private val beforeCloseLock = SynchronizedObject()
    private val beforeClose = mutableListOf<() -> Unit>()

    /**
     * Runs [block] once, before the core is destroyed. A renderer registers here so its render context
     * dies first. Dispose the handle when the owner of [block] closes first, so the core stops holding it.
     */
    public fun onBeforeClose(block: () -> Unit): DisposableHandle {
        synchronized(beforeCloseLock) {
            check(!closing.value) { "this Mpv is closed" }
            beforeClose += block
        }
        return object : DisposableHandle {
            override fun dispose() {
                synchronized(beforeCloseLock) { beforeClose.remove(block) }
            }
        }
    }

    public val isInitialized: Boolean get() = initialized
    public val isClosed: Boolean get() = closing.value

    // ---- lifecycle ----

    /** Sets an option before [initialize]. After it, use [set]; mpv ignores most options set late. */
    public fun <T> setOption(option: MpvProperty<T>, value: T): MpvResult<Unit> {
        val bytes = NodeCodec.encode(option.encode(value))
        return gate.call { unitResult(MpvNative.setOptionNode(handle, option.name, bytes)) }
    }

    public fun setOption(name: String, value: String): MpvResult<Unit> =
        gate.call { unitResult(MpvNative.setOptionString(handle, name, value)) }

    /** Starts the core and this handle's event thread. Once. */
    public fun initialize(): MpvResult<Unit> = gate.call {
        check(!initialized) { "initialize() was already called" }
        val r = MpvNative.initialize(handle)
        if (r >= 0) {
            initialized = true
            startPump()
        }
        unitResult(r)
    }

    /**
     * A second handle on the same core with its own event thread. Closing it does not end the core;
     * closing this handle closes it first, because mpv cannot end a core while a client is open.
     */
    public fun createClient(name: String): Mpv = gate.call {
        val h = MpvNative.createClient(handle, name)
        check(h != 0L) { "mpv_create_client failed" }
        Mpv(h, ownsCore = false, parent = this).also {
            clients[it] = Unit
            it.initialized = true
            it.startPump()
        }
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        synchronized(beforeCloseLock) { beforeClose.toList().also { beforeClose.clear() } }.forEach { runCatching(it) }
        // New calls fail from here, and the calls already inside native code finish before the handle is freed.
        gate.close()
        // mpv_terminate_destroy waits for every client of the core, so the clients go first.
        clients.keys().forEach { it.close() }
        stopPump()
        hookScope.cancel()
        replies.end()
        observers.clear()
        if (ownsCore) MpvNative.terminateDestroy(handle) else MpvNative.destroy(handle)
        parent?.clients?.remove(this)
        releaseWindow()
    }

    // ---- properties ----

    public operator fun <T> get(property: MpvProperty<T>): MpvResult<T> = getNode(property.name).flatDecode(property)

    public operator fun <T> set(property: MpvProperty<T>, value: T): MpvResult<Unit> = setNode(property.name, property.encode(value))

    public fun getNode(name: String): MpvResult<MpvNode> {
        val (error, node) = NodeCodec.decodeEnvelope(gate.call { MpvNative.getPropertyNode(handle, name) })
        return if (error < 0) MpvResult.Fail(MpvError.of(error), name) else MpvResult.Ok(node)
    }

    public fun setNode(name: String, node: MpvNode): MpvResult<Unit> {
        val bytes = NodeCodec.encode(node)
        return gate.call { unitResult(MpvNative.setPropertyNode(handle, name, bytes)) }
    }

    public fun getString(name: String): String? = gate.call { MpvNative.getPropertyString(handle, name) }
    public fun getOsdString(name: String): String? = gate.call { MpvNative.getPropertyOsdString(handle, name) }
    public fun setString(name: String, value: String): MpvResult<Unit> = gate.call { unitResult(MpvNative.setPropertyString(handle, name, value)) }
    public fun delete(name: String): MpvResult<Unit> = gate.call { unitResult(MpvNative.delProperty(handle, name)) }

    /** Reads [property] and suspends until mpv answers. A core that shuts down first gives a [MpvResult.Fail]. */
    public suspend fun <T> getAsync(property: MpvProperty<T>): MpvResult<T> {
        val reply = when (val r = await(property.name) { id -> MpvNative.getPropertyAsync(handle, id, property.name) }) {
            is MpvResult.Fail -> return r
            is MpvResult.Ok -> r.value as MpvEvent.GetPropertyReply
        }
        return if (reply.error.isError) MpvResult.Fail(reply.error, property.name) else MpvResult.Ok(reply.value).flatDecode(property)
    }

    /** Sets [property] and suspends until mpv answers. A core that shuts down first gives a [MpvResult.Fail]. */
    public suspend fun <T> setAsync(property: MpvProperty<T>, value: T): MpvResult<Unit> {
        val bytes = NodeCodec.encode(property.encode(value))
        val reply = when (val r = await(property.name) { id -> MpvNative.setPropertyAsync(handle, id, property.name, bytes) }) {
            is MpvResult.Fail -> return r
            is MpvResult.Ok -> r.value as MpvEvent.SetPropertyReply
        }
        return if (reply.error.isError) MpvResult.Fail(reply.error, property.name) else MpvResult.Ok(Unit)
    }

    /**
     * Every change of [property], starting with its current value, conflated to the latest.
     * Null when mpv reports the property unavailable (no file loaded, for example).
     */
    public fun <T> observe(property: MpvProperty<T>): Flow<T?> = observeNode(property.name).let { flow ->
        callbackFlow {
            val job = launch { flow.collect { node -> trySend(if (node == MpvNode.None) null else runCatching { property.decode(node) }.getOrNull()) } }
            awaitClose { job.cancel() }
        }.conflate()
    }

    /**
     * What the player is doing, kept current from the twelve properties the state is made of.
     * The flow starts observing on first access and stops when the core closes.
     */
    public val playback: StateFlow<MpvPlaybackState> by lazy {
        val idle = PlaybackStateReducer.Inputs(
            idleActive = true, coreIdle = true, pause = false, pausedForCache = false, eofReached = false,
            seeking = false, timePos = null, duration = null, path = null, cachePercent = null,
            seekable = false, speed = 1.0,
        )
        // Two combines of six: combine takes at most five flows before it needs an array.
        val part1 = combine(
            observe(MpvProperties.IdleActive),
            observe(MpvProperties.CoreIdle),
            observe(MpvProperties.Pause),
            observe(MpvProperties.PausedForCache),
            observe(MpvProperties.EofReached),
        ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
        val part2 = combine(
            observe(MpvProperties.Seeking),
            observe(MpvProperties.TimePos),
            observe(MpvProperties.Duration),
            observe(MpvProperties.Path),
            observe(MpvProperties.Seekable),
        ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
        val part3 = combine(
            observe(MpvProperties.CacheBufferingState),
            observe(MpvProperties.Speed),
        ) { a, b -> listOf(a, b) }
        combine(part1, part2, part3) { flags, values, rest ->
            PlaybackStateReducer.reduce(
                PlaybackStateReducer.Inputs(
                    idleActive = flags[0] as? Boolean ?: false,
                    coreIdle = flags[1] as? Boolean ?: false,
                    pause = flags[2] as? Boolean ?: false,
                    pausedForCache = flags[3] as? Boolean ?: false,
                    eofReached = flags[4] as? Boolean ?: false,
                    seeking = values[0] as? Boolean ?: false,
                    timePos = values[1] as? Double,
                    duration = values[2] as? Double,
                    path = values[3] as? String,
                    seekable = values[4] as? Boolean ?: false,
                    cachePercent = (rest[0] as? Long)?.toInt(),
                    speed = rest[1] as? Double ?: 1.0,
                ),
            )
        }.stateIn(hookScope, SharingStarted.Eagerly, PlaybackStateReducer.reduce(idle))
    }

    /** The raw form of [observe]: the node mpv sends, `None` when unavailable. A slow collector loses the oldest values, never the newest. */
    public fun observeNode(name: String, format: Int = 6): Flow<MpvNode> = callbackFlow {
        val id = replyIds.getAndIncrement()
        val r = gate.call {
            observers[id] = { node -> trySend(node) }
            MpvNative.observeProperty(handle, id, name, format)
        }
        if (r < 0) {
            observers.remove(id)
            close(MpvException(MpvError.of(r), name))
        }
        awaitClose {
            observers.remove(id)
            gate.callIfOpen { MpvNative.unobserveProperty(handle, id) }
        }
    }.buffer(64, BufferOverflow.DROP_OLDEST)

    // ---- commands ----

    public fun command(cmd: MpvCommand): MpvResult<MpvNode> {
        val bytes = NodeCodec.encode(cmd.toNode())
        val (error, node) = NodeCodec.decodeEnvelope(gate.call { MpvNative.commandNode(handle, bytes) })
        return if (error < 0) MpvResult.Fail(MpvError.of(error), cmd.toString()) else MpvResult.Ok(node)
    }

    public fun command(vararg args: String): MpvResult<Unit> {
        val argv = arrayOf(*args)
        return gate.call { unitResult(MpvNative.command(handle, argv)) }
    }

    /**
     * Runs [cmd] and suspends until mpv replies. Cancelling the coroutine aborts the command where mpv
     * allows it. A core that shuts down first gives a [MpvResult.Fail].
     */
    public suspend fun commandAsync(cmd: MpvCommand): MpvResult<MpvNode> {
        val bytes = NodeCodec.encode(cmd.toNode())
        val reply = when (val r = await(cmd.toString(), abortable = true) { id -> MpvNative.commandNodeAsync(handle, id, bytes) }) {
            is MpvResult.Fail -> return r
            is MpvResult.Ok -> r.value as MpvEvent.CommandReply
        }
        return if (reply.error.isError) MpvResult.Fail(reply.error, cmd.toString()) else MpvResult.Ok(reply.result)
    }

    // ---- events, logs, hooks, streams ----

    public fun requestLogMessages(minLevel: MpvLogLevel): MpvResult<Unit> =
        gate.call { unitResult(MpvNative.requestLogMessages(handle, minLevel.mpvName)) }

    /** Runs [handler] every time mpv reaches the hook [name]; mpv waits until it returns. An exception from it is logged, and mpv continues. */
    public fun hook(name: String, priority: Int = 0, handler: suspend (MpvEvent.Hook) -> Unit): MpvResult<Unit> {
        val id = replyIds.getAndIncrement()
        val r = gate.call {
            hooks[id] = handler
            MpvNative.hookAdd(handle, id, name, priority)
        }
        if (r < 0) hooks.remove(id)
        return unitResult(r)
    }

    /**
     * For [MPVLib]: [listener] sees every event synchronously on the event thread, in mpv's order,
     * before the flows do. An exception from it is logged.
     */
    internal fun addEventListener(listener: (MpvEvent) -> Unit) {
        listeners.update { it + listener }
    }

    /** For [MPVLib]: observes [name] in [format] under reply id 0, which only the event listeners see. Returns mpv's code. */
    internal fun observeForListeners(name: String, format: Int): Int = gate.call { MpvNative.observeProperty(handle, 0, name, format) }

    /** For [MPVLib]: log lines at `terminal-default`, which follows `msg-level`, as 0.1.0 asked. */
    internal fun requestTerminalDefaultLogs(): MpvResult<Unit> =
        gate.call { unitResult(MpvNative.requestLogMessages(handle, "terminal-default")) }

    // ---- the window ----

    private var windowRef: Long = 0
    private var releaseWindowRef: ((Long) -> Unit)? = null

    /** The window mpv draws into, or null. The platform's attach checks it, so a stale window cannot detach a newer one. */
    @Volatile
    internal var attachedWindow: Any? = null
        private set

    /**
     * Gives mpv [window] to render into: sets `wid` to the reference [makeRef] returns, then
     * `force-window=yes`, then `vo` to [vo]. [releaseRef] frees that reference once mpv lets go.
     */
    internal fun attachWindow(window: Any, vo: String, makeRef: () -> Long, releaseRef: (Long) -> Unit): MpvResult<Unit> = gate.call {
        // mpv can still be opening a window on the one attached before, on its own thread. Its
        // reference dies only once mpv has let go, or mpv reads a reference that is gone.
        if (windowRef != 0L) {
            MpvNative.setPropertyString(handle, "vo", "null")
            MpvNative.setOptionString(handle, "force-window", "no")
            MpvNative.setOptionNode(handle, "wid", NodeCodec.encode(MpvNode.Int64(0)))
        }
        releaseWindow()
        windowRef = makeRef()
        releaseWindowRef = releaseRef
        attachedWindow = window
        val r = MpvNative.setOptionNode(handle, "wid", NodeCodec.encode(MpvNode.Int64(windowRef)))
        if (r < 0) {
            unitResult(r)
        } else {
            MpvNative.setOptionString(handle, "force-window", "yes")
            unitResult(MpvNative.setPropertyString(handle, "vo", vo))
        }
    }

    /** Takes the window away: `vo=null`, `force-window=no`, `wid=0`, and the reference is released. */
    internal fun detachWindow() {
        gate.call {
            MpvNative.setPropertyString(handle, "vo", "null")
            MpvNative.setOptionString(handle, "force-window", "no")
            MpvNative.setOptionNode(handle, "wid", NodeCodec.encode(MpvNode.Int64(0)))
            releaseWindow()
        }
    }

    private fun releaseWindow() {
        attachedWindow = null
        val ref = windowRef
        windowRef = 0
        val release = releaseWindowRef
        releaseWindowRef = null
        if (ref != 0L) release?.invoke(ref)
    }

    // ---- internals ----

    private fun <T> MpvResult<MpvNode>.flatDecode(property: MpvProperty<T>): MpvResult<T> = when (this) {
        is MpvResult.Fail -> this
        is MpvResult.Ok -> try {
            MpvResult.Ok(property.decode(value))
        } catch (e: IllegalArgumentException) {
            MpvResult.Fail(MpvError.PROPERTY_FORMAT, e.message)
        }
    }

    /**
     * Starts an async request and suspends for mpv's reply. A refused start fails with mpv's own code;
     * a core that shuts down or closes before it replies fails with [MpvError.GENERIC].
     */
    private suspend fun await(what: String, abortable: Boolean = false, start: (Long) -> Int): MpvResult<MpvEvent> {
        check(!isClosed) { "this Mpv is closed" }
        check(initialized) { "initialize() first" }
        val id = replyIds.getAndIncrement()
        val reply = replies.register(id) ?: return MpvResult.Fail(MpvError.GENERIC, "$what: the core has shut down")
        val r = try {
            gate.call { start(id) }
        } catch (e: IllegalStateException) {
            replies.remove(id)
            throw e
        }
        if (r < 0) {
            replies.remove(id)
            return MpvResult.Fail(MpvError.of(r), what)
        }
        return try {
            reply.await()?.let { MpvResult.Ok(it) } ?: MpvResult.Fail(MpvError.GENERIC, "$what: the core shut down before replying")
        } catch (e: CancellationException) {
            replies.remove(id)
            if (abortable) gate.callIfOpen { MpvNative.abortAsyncCommand(handle, id) }
            throw e
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startPump() {
        // One thread per handle: waitEvent blocks it until mpv has an event. On the JVM it is a daemon thread.
        val dispatcher = newSingleThreadContext("mpv-events-$clientName")
        eventPump = Pump(dispatcher, CoroutineScope(dispatcher).launch { pump() })
    }

    /** Wakes the event loop and waits up to five seconds for it, unless [close] runs on the event thread itself. */
    private fun stopPump() {
        val pump = eventPump ?: return
        if (currentThreadKey() == pumpThreadKey.value) {
            // The loop sees the closed gate once this event's listeners return. Its thread closes after that.
            CoroutineScope(Dispatchers.Default).launch {
                pump.job.join()
                pump.dispatcher.close()
            }
            return
        }
        MpvNative.wakeup(handle)
        runBlocking { withTimeoutOrNull(5_000) { pump.job.join() } }
        pump.dispatcher.close()
    }

    private fun pump() {
        pumpThreadKey.value = currentThreadKey()
        while (!gate.isClosed) {
            // Sleeps until mpv has an event or close() wakes it; mpv keeps a wakeup that comes early.
            val event = EventDecoder.decode(MpvNative.waitEvent(handle, -1.0)) ?: continue
            dispatch(event)
            if (event is MpvEvent.Shutdown) break
        }
        // No reply can come once this loop ends, so waiting async calls fail instead of hanging.
        replies.end()
    }

    private fun dispatch(event: MpvEvent) {
        for (listener in listeners.value) {
            try {
                listener(event)
            } catch (e: Exception) {
                logError("an event listener failed on $event", e)
            }
        }
        when (event) {
            is MpvEvent.PropertyChange -> observers[event.replyId]?.invoke(event.value)
            is MpvEvent.CommandReply -> replies.complete(event.replyId, event)
            is MpvEvent.GetPropertyReply -> replies.complete(event.replyId, event)
            is MpvEvent.SetPropertyReply -> replies.complete(event.replyId, event)
            is MpvEvent.Hook -> hooks[event.replyId]?.let { handler ->
                hookScope.launch {
                    try {
                        handler(event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A bug in the app's handler: log it, let mpv continue, and keep the app running.
                        logError("hook ${event.name} failed", e)
                    } finally {
                        gate.callIfOpen { MpvNative.hookContinue(handle, event.hookId) }
                    }
                }
            }
            is MpvEvent.LogMessage -> {
                logs.tryEmit(event)
                return
            }
            else -> Unit
        }
        events.tryEmit(event)
    }

    public companion object {
        /**
         * A new core. Nothing runs until [initialize]. On Android, create the first core with
         * `Mpv.create(context)`, which hands the app's context to FFmpeg first.
         */
        public fun create(): Mpv {
            requirePlatformReady()
            val h = MpvNative.create()
            check(h != 0L) { "mpv_create failed" }
            return Mpv(h, ownsCore = true)
        }

        /** The libmpv client API version, major in the high 16 bits. */
        public val apiVersion: Long get() = MpvNative.clientApiVersion()
    }
}
