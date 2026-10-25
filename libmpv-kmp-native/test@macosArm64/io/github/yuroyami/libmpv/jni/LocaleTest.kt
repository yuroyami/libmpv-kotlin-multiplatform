package io.github.yuroyami.libmpv.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import platform.posix.LC_NUMERIC
import platform.posix.setlocale

/** mpv returns no core under a numeric locale other than C, so create() sets C before every mpv_create. */
@OptIn(MpvNativeApi::class)
class LocaleTest {
    @Test
    fun aGermanNumericLocaleStillStartsACore() {
        if (setlocale(LC_NUMERIC, "de_DE.UTF-8") == null) return println("skipped: no de_DE.UTF-8 locale")
        val h = MpvNative.create()
        assertNotEquals(0L, h)
        try {
            assertEquals(0, MpvNative.initialize(h))
            assertEquals(0, MpvNative.setPropertyString(h, "speed", "1.5"))
            assertEquals("1.500000", MpvNative.getPropertyString(h, "speed"))
        } finally {
            MpvNative.terminateDestroy(h)
        }
    }
}
