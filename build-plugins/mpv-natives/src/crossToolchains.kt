package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import io.github.yuroyami.libmpv.buildtools.DesktopPlatform
import io.github.yuroyami.libmpv.buildtools.KonanCross
import io.github.yuroyami.libmpv.buildtools.KonanProperties
import io.github.yuroyami.libmpv.buildtools.crossEnv
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.TaskAction
import java.io.File

/**
 * Writes `<prefixRoot>/<platform>/toolchain.env` for each Linux platform, which the build scripts
 * source. The Kotlin/Native distribution, its LLVM and the sysroots are fetched first when they are missing,
 * so a fresh CI runner needs no Kotlin/Native build before this task.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun writeCrossToolchains(platforms: List<String>, kotlinVersion: String, prefixRoot: String) {
    val konanRoot = KonanCross.konanRoot().apply { mkdirs() }
    if (konanRoot.listFiles().orEmpty().none { it.name == "kotlin-native-prebuilt-macos-aarch64-$kotlinVersion" }) {
        download(KonanProperties.distributionUrl(kotlinVersion, "macos-aarch64"), konanRoot)
    }
    val properties = KonanProperties.of(konanRoot, kotlinVersion)
    val dependencies = konanRoot.resolve("dependencies").apply { mkdirs() }
    // Kotlin/Native lists what it has unpacked in .extracted and downloads again whatever is not there.
    val extracted = dependencies.resolve(".extracted")
    val packages = listOf(properties.llvm("macos_arm64")) +
        platforms.flatMap { properties.crossDependencies("macos_arm64", DesktopPlatform.of(it).konanTarget) }
    packages.distinct().filterNot { dependencies.resolve(it).isDirectory }.forEach {
        download(properties.downloadUrl(it), dependencies)
        extracted.appendText("$it\n")
    }
    platforms.map(DesktopPlatform::of).forEach { platform ->
        val env = File(prefixRoot).resolve("${platform.directory}/toolchain.env")
        env.parentFile.mkdirs()
        env.writeText(crossEnv(KonanCross.resolve(platform.konanTarget)))
        println("[libmpv-kotlin-multiplatform] wrote $env")
    }
}

private fun download(url: String, into: File) {
    println("[libmpv-kotlin-multiplatform] downloading $url")
    val process = ProcessBuilder("sh", "-c", "curl -fsSL --retry 3 \"$1\" | tar -xz -C \"$2\"", "sh", url, into.path).inheritIO().start()
    check(process.waitFor() == 0) { "Could not download $url" }
}
