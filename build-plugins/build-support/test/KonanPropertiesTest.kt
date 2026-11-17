package io.github.yuroyami.libmpv.buildtools

import kotlin.test.Test
import kotlin.test.assertEquals

class KonanPropertiesTest {
    private val properties = """
        dependenciesUrl = https://download.jetbrains.com/kotlin/native
        toolchainDependency.linux_x64 = x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2
        toolchainDependency.mingw_x64 = msys2-mingw-w64-x86_64-2
        dependencies.macos_arm64-linux_x64 = \
            ${'$'}toolchainDependency.linux_x64
        dependencies.macos_arm64-mingw_x64 = ${'$'}toolchainDependency.mingw_x64
        llvm.macos_arm64.dev=llvm-21-aarch64-macos-dev-97
        llvm.macos_arm64.user=llvm-21-aarch64-macos-essentials-97
        llvm-21-aarch64-macos-essentials-97.default=remote:public:resources/llvm/21-aarch64-macos
    """.trimIndent()

    @Test
    fun aContinuedReferenceResolvesToItsPackage() {
        val konan = KonanProperties.parse(properties)
        assertEquals(listOf("x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"), konan.crossDependencies("macos_arm64", "linux_x64"))
        assertEquals(listOf("msys2-mingw-w64-x86_64-2"), konan.crossDependencies("macos_arm64", "mingw_x64"))
    }

    @Test
    fun theDownloadUrlComesFromTheProperties() {
        assertEquals(
            "https://download.jetbrains.com/kotlin/native/msys2-mingw-w64-x86_64-2.tar.gz",
            KonanProperties.parse(properties).downloadUrl("msys2-mingw-w64-x86_64-2"),
        )
    }

    /** LLVM lives in a folder of its own, named by its `.default` line. A download from the root answers 404. */
    @Test
    fun aPackageWithAFolderDownloadsFromThatFolder() {
        assertEquals(
            "https://download.jetbrains.com/kotlin/native/resources/llvm/21-aarch64-macos/llvm-21-aarch64-macos-essentials-97.tar.gz",
            KonanProperties.parse(properties).downloadUrl("llvm-21-aarch64-macos-essentials-97"),
        )
    }

    @Test
    fun theHostLlvmIsTheUserPackage() {
        assertEquals("llvm-21-aarch64-macos-essentials-97", KonanProperties.parse(properties).llvm("macos_arm64"))
    }

    @Test
    fun theDistributionUrlNamesTheVersionTwice() {
        assertEquals(
            "https://github.com/JetBrains/kotlin/releases/download/v2.4.21/kotlin-native-prebuilt-macos-aarch64-2.4.21.tar.gz",
            KonanProperties.distributionUrl("2.4.21", "macos-aarch64"),
        )
    }
}
