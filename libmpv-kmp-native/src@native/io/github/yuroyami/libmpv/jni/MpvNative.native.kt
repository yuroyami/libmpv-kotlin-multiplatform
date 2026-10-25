package io.github.yuroyami.libmpv.jni

import io.github.yuroyami.libmpv.cinterop.MPV_ERROR_INVALID_PARAMETER
import io.github.yuroyami.libmpv.cinterop.MPV_FORMAT_NODE
import io.github.yuroyami.libmpv.cinterop.mpv_abort_async_command
import io.github.yuroyami.libmpv.cinterop.mpv_client_api_version
import io.github.yuroyami.libmpv.cinterop.mpv_client_id
import io.github.yuroyami.libmpv.cinterop.mpv_client_name
import io.github.yuroyami.libmpv.cinterop.mpv_command
import io.github.yuroyami.libmpv.cinterop.mpv_command_async
import io.github.yuroyami.libmpv.cinterop.mpv_command_node
import io.github.yuroyami.libmpv.cinterop.mpv_command_node_async
import io.github.yuroyami.libmpv.cinterop.mpv_command_string
import io.github.yuroyami.libmpv.cinterop.mpv_create
import io.github.yuroyami.libmpv.cinterop.mpv_create_client
import io.github.yuroyami.libmpv.cinterop.mpv_create_weak_client
import io.github.yuroyami.libmpv.cinterop.mpv_del_property
import io.github.yuroyami.libmpv.cinterop.mpv_destroy
import io.github.yuroyami.libmpv.cinterop.mpv_error_string
import io.github.yuroyami.libmpv.cinterop.mpv_event_name
import io.github.yuroyami.libmpv.cinterop.mpv_free
import io.github.yuroyami.libmpv.cinterop.mpv_free_node_contents
import io.github.yuroyami.libmpv.cinterop.mpv_get_property
import io.github.yuroyami.libmpv.cinterop.mpv_get_property_async
import io.github.yuroyami.libmpv.cinterop.mpv_get_property_osd_string
import io.github.yuroyami.libmpv.cinterop.mpv_get_property_string
import io.github.yuroyami.libmpv.cinterop.mpv_get_time_us
import io.github.yuroyami.libmpv.cinterop.mpv_hook_add
import io.github.yuroyami.libmpv.cinterop.mpv_hook_continue
import io.github.yuroyami.libmpv.cinterop.mpv_initialize
import io.github.yuroyami.libmpv.cinterop.mpv_load_config_file
import io.github.yuroyami.libmpv.cinterop.mpv_node
import io.github.yuroyami.libmpv.cinterop.mpv_observe_property
import io.github.yuroyami.libmpv.cinterop.mpv_request_event
import io.github.yuroyami.libmpv.cinterop.mpv_request_log_messages
import io.github.yuroyami.libmpv.cinterop.mpv_set_option
import io.github.yuroyami.libmpv.cinterop.mpv_set_option_string
import io.github.yuroyami.libmpv.cinterop.mpv_set_property
import io.github.yuroyami.libmpv.cinterop.mpv_set_property_async
import io.github.yuroyami.libmpv.cinterop.mpv_set_property_string
import io.github.yuroyami.libmpv.cinterop.mpv_terminate_destroy
import io.github.yuroyami.libmpv.cinterop.mpv_unobserve_property
import io.github.yuroyami.libmpv.cinterop.mpv_wait_event
import io.github.yuroyami.libmpv.cinterop.mpv_wakeup
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import platform.posix.LC_NUMERIC
import platform.posix.setlocale

private typealias MpvHandle = cnames.structs.mpv_handle

private fun h(handle: Long): CPointer<MpvHandle>? = handle.toCPointer()

/** A string mpv returned, freed with mpv_free once it is a Kotlin string. */
private fun CPointer<ByteVar>?.takeMpvString(): String? = this?.let { s -> s.toKString().also { mpv_free(s) } }

