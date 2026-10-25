package io.github.yuroyami.libmpv.buildtools

import java.io.File
import java.io.StringReader
import java.util.Properties

/** The `konan.properties` of a Kotlin/Native distribution: which packages a cross target needs, and where they download from. */
class KonanProperties(private val properties: Properties) {
    /** The packages that [host] needs to compile for [target], with every `$name` reference resolved. */
    fun crossDependencies(host: String, target: String): List<String> =
        properties.getProperty("dependencies.$host-$target").orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::resolve)

    /** The LLVM package that a [host] such as `macos_arm64` compiles with. */
    fun llvm(host: String): String = properties.getProperty("llvm.$host.user") ?: error("konan.properties has no llvm.$host.user")

    fun downloadUrl(packageName: String): String = "${properties.getProperty("dependenciesUrl").trimEnd('/')}/$packageName.tar.gz"

    private fun resolve(token: String): String =
        if (token.startsWith("$")) resolve(properties.getProperty(token.drop(1)) ?: error("konan.properties has no ${token.drop(1)}")) else token

    companion object {
        /** The archive of the Kotlin/Native [kotlinVersion] for [host], such as `macos-aarch64`. */
        fun distributionUrl(kotlinVersion: String, host: String): String =
            "https://download.jetbrains.com/kotlin/native/builds/releases/$kotlinVersion/$host/kotlin-native-prebuilt-$host-$kotlinVersion.tar.gz"

        fun parse(text: String): KonanProperties = KonanProperties(Properties().apply { load(StringReader(text)) })

        /** The properties of the Kotlin/Native [kotlinVersion] under [konanRoot], whatever host it was made for. */
        fun of(konanRoot: File, kotlinVersion: String): KonanProperties {
            val file = konanRoot.listFiles().orEmpty()
                .firstOrNull { it.name.startsWith("kotlin-native-prebuilt-") && it.name.endsWith("-$kotlinVersion") }
                ?.resolve("konan/konan.properties")
                ?.takeIf { it.isFile }
                ?: error("No Kotlin/Native $kotlinVersion under $konanRoot. A Kotlin/Native build downloads it.")
            return parse(file.readText())
        }
    }
}
