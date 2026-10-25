package io.github.yuroyami.libmpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** The gate that keeps a handle alive while calls are inside native code. */
class CallGateTest {

    @Test
    fun closeWaitsForTheCallsInside(): Unit = runBlocking {
        val gate = CallGate()
        val inside = CompletableDeferred<Unit>()
        val leave = CompletableDeferred<Unit>()
        val caller = launch(Dispatchers.Default) { gate.call { inside.complete(Unit); runBlocking { leave.await() } } }
        withTimeout(5_000) { inside.await() }

        val closed = CompletableDeferred<Unit>()
        launch(Dispatchers.Default) { gate.close(); closed.complete(Unit) }
        assertNull(withTimeoutOrNull(200) { closed.await() }, "close returned while a call was still inside")
        leave.complete(Unit)
        assertTrue(withTimeoutOrNull(5_000) { closed.await() } != null, "close did not return after the call left")
        caller.join()
    }

    @Test
    fun callsAfterCloseFailAtOnce() {
        val gate = CallGate()
        assertEquals(2, gate.call { gate.call { 2 } }, "a call inside a call is fine")
        assertTrue(gate.close())
        assertFalse(gate.close(), "only the first close reports true")
        assertTrue(gate.isClosed)
        assertFailsWith<IllegalStateException> { gate.call { 1 } }
        var ran = false
        gate.callIfOpen { ran = true }
        assertFalse(ran)
    }

    /** The gate counts calls; it is not a lock, so calls from many threads run at the same time. */
    @Test
    fun callsRunInParallel(): Unit = runBlocking {
        val gate = CallGate()
        val entered = atomic(0)
        val both = CompletableDeferred<Unit>()
        val sawTheOther = atomic(0)
        List(2) {
            launch(Dispatchers.Default) {
                gate.call {
                    if (entered.incrementAndGet() == 2) both.complete(Unit)
                    if (runBlocking { withTimeoutOrNull(5_000) { both.await() } } != null) sawTheOther.incrementAndGet()
                }
            }
        }.forEach { it.join() }
        assertEquals(2, sawTheOther.value)
    }
}
