package io.github.yuroyami.libmpv.buildtools

/**
 * The `toolchain.env` of a Linux tree: one `export NAME='value'` line per tool and flag set,
 * which `buildscripts/include/platforms.sh` sources. The flag split follows KiteFFmpeg's `KonanCrossTools.kt`.
 */
fun crossEnv(cross: KonanCross): String {
    val bin = cross.toolchainBin
    val aim = listOf("-target", cross.triple, "--sysroot=${cross.sysroot}")
    val cxxflags = aim + cross.cxxIncludeDirs.flatMap { listOf("-isystem", it) }
    // meson and configure link a probe program even for a static archive, so the link flags are needed.
    val ldflags = listOfNotNull("-fuse-ld=lld", "-B$bin", cross.runtimeDir?.let { "-B$it" }, cross.runtimeDir?.let { "-L$it" }, cross.cxxLibDir?.let { "-L$it" })
    // Kotlin/Native's LLVM has only clang, lld and llvm-ar. Xcode's nm is llvm-nm and reads ELF and PE.
    val values = linkedMapOf(
        "CROSS_CC" to cross.clangxx.removeSuffix("++"),
        "CROSS_CXX" to cross.clangxx,
        "CROSS_AR" to "$bin/llvm-ar",
        "CROSS_NM" to "/usr/bin/nm",
        "CROSS_RANLIB" to "$bin/llvm-ar s",
        "CROSS_TRIPLE" to cross.triple,
        "CROSS_SYSROOT" to cross.sysroot,
        "CROSS_CFLAGS" to aim.joinToString(" "),
        "CROSS_CXXFLAGS" to cxxflags.joinToString(" "),
        "CROSS_LDFLAGS" to ldflags.joinToString(" "),
    )
    return values.entries.joinToString("\n", postfix = "\n") { (name, value) -> "export $name='$value'" }
}
