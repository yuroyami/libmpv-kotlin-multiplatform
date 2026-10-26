package io.github.yuroyami.libmpv.buildtools

import java.io.File

/**
 * A clang and sysroot for a Linux or Windows link. Linux uses Kotlin/Native's, found inside `~/.konan` and adapted
 * from KiteFFmpeg's `KonanCrossTools.kt`. Windows uses llvm-mingw ([llvmMingw]), which built the Windows tree.
 */
data class KonanCross(
    val clangxx: String,
    /** Where clang finds `ld.lld`. Apple's `ld` links neither ELF nor PE. */
    val toolchainBin: String,
    /** The gcc runtime (crtbegin, libgcc), which sits beside the sysroot, not inside it. */
    val runtimeDir: String?,
    val triple: String,
    val sysroot: String,
    /** libstdc++'s headers: the sysroots are C sysroots, so `<string>` is not on the default path. */
    val cxxIncludeDirs: List<String>,
    val cxxLibDir: String?,
) {
    /** `-target`, `--sysroot`, the C++ headers, lld and the runtime folders. */
    fun args(): List<String> = buildList {
        add("-target"); add(triple)
        add("--sysroot=$sysroot")
        cxxIncludeDirs.forEach { add("-isystem"); add(it) }
        add("-fuse-ld=lld")
        add("-B$toolchainBin")
        runtimeDir?.let { add("-B$it"); add("-L$it") }
        cxxLibDir?.let { add("-L$it") }
    }

    companion object {
        private val SYSROOTS = mapOf(
            "linux_x64" to ("x86_64-unknown-linux-gnu" to "x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot"),
            "linux_arm64" to ("aarch64-unknown-linux-gnu" to "aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot"),
        )

        /** llvm-mingw unpacked at [root] (`buildscripts/deps/llvm-mingw`). Its clang carries its own C++ headers. */
        fun llvmMingw(root: File): KonanCross = KonanCross(
            clangxx = root.resolve("bin/x86_64-w64-mingw32-clang++").path,
            toolchainBin = root.resolve("bin").path,
            runtimeDir = null,
            triple = "x86_64-w64-mingw32",
            sysroot = root.path,
            cxxIncludeDirs = emptyList(),
            cxxLibDir = null,
        )

        /** Where Kotlin/Native keeps its distributions and dependencies. */
        fun konanRoot(): File = System.getenv("KONAN_DATA_DIR")?.let(::File) ?: File(System.getProperty("user.home"), ".konan")

        fun resolve(konanTarget: String): KonanCross {
            val (triple, sysrootRelative) = SYSROOTS[konanTarget] ?: error("No cross toolchain is known for $konanTarget")
            val dependencies = konanRoot().resolve("dependencies")
            val llvmBin = dependencies.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("llvm-") && it.resolve("bin/clang++").canExecute() }
                .maxByOrNull { it.name }?.resolve("bin")
                ?: error("No Kotlin/Native LLVM with clang++ under $dependencies. A Kotlin/Native build downloads it.")
            val sysroot = dependencies.resolve(sysrootRelative)
            check(sysroot.isDirectory) {
                "No sysroot at $sysroot. Kotlin/Native downloads it the first time it links a program for $konanTarget."
            }
            val packageRoot = dependencies.resolve(sysrootRelative.substringBefore('/'))
            // The gcc version folder is discovered, never written down: a Kotlin/Native update moves it.
            val runtimeDir = packageRoot.resolve("lib/gcc").listFiles().orEmpty()
                .filter { it.isDirectory }
                .flatMap { it.listFiles().orEmpty().filter(File::isDirectory) }
                .firstOrNull { it.resolve("libgcc.a").isFile }
            val cxxConfig = packageRoot.walkTopDown().maxDepth(8).firstOrNull { it.name == "c++config.h" && it.isFile }
            val cxxIncludeDirs = cxxConfig?.let { config ->
                val machineDir = config.parentFile.parentFile
                val versionDir = machineDir.parentFile
                listOfNotNull(versionDir.path, machineDir.path, versionDir.resolve("backward").takeIf(File::isDirectory)?.path)
            }.orEmpty()
            val cxxLibDir = sequenceOf(packageRoot.resolve("$triple/lib64"), packageRoot.resolve("$triple/lib"), runtimeDir)
                .filterNotNull().firstOrNull { it.resolve("libstdc++.a").isFile }
            return KonanCross(
                clangxx = llvmBin.resolve("clang++").path,
                toolchainBin = llvmBin.path,
                runtimeDir = runtimeDir?.path,
                triple = triple,
                sysroot = sysroot.path,
                cxxIncludeDirs = cxxIncludeDirs,
                cxxLibDir = cxxLibDir?.path,
            )
        }
    }
}
