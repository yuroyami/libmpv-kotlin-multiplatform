package io.github.yuroyami.libmpv.jni

@OptIn(MpvNativeApi::class)
internal actual fun createTestHandle(): Long = MpvNative.create()
