package io.github.yuroyami.libmpv.jni

/**
 * The libmpv client API, one function per call, every handle a `Long`.
 *
 * Nodes cross as byte arrays in the format `node_codec.cpp` and `NodeCodec.kt` share. Functions
 * returning `Int` return the `mpv_error` code. Nothing here validates a handle: a wrong or closed
 * handle is undefined behaviour in libmpv, which is why `Mpv` owns every handle and this object
 * is opt-in.
 *
 * On the JVM and Android the functions are JNI calls into `libmpv_kmp_jni`, and Android's own calls
 * live in `MpvAndroidNative`. On macOS and iOS they call libmpv directly, through cinterop.
 */
@MpvNativeApi
public expect object MpvNative {
    public fun clientApiVersion(): Long
    public fun create(): Long
    public fun createClient(handle: Long, name: String?): Long
    public fun createWeakClient(handle: Long, name: String?): Long
    public fun clientName(handle: Long): String
    public fun clientId(handle: Long): Long
    public fun initialize(handle: Long): Int
    public fun destroy(handle: Long)
    public fun terminateDestroy(handle: Long)
    public fun loadConfigFile(handle: Long, path: String): Int
    public fun timeUs(handle: Long): Long

    public fun setOptionString(handle: Long, name: String, value: String): Int
    public fun setOptionNode(handle: Long, name: String, node: ByteArray): Int

    public fun command(handle: Long, args: Array<String>): Int
    /** Returns a result envelope: `i32` error, then the result node. */
    public fun commandNode(handle: Long, args: ByteArray): ByteArray
    public fun commandString(handle: Long, command: String): Int
    public fun commandAsync(handle: Long, replyId: Long, args: Array<String>): Int
    public fun commandNodeAsync(handle: Long, replyId: Long, args: ByteArray): Int
    public fun abortAsyncCommand(handle: Long, replyId: Long)

    /** Returns a result envelope: `i32` error, then the property as a node. */
    public fun getPropertyNode(handle: Long, name: String): ByteArray
    public fun getPropertyString(handle: Long, name: String): String?
    public fun getPropertyOsdString(handle: Long, name: String): String?
    public fun setPropertyNode(handle: Long, name: String, node: ByteArray): Int
    public fun setPropertyString(handle: Long, name: String, value: String): Int
    public fun getPropertyAsync(handle: Long, replyId: Long, name: String): Int
    public fun setPropertyAsync(handle: Long, replyId: Long, name: String, node: ByteArray): Int
    public fun delProperty(handle: Long, name: String): Int
    public fun observeProperty(handle: Long, replyId: Long, name: String, format: Int): Int
    public fun unobserveProperty(handle: Long, replyId: Long): Int

    public fun requestEvent(handle: Long, eventId: Int, enable: Boolean): Int
    public fun requestLogMessages(handle: Long, minLevel: String): Int
    /** Blocks up to [timeoutSeconds]. Returns an event envelope: `i32` id, `i64` reply id, `i32` error, then the event node. */
    public fun waitEvent(handle: Long, timeoutSeconds: Double): ByteArray
    public fun wakeup(handle: Long)

    public fun hookAdd(handle: Long, replyId: Long, name: String, priority: Int): Int
    public fun hookContinue(handle: Long, hookId: Long): Int

    public fun errorString(code: Int): String
    public fun eventName(eventId: Int): String
}
