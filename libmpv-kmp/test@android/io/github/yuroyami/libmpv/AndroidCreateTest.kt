package io.github.yuroyami.libmpv

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A host test, where no native library can load: create() must refuse before it touches MpvNative. */
class AndroidCreateTest {
    @Test
    fun createWithoutAContextFailsClearly() {
        val e = assertFailsWith<IllegalStateException> { Mpv.create() }
        assertTrue("Mpv.create(context)" in e.message.orEmpty())
    }
}
