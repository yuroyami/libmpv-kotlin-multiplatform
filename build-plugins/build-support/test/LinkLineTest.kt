package io.github.yuroyami.libmpv.buildtools

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkLineTest {
    @Test
    fun splitLinkLineSortsFlagsIntoTheThreeKinds() {
        val lib = createTempDirectory().toFile().apply { resolve("libmpv.a").createNewFile(); resolve("libavcodec.a").createNewFile() }
        val line = splitLinkLine(listOf("-L$lib", "-lmpv", "-lavcodec", "-lz", "-framework", "CoreAudio", "-pthread"))
        assertEquals(listOf(lib.path), line.libraryPaths)
        assertEquals(listOf("libmpv.a", "libavcodec.a"), line.archives)
        assertEquals(listOf("-lz", "-framework", "CoreAudio", "-pthread"), line.systemFlags)
    }
}
