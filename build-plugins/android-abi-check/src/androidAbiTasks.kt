package io.github.yuroyami.libmpvkt.buildplugins.androidabi

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import org.jetbrains.kotlin.abi.tools.AbiFilters
import org.jetbrains.kotlin.abi.tools.AbiTools
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

private val tools: AbiTools get() = AbiTools.getInstance(AbiTools::class.java.classLoader)

/**
 * Writes the ABI dump of the module's Android classes, in the format of the Kotlin Gradle plugin.
 * No plugin reference gives the Android classes, so run `./kotlin build -p android` first.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun dumpAndroidAbi(
    moduleName: String,
    // A String, not an @Input Path: the build folder holds this task's own output.
    buildDir: String,
    @Output dumpFile: Path,
) {
    val classes = listOf("debug", "release")
        .map { Path(buildDir, "artifacts", "CompiledJvmArtifact", "${moduleName}android$it", "kotlin-output") }
        .firstOrNull { it.exists() }
        ?: error("No Android classes of $moduleName. Run `./kotlin build -p android -m $moduleName` first.")
    val classFiles = classes.walk()
        .filter { it.isRegularFile() && it.extension == "class" }
        .map { it.toFile() }
        .sortedBy { it.path }
        .toList()
    val dump = StringBuilder()
    tools.printJvmDump(dump, classFiles, AbiFilters.EMPTY)
    dumpFile.createParentDirectories().writeText(dump)
}

/** Fails when the built Android dump differs from the committed one. Line endings are ignored. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkAndroidAbi(
    moduleName: String,
    @Input builtDump: Path,
    // The committed dump is a baseline: reading it must not make `update` run first.
    @Input(inferTaskDependency = false) committedDump: Path,
) {
    check(committedDump.exists()) {
        "No ABI dump at $committedDump. Run `./kotlin do updateAndroidAbi -m $moduleName` to create it."
    }
    if (committedDump.readText().lines() == builtDump.readText().lines()) return
    val diff = tools.filesDiff(committedDump.toFile(), builtDump.toFile())
    error(
        "The public API of $moduleName changed.\n$diff\n\n" +
            "If the change is intended, run `./kotlin do updateAndroidAbi -m $moduleName` and commit the dump."
    )
}

/** Overwrites the committed dump with the built one. */
@TaskAction
fun updateAndroidAbi(
    @Input builtDump: Path,
    @Output committedDump: Path,
) {
    builtDump.copyTo(committedDump.createParentDirectories(), overwrite = true)
}
