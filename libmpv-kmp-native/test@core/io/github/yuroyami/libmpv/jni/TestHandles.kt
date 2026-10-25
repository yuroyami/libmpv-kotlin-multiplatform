package io.github.yuroyami.libmpv.jni

/** A new raw core, after the platform's setup. Android hands FFmpeg the app's context first. */
@OptIn(MpvNativeApi::class)
internal expect fun createTestHandle(): Long
