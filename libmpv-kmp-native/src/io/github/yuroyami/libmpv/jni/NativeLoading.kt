package io.github.yuroyami.libmpv.jni

/** Loads `libmpv_kmp_jni` and what it links. [MpvNative] and `MpvAndroidNative` call it first; a second call does nothing. */
internal expect fun loadMpvJni()
