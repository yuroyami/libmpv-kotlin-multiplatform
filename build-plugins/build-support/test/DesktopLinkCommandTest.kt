package io.github.yuroyami.libmpv.buildtools

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLinkCommandTest {
    @Test
    fun theWindowsLinkIsStaticAndBringsWinpthreads() {
        val lib = Files.createTempDirectory("lib").toFile().apply { resolve("libmpv.a").writeText("x") }
        val mpv = DesktopMpv.Prefix(LinkLine(listOf(lib.path), listOf("libmpv.a"), listOf("-lole32")))
        val command = desktopLinkCommand(
            DesktopPlatform.WindowsX64, mpv, File("/jni"), File("/mpv"), File("/jdk"), File("/md"),
            KonanCross.llvmMingw(File("/m")), File("/out/mpv_kmp_jni.dll"),
        )
        assertEquals("/m/bin/x86_64-w64-mingw32-clang++", command.first())
        // The JNI sources call pthread_key_create. With win32 threads mpv no longer brings winpthreads.
        assertTrue("-lpthread" in command, command.toString())
        assertTrue("-static" in command, command.toString())
    }
}
