package io.github.yuroyami.libmpv.buildplugins.mpvnatives

import io.github.yuroyami.libmpv.buildtools.DesktopPlatform
import io.github.yuroyami.libmpv.buildtools.KonanCross
import io.github.yuroyami.libmpv.buildtools.KonanProperties
import io.github.yuroyami.libmpv.buildtools.crossEnv
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.TaskAction
import java.io.File

/**
 * Writes `<prefixRoot>/<platform>/toolchain.env` for each Linux and Windows platform, which the build scripts
 * source. A sysroot that Kotlin/Native has not downloaded yet is fetched first, from its `konan.properties`.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun writeCrossToolchains(platforms: List<String>, kotlinVersion: String, prefixRoot: String) {
    val konanRoot = KonanCross.konanRoot()
    val dependencies = konanRoot.resolve("dependencies").apply { mkdirs() }
    platforms.map(DesktopPlatform::of).forEach { platform ->
        val properties = KonanProperties.of(konanRoot, kotlinVersion)
        properties.crossDependencies("macos_arm64", platform.konanTarget)
            .filterNot { dependencies.resolve(it).isDirectory }
            .forEach { download(properties.downloadUrl(it), dependencies) }
        val env = File(prefixRoot).resolve("${platform.directory}/toolchain.env")
        env.parentFile.mkdirs()
        env.writeText(crossEnv(platform, KonanCross.resolve(platform.konanTarget)))
        println("[libmpv-kotlin-multiplatform] wrote $env")
    }
}

private fun download(url: String, into: File) {
    println("[libmpv-kotlin-multiplatform] downloading $url")
    val process = ProcessBuilder("sh", "-c", "curl -fsSL --retry 3 \"$1\" | tar -xz -C \"$2\"", "sh", url, into.path).inheritIO().start()
    check(process.waitFor() == 0) { "Could not download $url" }
}
