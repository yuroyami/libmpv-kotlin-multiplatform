package io.github.yuroyami.libmpv.buildplugins.androidnatives

import io.github.yuroyami.libmpv.buildtools.AndroidNativeChecks
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

private fun log(message: String) = println("[libmpv-kotlin-multiplatform] $message")

/** Copies `<abi>/<file>` into `lib/<abi>/<file>` for every ABI that has all [files]. Warns once per incomplete ABI. */
@TaskAction
fun packageAndroidNatives(
    // The libraries come from buildscripts/ or a release, not from another task.
    @Input(inferTaskDependency = false) nativeLibsDir: Path,
    files: List<String>,
    @Output resourcesDir: Path,
) {
    val warnings = AndroidNativeChecks.packageNatives(nativeLibsDir.toFile(), files, resourcesDir.toFile())
    warnings.forEach { log("WARNING: $it") }
}

/** Fails unless every ABI in LIBMPV_KMP_ABIS passes [AndroidNativeChecks.findProblems]. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkAndroidNatives(
    @Input(inferTaskDependency = false) nativeLibsDir: Path,
    files: List<String>,
    minApi: Int,
    probeSymbol: String?,
    libcxxMarker: String?,
) {
    val abis = AndroidNativeChecks.requiredAbis(System.getenv("LIBMPV_KMP_ABIS"))
    val problems = AndroidNativeChecks.findProblems(nativeLibsDir.toFile(), abis, files, minApi, probeSymbol, libcxxMarker)
    check(problems.isEmpty()) { AndroidNativeChecks.failureMessage(problems) }
    log("${files.size} native files per ABI complete in $nativeLibsDir for ${abis.joinToString()}")
}
