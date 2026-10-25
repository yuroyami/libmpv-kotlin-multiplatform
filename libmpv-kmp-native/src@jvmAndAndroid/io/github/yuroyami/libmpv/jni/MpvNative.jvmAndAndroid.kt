package io.github.yuroyami.libmpv.jni

import io.github.yuroyami.libmpv.stream.MpvStreamProvider

// The C++ in native/jni finds these functions by their JNI names, so the class name and package stay fixed.
@MpvNativeApi
public actual object MpvNative {
    init {
        loadMpvJni()
    }

    public actual external fun clientApiVersion(): Long
    public actual external fun create(): Long
    public actual external fun createClient(handle: Long, name: String?): Long
    public actual external fun createWeakClient(handle: Long, name: String?): Long
    public actual external fun clientName(handle: Long): String
    public actual external fun clientId(handle: Long): Long
    public actual external fun initialize(handle: Long): Int
    public actual external fun destroy(handle: Long)
    public actual external fun terminateDestroy(handle: Long)
    public actual external fun loadConfigFile(handle: Long, path: String): Int
    public actual external fun timeUs(handle: Long): Long

    public actual external fun setOptionString(handle: Long, name: String, value: String): Int
    public actual external fun setOptionNode(handle: Long, name: String, node: ByteArray): Int

    public actual external fun command(handle: Long, args: Array<String>): Int
    public actual external fun commandNode(handle: Long, args: ByteArray): ByteArray
    public actual external fun commandString(handle: Long, command: String): Int
    public actual external fun commandAsync(handle: Long, replyId: Long, args: Array<String>): Int
    public actual external fun commandNodeAsync(handle: Long, replyId: Long, args: ByteArray): Int
    public actual external fun abortAsyncCommand(handle: Long, replyId: Long)

    public actual external fun getPropertyNode(handle: Long, name: String): ByteArray
    public actual external fun getPropertyString(handle: Long, name: String): String?
    public actual external fun getPropertyOsdString(handle: Long, name: String): String?
    public actual external fun setPropertyNode(handle: Long, name: String, node: ByteArray): Int
    public actual external fun setPropertyString(handle: Long, name: String, value: String): Int
    public actual external fun getPropertyAsync(handle: Long, replyId: Long, name: String): Int
    public actual external fun setPropertyAsync(handle: Long, replyId: Long, name: String, node: ByteArray): Int
    public actual external fun delProperty(handle: Long, name: String): Int
    public actual external fun observeProperty(handle: Long, replyId: Long, name: String, format: Int): Int
    public actual external fun unobserveProperty(handle: Long, replyId: Long): Int

    public actual external fun requestEvent(handle: Long, eventId: Int, enable: Boolean): Int
    public actual external fun requestLogMessages(handle: Long, minLevel: String): Int
    public actual external fun waitEvent(handle: Long, timeoutSeconds: Double): ByteArray
    public actual external fun wakeup(handle: Long)

    public actual external fun hookAdd(handle: Long, replyId: Long, name: String, priority: Int): Int
    public actual external fun hookContinue(handle: Long, hookId: Long): Int

    public actual external fun errorString(code: Int): String
    public actual external fun eventName(eventId: Int): String

    /** Registers [provider] for `protocol://` URIs on this core. The provider is held for the life of the core. */
    public external fun streamCbAddRo(handle: Long, protocol: String, provider: MpvStreamProvider): Int
}
