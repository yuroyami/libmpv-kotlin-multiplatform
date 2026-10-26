package io.github.yuroyami.libmpv.buildtools

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeManifestTest {
    private fun tree(vararg files: String): File = Files.createTempDirectory("tree").toFile().apply {
        files.forEach { resolve(it).apply { parentFile.mkdirs(); writeText("x") } }
    }

    @Test
    fun aMissingArchiveIsAProblem() {
        val problems = treeProblems(tree("lib/libmpv.a"), listOf("lib/libmpv.a", "lib/libavcodec.a"))
        assertEquals(1, problems.size)
        assertTrue("lib/libavcodec.a" in problems.single(), problems.single())
    }

    @Test
    fun aSharedLibraryIsAProblem() {
        val problems = treeProblems(tree("lib/libmpv.a", "lib/libfoo.dylib"), listOf("lib/libmpv.a"))
        assertTrue(problems.any { "lib/libfoo.dylib" in it }, problems.toString())
    }

    @Test
    fun aCompleteTreeHasNoProblems() {
        assertEquals(emptyList(), treeProblems(tree("lib/libmpv.a", "lib/pkgconfig/mpv.pc"), listOf("lib/libmpv.a", "lib/pkgconfig/mpv.pc")))
    }
}