/** The libmpv client API through cinterop, one call per function: a line-for-line port of `mpv_kmp.cpp`. */
@MpvNativeApi
public actual object MpvNative {
    public actual fun clientApiVersion(): Long = mpv_client_api_version().toLong()

    public actual fun create(): Long {
        // mpv_create returns no handle under any other numeric locale, and the app can change it at any time.
        setlocale(LC_NUMERIC, "C")
        return mpv_create().toLong()
    }

    public actual fun createClient(handle: Long, name: String?): Long = mpv_create_client(h(handle), name).toLong()
    public actual fun createWeakClient(handle: Long, name: String?): Long = mpv_create_weak_client(h(handle), name).toLong()
    public actual fun clientName(handle: Long): String = mpv_client_name(h(handle))?.toKString().orEmpty()
    public actual fun clientId(handle: Long): Long = mpv_client_id(h(handle))
    public actual fun initialize(handle: Long): Int = mpv_initialize(h(handle))
    public actual fun destroy(handle: Long): Unit = mpv_destroy(h(handle))
    public actual fun terminateDestroy(handle: Long): Unit = mpv_terminate_destroy(h(handle))
    public actual fun loadConfigFile(handle: Long, path: String): Int = mpv_load_config_file(h(handle), path)
    public actual fun timeUs(handle: Long): Long = mpv_get_time_us(h(handle))

    public actual fun setOptionString(handle: Long, name: String, value: String): Int = mpv_set_option_string(h(handle), name, value)

    public actual fun setOptionNode(handle: Long, name: String, node: ByteArray): Int = memScoped {
        val n = decodeNode(node) ?: return MPV_ERROR_INVALID_PARAMETER
        mpv_set_option(h(handle), name, MPV_FORMAT_NODE, n)
    }

    public actual fun command(handle: Long, args: Array<String>): Int = memScoped {
        val argv = allocArray<CPointerVar<ByteVar>>(args.size + 1)
        args.forEachIndexed { i, a -> argv[i] = a.cstr.ptr }
        argv[args.size] = null
        mpv_command(h(handle), argv)
    }

    public actual fun commandNode(handle: Long, args: ByteArray): ByteArray = memScoped {
        val input = decodeNode(args) ?: return resultEnvelope(MPV_ERROR_INVALID_PARAMETER, null)
        val result = alloc<mpv_node>()
        val r = mpv_command_node(h(handle), input, result.ptr)
        resultEnvelope(r, result.ptr).also { if (r >= 0) mpv_free_node_contents(result.ptr) }
    }

    public actual fun commandString(handle: Long, command: String): Int = mpv_command_string(h(handle), command)

    public actual fun commandAsync(handle: Long, replyId: Long, args: Array<String>): Int = memScoped {
        val argv = allocArray<CPointerVar<ByteVar>>(args.size + 1)
        args.forEachIndexed { i, a -> argv[i] = a.cstr.ptr }
        argv[args.size] = null
        mpv_command_async(h(handle), replyId.toULong(), argv)
    }

    public actual fun commandNodeAsync(handle: Long, replyId: Long, args: ByteArray): Int = memScoped {
        val input = decodeNode(args) ?: return MPV_ERROR_INVALID_PARAMETER
        mpv_command_node_async(h(handle), replyId.toULong(), input)
    }

    public actual fun abortAsyncCommand(handle: Long, replyId: Long): Unit = mpv_abort_async_command(h(handle), replyId.toULong())

    public actual fun getPropertyNode(handle: Long, name: String): ByteArray = memScoped {
        val v = alloc<mpv_node>()
        val r = mpv_get_property(h(handle), name, MPV_FORMAT_NODE, v.ptr)
        resultEnvelope(r, v.ptr).also { if (r >= 0) mpv_free_node_contents(v.ptr) }
    }

    public actual fun getPropertyString(handle: Long, name: String): String? = mpv_get_property_string(h(handle), name).takeMpvString()
    public actual fun getPropertyOsdString(handle: Long, name: String): String? = mpv_get_property_osd_string(h(handle), name).takeMpvString()

    public actual fun setPropertyNode(handle: Long, name: String, node: ByteArray): Int = memScoped {
        val n = decodeNode(node) ?: return MPV_ERROR_INVALID_PARAMETER
        mpv_set_property(h(handle), name, MPV_FORMAT_NODE, n)
    }

    public actual fun setPropertyString(handle: Long, name: String, value: String): Int = mpv_set_property_string(h(handle), name, value)

    public actual fun getPropertyAsync(handle: Long, replyId: Long, name: String): Int =
        mpv_get_property_async(h(handle), replyId.toULong(), name, MPV_FORMAT_NODE)

    public actual fun setPropertyAsync(handle: Long, replyId: Long, name: String, node: ByteArray): Int = memScoped {
        val n = decodeNode(node) ?: return MPV_ERROR_INVALID_PARAMETER
        mpv_set_property_async(h(handle), replyId.toULong(), name, MPV_FORMAT_NODE, n)
    }

    public actual fun delProperty(handle: Long, name: String): Int = mpv_del_property(h(handle), name)

    public actual fun observeProperty(handle: Long, replyId: Long, name: String, format: Int): Int =
        mpv_observe_property(h(handle), replyId.toULong(), name, format.toUInt())

    public actual fun unobserveProperty(handle: Long, replyId: Long): Int = mpv_unobserve_property(h(handle), replyId.toULong())

    public actual fun requestEvent(handle: Long, eventId: Int, enable: Boolean): Int =
        mpv_request_event(h(handle), eventId.toUInt(), if (enable) 1 else 0)

    public actual fun requestLogMessages(handle: Long, minLevel: String): Int = mpv_request_log_messages(h(handle), minLevel)

    public actual fun waitEvent(handle: Long, timeoutSeconds: Double): ByteArray =
        eventEnvelope(mpv_wait_event(h(handle), timeoutSeconds)!!)

    public actual fun wakeup(handle: Long): Unit = mpv_wakeup(h(handle))

    public actual fun hookAdd(handle: Long, replyId: Long, name: String, priority: Int): Int =
        mpv_hook_add(h(handle), replyId.toULong(), name, priority)

    public actual fun hookContinue(handle: Long, hookId: Long): Int = mpv_hook_continue(h(handle), hookId.toULong())
    public actual fun errorString(code: Int): String = mpv_error_string(code)?.toKString().orEmpty()
    public actual fun eventName(eventId: Int): String = mpv_event_name(eventId.toUInt())?.toKString().orEmpty()
}
