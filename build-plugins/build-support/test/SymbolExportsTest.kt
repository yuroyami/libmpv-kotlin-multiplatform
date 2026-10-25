package io.github.yuroyami.libmpv.buildtools

import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolExportsTest {
    @Test
    fun onlyJniSymbolsPass() {
        assertEquals(emptyList(), unexpectedExports(listOf("_JNI_OnLoad", "_Java_io_github_yuroyami_libmpv_jni_MpvNative_create")))
        assertEquals(listOf("av_malloc"), unexpectedExports(listOf("JNI_OnLoad", "av_malloc")))
    }

    @Test
    fun theDllExportTableIsRead() {
        val objdump = """
            Export Table:
             DLL name: mpv_kmp_jni.dll
             Ordinal base: 1
             Ordinal      RVA  Name
                   1 0x0001a2b0 JNI_OnLoad
                   2 0x0001a400 mpv_create

            The Import Tables:
        """.trimIndent()
        assertEquals(listOf("JNI_OnLoad", "mpv_create"), peExports(objdump))
    }
}
