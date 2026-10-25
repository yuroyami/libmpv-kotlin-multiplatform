package io.github.yuroyami.libmpv.buildtools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossToolchainTest {
    private fun cross(triple: String) = KonanCross(
        clangxx = "/k/llvm/bin/clang++",
        toolchainBin = "/k/llvm/bin",
        runtimeDir = "/k/gcc",
        triple = triple,
        sysroot = "/k/sysroot",
        cxxIncludeDirs = listOf("/k/include/c++/8.3.0"),
        cxxLibDir = "/k/lib64",
    )

    private fun line(env: String, name: String): String =
        env.lines().single { it.startsWith("export $name=") }

    @Test
    fun linuxEnvAimsKonanClangAtTheSysroot() {
        val env = crossEnv(DesktopPlatform.LinuxX64, cross("x86_64-unknown-linux-gnu"))
        assertTrue("export CROSS_CC='/k/llvm/bin/clang'" in env.lines())
        assertTrue("export CROSS_CXX='/k/llvm/bin/clang++'" in env.lines())
        assertTrue("export CROSS_AR='/k/llvm/bin/llvm-ar'" in env.lines())
        assertTrue("export CROSS_RANLIB='/k/llvm/bin/llvm-ar s'" in env.lines())
        assertTrue("export CROSS_TRIPLE='x86_64-unknown-linux-gnu'" in env.lines())
        assertTrue("-target x86_64-unknown-linux-gnu --sysroot=/k/sysroot" in line(env, "CROSS_CFLAGS"))
        assertTrue("-fuse-ld=lld -B/k/llvm/bin -B/k/gcc -L/k/gcc" in line(env, "CROSS_LDFLAGS"))
        assertTrue("-isystem /k/include/c++/8.3.0" in line(env, "CROSS_CXXFLAGS"))
    }

    @Test
    fun windowsEnvAddsTheGnuDialect() {
        val env = crossEnv(DesktopPlatform.WindowsX64, cross("x86_64-pc-windows-gnu"))
        assertTrue(line(env, "CROSS_CFLAGS").endsWith("-std=gnu11'"))
        // clang++ refuses every program when it is given a C dialect.
        assertFalse("gnu11" in line(env, "CROSS_CXXFLAGS"))
    }
}
