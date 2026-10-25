package io.github.yuroyami.libmpvkt.buildplugins.androidnatives

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface AndroidNativesSettings {
    /** The folder with one subfolder per ABI, relative to the project root. buildscripts/ and fetch-natives.sh write it. */
    val nativeLibsDir: String

    /** The file names that every ABI folder must hold. */
    val files: List<String>

    /** The minSdk of the AAR that loads the libraries. A library linked for a higher API fails the check. */
    val minApi: Int

    /** A symbol that libmpvkt_jni.so must contain, or null for no such check. */
    val probeSymbol: String?

    /** A text that libc++_shared.so must contain, or null for no such check. */
    val libcxxMarker: String?
}
