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

    @Test
    fun theLinuxAndWindowsLinksDropTheDebugSections() {
        val lib = Files.createTempDirectory("lib").toFile().apply { resolve("libmpv.a").writeText("x") }
        val mpv = DesktopMpv.Prefix(LinkLine(listOf(lib.path), listOf("libmpv.a"), emptyList()))
        val linux = KonanCross("/k/clang++", "/k", null, "x86_64-unknown-linux-gnu", "/k/sysroot", emptyList(), null)
        for ((platform, cross) in listOf(DesktopPlatform.LinuxX64 to linux, DesktopPlatform.WindowsX64 to KonanCross.llvmMingw(File("/m")))) {
            val command = desktopLinkCommand(platform, mpv, File("/jni"), File("/mpv"), File("/jdk"), File("/md"), cross, File("/out/lib"))
            // The archives carry debug sections, which made the Windows DLL 134 MB.
            assertTrue("-Wl,--strip-debug" in command, "$platform: $command")
        }
    }
}
