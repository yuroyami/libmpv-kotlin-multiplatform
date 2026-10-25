package io.github.yuroyami.libmpv.buildtools

import java.io.File

/** Where a Kotlin/Native target takes mpv from. */
sealed interface InteropSource {
    /** A static tree at `buildscripts/prefix/<platform>/`. cinterop embeds its archives in the klib. */
    data class Prefix(val line: LinkLine) : InteropSource

    /** Homebrew's shared mpv, for local work on macOS. A release refuses it. */
    data class Dev(val homebrewPrefix: String) : InteropSource

    /** No mpv at all: the klib builds, and nothing that uses it links. */
    data object HeadersOnly : InteropSource
}

const val INTEROP_SOURCE_MARKER: String = "# libmpv-kmp-source:"

/** The cinterop def of mpv's client API, read from the vendored headers in [headersDir]. */
fun renderInteropDef(headersDir: File, source: InteropSource): String = buildString {
    appendLine("package = io.github.yuroyami.libmpv.cinterop")
    appendLine("headers = mpv/client.h")
    appendLine("headerFilter = mpv/client.h")
    appendLine("compilerOpts = -I${headersDir.absolutePath}")
    // Plain constants, so a format or an event id is an Int-like value, as on the JNI side.
    appendLine("nonStrictEnums = mpv_error mpv_format mpv_event_id mpv_log_level mpv_end_file_reason")
    when (source) {
        is InteropSource.Prefix -> {
            appendLine("$INTEROP_SOURCE_MARKER prefix")
            appendLine("staticLibraries = ${source.line.archives.joinToString(" ")}")
            appendLine("libraryPaths = ${source.line.libraryPaths.joinToString(" ")}")
            // Kotlin/Native passes linkerOpts to ld itself, which rejects the driver flag -pthread. On Apple,
            // pthreads are part of libSystem anyway.
            appendLine("linkerOpts = ${source.line.systemFlags.filter { it != "-pthread" }.joinToString(" ")}")
        }
        is InteropSource.Dev -> {
            appendLine("$INTEROP_SOURCE_MARKER dev")
            appendLine("linkerOpts = -L${source.homebrewPrefix}/lib -lmpv")
        }
        InteropSource.HeadersOnly -> appendLine("$INTEROP_SOURCE_MARKER headers-only")
    }
}

/** The mpv a Kotlin/Native target binds: the static tree, Homebrew's mpv on macOS, or headers only. */
fun interopSource(konanTarget: String, prefixTree: File, homebrewPrefix: String): InteropSource = when {
    prefixTree.resolve("lib/pkgconfig/mpv.pc").isFile -> InteropSource.Prefix(splitLinkLine(pkgConfigLibs(prefixTree, static = true)))
    konanTarget == "macos_arm64" && File(homebrewPrefix, "lib/pkgconfig/mpv.pc").isFile -> InteropSource.Dev(homebrewPrefix)
    else -> InteropSource.HeadersOnly
}

/** The def files that do not bind a static tree. A release needs this list empty. */
fun nonReleaseInteropDefs(defFiles: List<File>): List<File> =
    defFiles.filterNot { it.isFile && it.readLines().any { line -> line.trim() == "$INTEROP_SOURCE_MARKER prefix" } }
