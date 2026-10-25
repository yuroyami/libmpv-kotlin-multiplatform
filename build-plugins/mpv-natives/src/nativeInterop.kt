package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import io.github.yuroyami.libmpv.buildtools.InteropSource
import io.github.yuroyami.libmpv.buildtools.interopSource
import io.github.yuroyami.libmpv.buildtools.nonReleaseInteropDefs
import io.github.yuroyami.libmpv.buildtools.renderInteropDef
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/** Writes the cinterop def of [konanTarget]: a static tree, Homebrew's mpv on macOS (dev mode), or headers only. */
@TaskAction
fun writeMpvInterop(
    konanTarget: String,
    homebrewPrefix: String,
    @Input mpvHeaders: Path,
    @Input(inferTaskDependency = false) prefixTree: Path,
    @Output defFile: Path,
) {
    val source = interopSource(konanTarget, prefixTree.toFile(), homebrewPrefix)
    val kind = when (source) {
        is InteropSource.Prefix -> "the static tree at $prefixTree"
        is InteropSource.Dev -> "dev mode, Homebrew's mpv in ${source.homebrewPrefix}"
        InteropSource.HeadersOnly -> "headers-only: no prefix tree at $prefixTree, so nothing that uses the klib links"
    }
    println("[libmpv-kotlin-multiplatform] $konanTarget binds mpv from $kind")
    defFile.createParentDirectories().writeText(renderInteropDef(mpvHeaders.toFile(), source))
}

/** Fails unless every def binds a static tree. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkNativeInterop(@Input defFiles: List<Path>) {
    val wrong = nonReleaseInteropDefs(defFiles.map { it.toFile() })
    check(wrong.isEmpty()) {
        "These Kotlin/Native targets bind no static mpv tree, which a release needs:\n" + wrong.joinToString("\n") { "- $it" }
    }
    println("[libmpv-kotlin-multiplatform] every Kotlin/Native target binds a static mpv tree")
}
