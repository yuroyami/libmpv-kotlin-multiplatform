package io.github.yuroyami.libmpv.buildtools

import java.io.File

/** A desktop JVM platform: its folder name in the jar, its Kotlin/Native target and its library file. */
enum class DesktopPlatform(val directory: String, val konanTarget: String, val libraryFile: String) {
    MacosArm64("macos-arm64", "macos_arm64", "libmpv_kmp_jni.dylib"),
    LinuxX64("linux-x64", "linux_x64", "libmpv_kmp_jni.so"),
    LinuxArm64("linux-arm64", "linux_arm64", "libmpv_kmp_jni.so"),
    WindowsX64("windows-x64", "mingw_x64", "mpv_kmp_jni.dll");

    val isMac: Boolean get() = this == MacosArm64
    val isWindows: Boolean get() = this == WindowsX64

    companion object {
        fun of(directory: String): DesktopPlatform =
            entries.firstOrNull { it.directory == directory } ?: error("No desktop platform named $directory")
    }
}

/** Where a desktop library takes mpv from. */
sealed interface DesktopMpv {
    /** A static tree at `buildscripts/prefix/<platform>/`. The only source a release accepts. */
    data class Prefix(val line: LinkLine) : DesktopMpv

    /** Homebrew's shared mpv, for local work on macOS. A release refuses it. */
    data class Dev(val homebrewPrefix: String) : DesktopMpv
}

/** The jar folder of the desktop libraries, as `JniLibrary` reads it. */
const val DESKTOP_RESOURCE_ROOT: String = "libmpv-kmp-native"
const val MANIFEST_NAME: String = "manifest.txt"
const val DEV_MARKER: String = "# dev"

/** The four C++ files that Android compiles too. `mpv_kmp_android.cpp` is Android only. */
val DESKTOP_JNI_SOURCES: List<String> = listOf("mpv_kmp.cpp", "node_codec.cpp", "stream_cb.cpp", "utf8.cpp")

/**
 * The mpv that [platform] links: the static tree when it has `lib/pkgconfig/mpv.pc`, Homebrew's mpv on
 * macOS when only that exists, or null when there is neither.
 */
fun desktopMpv(platform: DesktopPlatform, prefixTree: File, homebrewPrefix: String): DesktopMpv? = when {
    prefixTree.resolve("lib/pkgconfig/mpv.pc").isFile -> DesktopMpv.Prefix(splitLinkLine(pkgConfigLibs(prefixTree, static = true)))
    platform.isMac && File(homebrewPrefix, "lib/pkgconfig/mpv.pc").isFile -> DesktopMpv.Dev(homebrewPrefix)
    else -> null
}

/**
 * The compile and link command of [platform]'s JNI library, one clang++ call. [crossJniMd] is the folder
 * that holds the written `jni_md.h` of a Linux or Windows link, and [cross] its toolchain.
 */
