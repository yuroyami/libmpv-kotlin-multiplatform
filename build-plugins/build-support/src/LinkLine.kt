package io.github.yuroyami.libmpv.buildtools

import java.io.File

/** A `pkg-config --libs` line, sorted for a link: library folders, static archives, then everything else. */
data class LinkLine(val libraryPaths: List<String>, val archives: List<String>, val systemFlags: List<String>)

/**
 * `-L<dir>` becomes a library path. `-l<name>` becomes an archive when `lib<name>.a` is in one of those
 * paths. Everything else (`-lz`, `-framework X`, `-pthread`) is a system flag. The order stays.
 */
fun splitLinkLine(flags: List<String>): LinkLine {
    val paths = flags.filter { it.startsWith("-L") }.map { it.removePrefix("-L") }
    val archives = mutableListOf<String>()
    val system = mutableListOf<String>()
    for (flag in flags) {
        if (flag.startsWith("-L")) continue
        val archive = flag.takeIf { it.startsWith("-l") }?.let { "lib${it.removePrefix("-l")}.a" }
        if (archive != null && paths.any { File(it, archive).isFile }) archives += archive else system += flag
    }
    return LinkLine(paths, archives, system)
}

/**
 * Runs `pkg-config --libs mpv` against [tree], with `--static` when [static] is true. The variables are the
 * ones `buildscripts/include/path.sh` sets for Android, so the `.pc` files of a prefix tree resolve inside it.
 */
fun pkgConfigLibs(tree: File, static: Boolean): List<String> {
    val command = listOfNotNull("pkg-config", "--static".takeIf { static }, "--libs", "mpv")
    val process = ProcessBuilder(command).apply {
        environment()["PKG_CONFIG_SYSROOT_DIR"] = tree.absolutePath
        environment()["PKG_CONFIG_LIBDIR"] = tree.resolve("lib/pkgconfig").absolutePath
        redirectErrorStream(true)
    }.start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "pkg-config failed for $tree: $output" }
    return output.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
