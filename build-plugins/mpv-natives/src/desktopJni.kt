package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import io.github.yuroyami.libmpv.buildtools.DESKTOP_RESOURCE_ROOT
import io.github.yuroyami.libmpv.buildtools.DEV_MARKER
import io.github.yuroyami.libmpv.buildtools.DesktopMpv
import io.github.yuroyami.libmpv.buildtools.DesktopPlatform
import io.github.yuroyami.libmpv.buildtools.KonanCross
import io.github.yuroyami.libmpv.buildtools.MANIFEST_NAME
import io.github.yuroyami.libmpv.buildtools.desktopJniProblems
import io.github.yuroyami.libmpv.buildtools.desktopLinkCommand
import io.github.yuroyami.libmpv.buildtools.desktopMpv
import io.github.yuroyami.libmpv.buildtools.jdkIncludeDir
import io.github.yuroyami.libmpv.buildtools.jniPlatformHeader
import io.github.yuroyami.libmpv.buildtools.mpvHeaderDifferences
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

private fun log(message: String) = println("[libmpv-kotlin-multiplatform] $message")

/**
 * Links `mpv_kmp_jni` for [platform] into [resourcesDir], with a manifest that `JniLibrary` reads. A platform
 * without a static tree, and without dev mode, links nothing: the jar then carries no library for it.
 */
@TaskAction
fun linkDesktopJni(
    platform: String,
    homebrewPrefix: String,
    kotlinVersion: String,
    @Input jniSources: Path,
    @Input mpvHeaders: Path,
    // The tree comes from the native build, not from another task.
    @Input(inferTaskDependency = false) prefixTree: Path,
    @Output workDir: Path,
    @Output resourcesDir: Path,
) {
    val target = DesktopPlatform.of(platform)
    val resources = resourcesDir.toFile().apply { deleteRecursively(); mkdirs() }
    val hostOs = System.getProperty("os.name").orEmpty()
    val hostIsArmMac = hostOs.startsWith("Mac") && System.getProperty("os.arch") in setOf("aarch64", "arm64")
    val mpv = desktopMpv(target, prefixTree.toFile(), homebrewPrefix)
    val skip = when {
        mpv == null -> "no prefix tree at buildscripts/prefix/$platform"
        target.isMac && !hostIsArmMac -> "the macOS library links on an arm64 Mac only"
        !target.isMac && hostOs.startsWith("Windows") -> "the Linux and Windows links run on macOS and Linux hosts"
        else -> null
    }
    if (skip != null) {
        log("$skip: the jar carries no $platform library")
        return
    }
    if (mpv is DesktopMpv.Dev) log("$platform: dev link against ${mpv.homebrewPrefix}")
    val work = workDir.toFile().apply { deleteRecursively(); mkdirs() }
    // The Windows DLL links with the llvm-mingw that built its tree, so the C++ runtimes agree.
    val cross = when {
        target.isMac -> null
        target.isWindows -> KonanCross.llvmMingw(prefixTree.toFile().resolve("../../deps/llvm-mingw").normalize())
        else -> KonanCross.resolve(target.konanTarget)
    }
    val jniMd = if (target.isMac) null else work.resolve("jni-platform").apply {
        mkdirs()
        resolve("jni_md.h").writeText(jniPlatformHeader(target.isWindows))
    }
    val folder = resources.resolve("$DESKTOP_RESOURCE_ROOT/$platform").apply { mkdirs() }
    val library = folder.resolve(target.libraryFile)
    val command = desktopLinkCommand(target, mpv!!, jniSources.toFile(), mpvHeaders.toFile(), jdkIncludeDir(), jniMd, cross, library)
    val process = ProcessBuilder(command).directory(work).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "The $platform JNI link failed (Kotlin $kotlinVersion toolchain):\n${command.joinToString(" ")}\n$output" }
    // JniLibrary extracts every file the manifest names, the JNI library last. Lines with # are comments.
    val lines = listOfNotNull(DEV_MARKER.takeIf { mpv is DesktopMpv.Dev }, target.libraryFile)
    folder.resolve(MANIFEST_NAME).writeText(lines.joinToString("\n", postfix = "\n"))
    log("$platform: linked ${library.length() / 1024} KiB")
}

/** Fails when a platform has no library, has a dev link, or exports more than the JNI functions. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkDesktopJni(platforms: List<String>, @Input resourcesDirs: List<Path>) {
    val problems = desktopJniProblems(platforms, resourcesDirs.map { it.toFile() })
    check(problems.isEmpty()) { "The desktop JNI libraries are not ready for a release:\n" + problems.joinToString("\n") { "- $it" } }
    log("desktop JNI libraries complete for ${platforms.joinToString()}")
}

/** Fails when the vendored mpv headers differ from the mpv checkout. Passes without a checkout. */
@TaskAction(ExecutionAvoidance.Disabled)
fun checkMpvHeaders(@Input vendored: Path, @Input(inferTaskDependency = false) checkout: Path) {
    if (!checkout.toFile().isDirectory) {
        log("no mpv checkout at $checkout, so the vendored headers are not compared")
        return
    }
    val different = mpvHeaderDifferences(vendored.toFile(), checkout.toFile())
    check(different.isEmpty()) { "These vendored mpv headers differ from $checkout: ${different.joinToString()}. Copy them again." }
    log("the vendored mpv headers match $checkout")
}