fun desktopLinkCommand(
    platform: DesktopPlatform,
    mpv: DesktopMpv,
    jniSources: File,
    mpvHeaders: File,
    jdkInclude: File,
    crossJniMd: File?,
    cross: KonanCross?,
    output: File,
): List<String> = buildList {
    val line = (mpv as? DesktopMpv.Prefix)?.line
    val archives = line?.archives.orEmpty().map { archive ->
        line!!.libraryPaths.map { File(it, archive) }.first { it.isFile }.path
    }
    if (platform.isMac) {
        addAll(listOf("xcrun", "clang++", "-target", "arm64-apple-macos12"))
    } else {
        add(cross!!.clangxx)
        addAll(cross.args())
    }
    addAll(listOf("-std=c++17", "-O2", "-fvisibility=hidden", "-fvisibility-inlines-hidden", "-shared"))
    if (!platform.isWindows) add("-fPIC")
    add("-I${jniSources.path}")
    add("-I${mpvHeaders.path}")
    crossJniMd?.let { add("-I${it.path}") }
    add("-I${jdkInclude.path}")
    if (platform.isMac) add("-I${jdkInclude.resolve("darwin").path}")
    DESKTOP_JNI_SOURCES.forEach { add(jniSources.resolve(it).path) }
    when {
        platform.isMac -> {
            when (mpv) {
                is DesktopMpv.Prefix -> { addAll(archives); addAll(line!!.systemFlags) }
                is DesktopMpv.Dev -> addAll(listOf("-L${mpv.homebrewPrefix}/lib", "-lmpv"))
            }
            add("-lc++")
            add("-Wl,-exported_symbols_list,${jniSources.resolve("exports.macos").path}")
            add("-Wl,-dead_strip")
            add("-Wl,-install_name,@rpath/${platform.libraryFile}")
        }
        platform.isWindows -> {
            // -Bstatic takes zlib and winpthreads from their archives. The MinGW sysroot also has DLL import
            // libraries for them, and a DLL that imports those does not load without MinGW.
            add("-Wl,-Bstatic")
            addAll(archives)
            addAll(line!!.systemFlags)
            // The JNI sources use pthread keys. mpv uses win32 threads and no longer brings winpthreads.
            add("-lpthread")
            // libc++ and libunwind of llvm-mingw go into the DLL, so it imports system DLLs only.
            add("-static")
        }
        else -> {
            addAll(listOf("-static-libstdc++", "-static-libgcc"))
            addAll(archives)
            addAll(line!!.systemFlags)
            add("-Wl,--version-script=${jniSources.resolve("exports.map").path}")
            // A missing archive fails this link, not the user's app.
            add("-Wl,--no-undefined")
        }
    }
    // The archives carry debug sections. Without this the Linux library is 93 MB and the Windows DLL 134 MB.
    if (!platform.isMac) add("-Wl,--strip-debug")
    addAll(listOf("-o", output.path))
}

/** The problems of the desktop libraries in [resourcesDirs]: a missing platform, a dev link, or wrong exports. */
fun desktopJniProblems(platforms: List<String>, resourcesDirs: List<File>): List<String> = platforms.flatMap { name ->
    val platform = DesktopPlatform.of(name)
    val folder = resourcesDirs.map { it.resolve("$DESKTOP_RESOURCE_ROOT/$name") }.firstOrNull { it.resolve(MANIFEST_NAME).isFile }
        ?: return@flatMap listOf("$name: no library. It needs a static mpv tree at buildscripts/prefix/$name.")
    val manifest = folder.resolve(MANIFEST_NAME).readLines()
    if (manifest.firstOrNull()?.trim() == DEV_MARKER) return@flatMap listOf("$name: a dev link against Homebrew's mpv, which a release refuses.")
    val library = folder.resolve(platform.libraryFile)
    if (!library.isFile) return@flatMap listOf("$name: the manifest has no ${platform.libraryFile}.")
    val symbols = listExportedSymbols(library)
    buildList {
        if (symbols.none { it.removePrefix("_") == "${JNI_SYMBOL_PREFIX}create" }) add("$name: ${platform.libraryFile} does not export ${JNI_SYMBOL_PREFIX}create.")
        // Windows binds every import to one named DLL, so mpv's own dllexport functions are harmless there.
        if (!platform.isWindows) unexpectedExports(symbols).takeIf { it.isNotEmpty() }?.let {
            add("$name: ${platform.libraryFile} exports ${it.size} symbols beside the JNI functions, the first ${it.take(5)}.")
        }
    }
}

/** The vendored headers that differ from the mpv checkout in [checkout], or that it lacks. Empty when they match. */
fun mpvHeaderDifferences(vendored: File, checkout: File): List<String> = listOf("client.h", "stream_cb.h").filter { name ->
    val ours = vendored.resolve("mpv/$name")
    val theirs = checkout.resolve(name)
    !ours.isFile || !theirs.isFile || !ours.readBytes().contentEquals(theirs.readBytes())
}
