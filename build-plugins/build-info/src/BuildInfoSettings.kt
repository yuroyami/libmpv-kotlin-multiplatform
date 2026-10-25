package io.github.yuroyami.libmpv.buildplugins.buildinfo

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface BuildInfoSettings {
    /** The committed `BuildInfo.kt`, relative to the module directory. */
    val file: String

    /** The API level the native libraries are linked for. `BuildInfo.MIN_SDK` carries it. */
    val minSdk: Int get() = 21
}
