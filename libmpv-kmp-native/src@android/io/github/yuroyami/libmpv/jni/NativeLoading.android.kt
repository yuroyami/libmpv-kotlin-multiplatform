package io.github.yuroyami.libmpv.jni

internal actual fun loadMpvJni() {
    // libmpv first: the JNI library links it, and old Android linkers do not search the app's folder.
    System.loadLibrary("mpv")
    System.loadLibrary("mpv_kmp_jni")
}
