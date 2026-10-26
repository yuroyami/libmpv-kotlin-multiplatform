package io.github.yuroyami.libmpv.buildtools

import kotlin.test.Test
import kotlin.test.assertEquals
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
        val env = crossEnv(cross("x86_64-unknown-linux-gnu"))
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
    fun llvmMingwLinksWithItsOwnClangAndSysroot() {
        val cross = KonanCross.llvmMingw(java.io.File("/m"))
        assertEquals("/m/bin/x86_64-w64-mingw32-clang++", cross.clangxx)
        assertEquals(listOf("-target", "x86_64-w64-mingw32", "--sysroot=/m", "-fuse-ld=lld", "-B/m/bin"), cross.args())
    }
}
