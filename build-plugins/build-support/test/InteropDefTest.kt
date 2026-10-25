package io.github.yuroyami.libmpv.buildtools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InteropDefTest {
    private val headers = File("/repo/libmpv-kmp-native/native/include")

    private fun common(def: String) {
        assertTrue("package = io.github.yuroyami.libmpv.cinterop" in def)
        assertTrue("headers = mpv/client.h" in def)
        assertTrue("headerFilter = mpv/client.h" in def)
        assertTrue("compilerOpts = -I/repo/libmpv-kmp-native/native/include" in def)
    }

    @Test
    fun aPrefixTreeEmbedsItsArchives() {
        val def = renderInteropDef(headers, InteropSource.Prefix(LinkLine(listOf("/t/lib"), listOf("libmpv.a", "libavcodec.a"), listOf("-lz", "-framework", "CoreAudio"))))
        common(def)
        assertTrue("# libmpv-kmp-source: prefix" in def)
        assertTrue("staticLibraries = libmpv.a libavcodec.a" in def)
        assertTrue("libraryPaths = /t/lib" in def)
        assertTrue("linkerOpts = -lz -framework CoreAudio" in def)
    }

    @Test
    fun devModeLinksHomebrewsMpv() {
        val def = renderInteropDef(headers, InteropSource.Dev("/opt/homebrew"))
        common(def)
        assertTrue("# libmpv-kmp-source: dev" in def)
        assertTrue("linkerOpts = -L/opt/homebrew/lib -lmpv" in def)
    }

    @Test
    fun headersOnlyLinksNothing() {
        val def = renderInteropDef(headers, InteropSource.HeadersOnly)
        common(def)
        assertTrue("# libmpv-kmp-source: headers-only" in def)
        assertTrue("linkerOpts" !in def && "staticLibraries" !in def)
    }

    @Test
    fun onlyPrefixDefsPassTheReleaseCheck() {
        val dir = createTempDirectory().toFile()
        val prefix = dir.resolve("a.def").apply { writeText(renderInteropDef(headers, InteropSource.Prefix(LinkLine(emptyList(), emptyList(), emptyList())))) }
        val dev = dir.resolve("b.def").apply { writeText(renderInteropDef(headers, InteropSource.Dev("/opt/homebrew"))) }
        assertEquals(listOf(dev), nonReleaseInteropDefs(listOf(prefix, dev)))
    }
}
