package io.github.yuroyami.libmpv.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JniLibraryTest {
    @Test
    fun platformNamesFollowTheJvmProperties() {
        assertEquals("macos-arm64", JniLibrary.platformDirectory("Mac OS X", "aarch64"))
        assertEquals("macos-x64", JniLibrary.platformDirectory("Mac OS X", "x86_64"))
        assertEquals("linux-x64", JniLibrary.platformDirectory("Linux", "amd64"))
        assertEquals("linux-arm64", JniLibrary.platformDirectory("Linux", "aarch64"))
        assertEquals("windows-x64", JniLibrary.platformDirectory("Windows 11", "amd64"))
    }

    @Test
    fun theLibraryFileNameFollowsThePlatform() {
        assertEquals("libmpv_kmp_jni.dylib", JniLibrary.libraryFileName("macos-arm64"))
        assertEquals("libmpv_kmp_jni.so", JniLibrary.libraryFileName("linux-x64"))
        assertEquals("mpv_kmp_jni.dll", JniLibrary.libraryFileName("windows-x64"))
    }

    @Test
    fun aMissingPlatformNamesItselfAndTheOverrides() {
        val message = JniLibrary.missingLibraryMessage("macos-x64")
        assertTrue("macos-x64" in message)
        assertTrue("-Dlibmpv-kmp.jni.path" in message)
        assertTrue("java.library.path" in message)
    }
}
