package io.github.yuroyami.libmpv

import io.github.yuroyami.libmpv.jni.MpvNative
import io.github.yuroyami.libmpv.jni.MpvNativeApi
import io.github.yuroyami.libmpv.stream.MpvStreamProvider

/** Serves `protocol://` URIs on this core from [provider]. The core holds the provider until it closes. */
@OptIn(MpvNativeApi::class, InternalLibmpvKmpApi::class)
public fun Mpv.addStreamProtocol(protocol: String, provider: MpvStreamProvider): MpvResult<Unit> =
    withNativeHandle { unitResult(MpvNative.streamCbAddRo(it, protocol, provider)) }
