package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import io.github.yuroyami.libmpv.buildtools.TREE_MANIFEST_NAME
import io.github.yuroyami.libmpv.buildtools.treeProblems
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.TaskAction
import java.io.File

/** Fails unless every platform has an unpacked tree whose files match its manifest and hold no shared library. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkTrees(platforms: List<String>, prefixRoot: String) {
    val problems = platforms.flatMap { platform ->
        val tree = File(prefixRoot).resolve(platform)
        val manifest = tree.resolve(TREE_MANIFEST_NAME)
        if (!manifest.isFile) listOf("no $TREE_MANIFEST_NAME at $tree. fetch-natives.sh unpacks the release trees there.")
        else treeProblems(tree, manifest.readLines()).map { "$platform: $it" }
    }
    check(problems.isEmpty()) { "The static mpv trees are not ready for a release:\n" + problems.joinToString("\n") { "- $it" } }
    println("[libmpv-kotlin-multiplatform] trees complete for ${platforms.joinToString()}")
}
