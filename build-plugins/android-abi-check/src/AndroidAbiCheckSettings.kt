package io.github.yuroyami.libmpvkt.buildplugins.androidabi

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface AndroidAbiCheckSettings {
    /** Directory of the committed Android ABI dump, relative to the module directory. The file is `<module>.api`. */
    val dumpDir: String get() = "api/android"
}
