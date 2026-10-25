package io.github.yuroyami.libmpv

import kotlin.test.Test
import kotlin.test.fail
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class MpvTest {

    /** A wait that says what it was waiting for when it runs out, and is patient on a slow emulator. */
    private suspend fun <T> awaiting(what: String, millis: Long = 20_000, block: suspend () -> T): T =
        withTimeoutOrNull(millis) { block() } ?: fail("timed out after ${millis}ms waiting for $what")

    private fun start(): Mpv = createTestCore().apply {
        setOption("vo", "null").getOrThrow()
        setOption("ao", "null").getOrThrow()
        setOption("idle", "yes").getOrThrow()
        initialize().getOrThrow()
    }

    @Test
    fun propertiesCommandsAndTwoInstances() {
        val a = start()
        val b = start()
        try {
            assertTrue(a.getString("mpv-version")!!.startsWith("mpv "))
            assertEquals(MpvResult.Ok(true), a[MpvProperty.Flag("pause")].also { a[MpvProperty.Flag("pause")] = true }.let { a[MpvProperty.Flag("pause")] })
            assertEquals(MpvResult.Ok(false), b[MpvProperty.Flag("pause")], "the second core is independent")
            assertEquals(MpvResult.Ok(MpvNode.Str("x")), a.command(MpvCommand.of("expand-text", "x")))
            assertTrue(a.command(MpvCommand.of("no-such-command")) is MpvResult.Fail)
            assertEquals(MpvResult.Fail(MpvError.PROPERTY_NOT_FOUND, "no-such-property"), a.getNode("no-such-property"))
        } finally {
            a.close(); b.close(); a.close()
        }
    }

    @Test
    fun observeAsyncHookAndShutdown(): Unit = runBlocking {
        val mpv = start()
        val wav = writeTestFile("silence.wav", SilentWav.bytes(1))
        try {
            var hookRan = false
            mpv.hook("on_load") { hookRan = true }.getOrThrow()
            val ended = awaiting("the file to end") {
                mpv.command(MpvCommand.of("loadfile", wav)).getOrThrow()
                mpv.events.filterIsInstance<MpvEvent.EndFile>().first()
            }
            assertEquals(EndFileReason.Eof, ended.reason)
            assertTrue(hookRan, "the on_load hook ran and continued, or playback could not have started")
            assertEquals(MpvResult.Ok(MpvNode.Str("y")), awaiting("the async command reply") { mpv.commandAsync(MpvCommand.of("expand-text", "y")) })
            val observed = awaiting("the first observed value") { mpv.observe(MpvProperty.Flag("pause")).first() }
            assertEquals(false, observed)
            // events has no replay, so the collector has to be attached before the quit, or the
            // shutdown is gone by the time anyone looks. On a slow emulator that race is lost.
            val attached = CompletableDeferred<Unit>()
            val shutdown = async {
                mpv.events.onSubscription { attached.complete(Unit) }
                    .filterIsInstance<MpvEvent.Shutdown>().first()
            }
            attached.await()
            mpv.command("quit")
            awaiting("the shutdown event") { shutdown.await() }
        } finally {
            mpv.close()
        }
    }

    /** The events mpv_event_to_node leaves empty: async property replies, INT64 changes, hook names. */
    @Test
    fun asyncReadsInt64ObserversAndHookNamesCarryTheirData(): Unit = runBlocking {
        val mpv = start()
        val wav = writeTestFile("hooked.wav", SilentWav.bytes(1))
        try {
            assertEquals(MpvResult.Ok(100.0), awaiting("the async volume read") { mpv.getAsync(MpvProperties.Volume) })
            val int64 = awaiting("the first INT64 value") { mpv.observeNode("playlist-count", format = 4).first() }
            assertEquals(MpvNode.Int64(0), int64)
            val hookName = CompletableDeferred<String>()
            mpv.hook("on_load") { hookName.complete(it.name) }.getOrThrow()
            mpv.command(MpvCommand.of("loadfile", wav)).getOrThrow()
            assertEquals("on_load", awaiting("the on_load hook") { hookName.await() })
        } finally {
            mpv.close()
        }
    }

    /** close() waits for the calls inside native code; a reader on another thread then ends with IllegalStateException. */
    @Test
    fun closeWhileAnotherThreadReads(): Unit = runBlocking {
        val mpv = start()
        val reading = CompletableDeferred<Unit>()
        val ending = CompletableDeferred<Throwable>()
        launch(Dispatchers.Default) {
            try {
                while (true) { mpv.getNode("time-pos"); mpv[MpvProperties.Volume]; reading.complete(Unit) }
            } catch (t: Throwable) {
                ending.complete(t)
            }
        }
        awaiting("the reader to get going", millis = 10_000) { reading.await() }
        mpv.close()
        val ended = awaiting("the reader to end", millis = 10_000) { ending.await() }
        assertTrue(ended is IllegalStateException, "the reader ended with $ended")
    }

    /** A hook handler that throws is logged; mpv continues and the file loads. */
    @Test
    fun aThrowingHookDoesNotEndTheApp(): Unit = runBlocking {
        val mpv = start()
        val wav = writeTestFile("throwing-hook.wav", SilentWav.bytes(1))
        try {
            mpv.hook("on_load") { throw IllegalStateException("a broken hook") }.getOrThrow()
            val attached = CompletableDeferred<Unit>()
            val loaded = async { mpv.events.onSubscription { attached.complete(Unit) }.first { it == MpvEvent.FileLoaded } }
            attached.await()
            mpv.command(MpvCommand.of("loadfile", wav)).getOrThrow()
            awaiting("the file to load past the failed hook") { loaded.await() }
        } finally {
            mpv.close()
        }
    }

    /** After a quit nothing can reply, so an async call fails at once instead of waiting for ever. */
    @Test
    fun asyncCallsAfterAQuitFail(): Unit = runBlocking {
        val mpv = start()
        try {
            val attached = CompletableDeferred<Unit>()
            val shutdown = async { mpv.events.onSubscription { attached.complete(Unit) }.first { it == MpvEvent.Shutdown } }
            attached.await()
            mpv.command("quit")
            awaiting("the shutdown event") { shutdown.await() }
            val after = awaiting("the async read after the quit", millis = 5_000) { mpv.getAsync(MpvProperties.Volume) }
            assertTrue(after is MpvResult.Fail, "got $after")
        } finally {
            mpv.close()
        }
    }

    /** Log lines arrive on logs only, so a burst of them cannot push EndFile out of events. */
    @Test
    fun logLinesStayOutOfEvents(): Unit = runBlocking {
        val mpv = start()
        val wav = writeTestFile("logged.wav", SilentWav.bytes(1))
        try {
            mpv.requestLogMessages(MpvLogLevel.Verbose).getOrThrow()
            val attached = CompletableDeferred<Unit>()
            val seen = async { mpv.events.onSubscription { attached.complete(Unit) }.takeWhile { it !is MpvEvent.EndFile }.toList() }
            attached.await()
            mpv.command(MpvCommand.of("loadfile", wav)).getOrThrow()
            val events = awaiting("the end of the file") { seen.await() }
            assertTrue(events.none { it is MpvEvent.LogMessage }, "log lines reached events")
        } finally {
            mpv.close()
        }
    }

    @Test
    fun beforeCloseCallbacksRunOnceAndCanBeRemoved() {
        val mpv = start()
        var kept = 0
        var removed = 0
        mpv.onBeforeClose { kept++ }
        mpv.onBeforeClose { removed++ }.dispose()
        mpv.close()
        mpv.close()
        assertEquals(1, kept)
        assertEquals(0, removed)
    }

    /** mpv_terminate_destroy waits for every client of the core, so the owner closes its clients first. */
    @Test
    fun closingACoreClosesItsClients(): Unit = runBlocking {
        val mpv = start()
        val client = mpv.createClient("second")
        val closer = launch(Dispatchers.Default) { mpv.close() }
        assertTrue(withTimeoutOrNull(10_000) { closer.join() } != null, "close blocked on the open client")
        assertTrue(client.isClosed)
    }

    @Test
    fun nonAsciiTextRoundTrips() {
        val mpv = start()
        try {
            mpv.setString("title", "日本語の字幕 ✓").getOrThrow()
            assertEquals("日本語の字幕 ✓", mpv.getString("title"))
        } finally {
            mpv.close()
        }
    }

    /** An MPVLib observer may destroy the core from the event thread. close() must not wait for its own thread. */
    @Test
    fun closeFromAnEventListenerReturnsAtOnce(): Unit = runBlocking {
        val mpv = start()
        val closed = CompletableDeferred<Long>()
        mpv.addEventListener { event ->
            if (event is MpvEvent.ClientMessage) closed.complete(measureTime { mpv.close() }.inWholeMilliseconds)
        }
        mpv.command("script-message", "close-now").getOrThrow()
        assertTrue(withTimeout(10_000) { closed.await() } < 1_000)
        assertTrue(mpv.isClosed)
    }
}
