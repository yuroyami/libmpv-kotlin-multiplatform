package io.github.yuroyami.libmpv

/**
 * Catalog entries that only some builds of mpv have. A catalog failure on a new platform is fixed by an
 * entry here, never in the test.
 */
internal object CatalogAvailability {
    /** Properties that only an Android build of mpv has. */
    val androidOnlyProperties: Set<String> = setOf("android-surface-size")
}
