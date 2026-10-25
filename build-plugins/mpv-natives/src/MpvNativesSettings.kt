package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface MpvNativesSettings {
    /** Where Homebrew lives. On macOS without a static tree, the JNI library links Homebrew's mpv (dev mode). */
    val homebrewPrefix: String get() = "/opt/homebrew"

    /** The Kotlin/Native version whose clang and sysroots the Linux and Windows links use. Keep it equal to `settings.kotlin.version`. */
    val kotlinVersion: String get() = "2.4.21"
}
