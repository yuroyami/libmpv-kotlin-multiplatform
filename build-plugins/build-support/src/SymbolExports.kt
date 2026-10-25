package io.github.yuroyami.libmpv.buildtools

import java.io.File

const val JNI_SYMBOL_PREFIX: String = "Java_io_github_yuroyami_libmpv_jni_MpvNative_"

/**
 * The symbols a Linux or macOS JNI library must not export: anything but `JNI_OnLoad` and the
 * `MpvNative` JNI functions. An exported FFmpeg symbol could bind to a second FFmpeg in the same process.
 */
fun unexpectedExports(symbols: List<String>): List<String> = symbols
    .map { it.removePrefix("_") }
    .filterNot { it == "JNI_OnLoad" || it.startsWith(JNI_SYMBOL_PREFIX) }

/**
 * The defined, exported symbols of [library]. Mach-O and ELF go through Xcode's `llvm-nm`, a PE DLL
 * through the export table of Xcode's `llvm-objdump -p`. Kotlin/Native's LLVM ships neither tool.
 */
fun listExportedSymbols(library: File): List<String> = when (library.extension) {
    "dylib" -> run("xcrun", "llvm-nm", "-gU", library.path).lines().mapNotNull { it.trim().split(' ').lastOrNull()?.takeIf(String::isNotEmpty) }
    "so" -> run("xcrun", "llvm-nm", "-D", "--defined-only", library.path).lines().mapNotNull { it.trim().split(' ').lastOrNull()?.takeIf(String::isNotEmpty) }
    "dll" -> peExports(run("xcrun", "llvm-objdump", "-p", library.path))
    else -> error("Not a desktop library: $library")
}

/** The names in the `Export Table` section of `llvm-objdump -p`. Each row ends with the symbol name. */
internal fun peExports(objdump: String): List<String> {
    val lines = objdump.lines()
    val start = lines.indexOfFirst { it.trim() == "Export Table:" }
    if (start < 0) return emptyList()
    // The table ends at the next section, a line that starts at the left margin.
    return lines.drop(start + 1)
        .takeWhile { it.isEmpty() || it.first().isWhitespace() }
        .map { it.trim() }
        .filter { Regex("^\\d+\\s+0x[0-9a-fA-F]+\\s+\\S+$").matches(it) }
        .map { it.split(Regex("\\s+")).last() }
}

private fun run(vararg command: String): String {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "${command.joinToString(" ")} failed: $output" }
    return output
}
