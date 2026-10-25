package io.github.yuroyami.libmpv

/**
 * Catalog entries that only some builds of mpv have. A catalog failure on a new platform is fixed by an
 * entry here, never in the test.
 */
internal object CatalogAvailability {
    /** Properties that only an Android build of mpv has. */
    val androidOnlyProperties: Set<String> = setOf("android-surface-size")

    /** Properties of mpv's OpenGL outputs, which the static trees leave out (`-Dgl=disabled`, no video yet). */
    val withoutOpenGl: Set<String> = setOf("opengl-es")

    /** Properties of mpv's Lua scripting, which the iOS trees leave out (`-Dlua=disabled`). */
    val withoutLua: Set<String> = setOf("ytdl", "load-scripts", "scripts", "script-opts", "load-stats-overlay", "load-console", "load-auto-profiles")

    /** What a static tree of the desktop or Apple platforms lacks, beside the Android-only properties. */
    val notInStaticTrees: Set<String> = androidOnlyProperties + withoutOpenGl
}
