package io.github.yuroyami.libmpv.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The JNI library in this jar loads and starts a core. Run it under a German locale too: mpv needs C numbers. */
@OptIn(MpvNativeApi::class)
class DesktopLibraryTest {
    @Test
    fun theBundledLibraryStartsACore() {
        val h = MpvNative.create()
        assertNotEquals(0L, h)
        try {
            assertEquals(0, MpvNative.initialize(h))
            val version = MpvNative.getPropertyString(h, "mpv-version")
            assertTrue(version.orEmpty().startsWith("mpv "), "mpv-version answered $version")
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }
}
