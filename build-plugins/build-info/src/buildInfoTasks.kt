package io.github.yuroyami.libmpv.buildplugins.buildinfo

import io.github.yuroyami.libmpv.buildtools.renderBuildInfo
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Fails when the committed BuildInfo.kt differs from what depinfo.sh and the publishing version give. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkBuildInfo(
    moduleName: String,
    version: String?,
    minSdk: Int,
    @Input depinfo: Path,
    // The committed file is a baseline: reading it must not make `update` run first.
    @Input(inferTaskDependency = false) sourceFile: Path,
) {
    val expected = render(moduleName, version, minSdk, depinfo)
    check(sourceFile.exists()) { "No $sourceFile. Run `./kotlin do updateBuildInfo -m $moduleName` to write it." }
    check(sourceFile.readText().lines() == expected.lines()) {
        "$sourceFile does not match buildscripts/include/depinfo.sh and version $version. " +
            "Run `./kotlin do updateBuildInfo -m $moduleName` and commit the file."
    }
}

/** Rewrites the committed BuildInfo.kt. */
@TaskAction(ExecutionAvoidance.Disabled)
fun updateBuildInfo(
    moduleName: String,
    version: String?,
    minSdk: Int,
    @Input depinfo: Path,
    @Output sourceFile: Path,
) {
    val text = render(moduleName, version, minSdk, depinfo)
    if (!sourceFile.exists() || sourceFile.readText() != text) sourceFile.createParentDirectories().writeText(text)
}

private fun render(moduleName: String, version: String?, minSdk: Int, depinfo: Path): String {
    val v = checkNotNull(version) { "Set settings.publishing.version in the module.yaml of $moduleName." }
    return renderBuildInfo(depinfo.readText(), v, minSdk)
}
