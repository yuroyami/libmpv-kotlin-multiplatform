package io.github.yuroyami.libmpv.jni

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Finds and loads `mpv_kmp_jni` on a desktop JVM. Three sources, in this order:
 * 1. The path in the system property [PATH_PROPERTY].
 * 2. `System.loadLibrary`, which reads `java.library.path`. A packager that ships its own copy uses this.
 * 3. The copy in this jar under `libmpv-kmp-native/<platform>/`, extracted to a temporary directory.
 */
internal object JniLibrary {
    const val PATH_PROPERTY: String = "libmpv-kmp.jni.path"
    const val RESOURCE_ROOT: String = "libmpv-kmp-native"
    private const val LIBRARY_NAME = "mpv_kmp_jni"
    private const val MANIFEST_NAME = "manifest.txt"

    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val override = System.getProperty(PATH_PROPERTY)
            if (!override.isNullOrBlank()) System.load(override) else loadInstalledOrBundled()
            loaded = true
        }
    }

    private fun loadInstalledOrBundled() {
        if (runCatching { System.loadLibrary(LIBRARY_NAME) }.isSuccess) return
        val platform = platformDirectory(System.getProperty("os.name").orEmpty(), System.getProperty("os.arch").orEmpty())
        val directoryResource = "/$RESOURCE_ROOT/$platform"
        // manifest.txt names every file of the bundle, the JNI library last. Lines that start with # are comments.
        val manifest = JniLibrary::class.java.getResourceAsStream("$directoryResource/$MANIFEST_NAME")
            ?.bufferedReader()?.use { it.readLines() }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?: throw UnsatisfiedLinkError(missingLibraryMessage(platform))

        val directory = Files.createTempDirectory("libmpv-kmp-jni").toFile().apply { deleteOnExit() }
        val fileName = libraryFileName(platform)
        var library: File? = null
        for (name in manifest) {
            val stream = JniLibrary::class.java.getResourceAsStream("$directoryResource/$name")
                ?: throw UnsatisfiedLinkError("The manifest at $directoryResource names $name, but the jar does not have it.")
            val target = File(directory, name)
            stream.use { Files.copy(it, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            target.deleteOnExit()
            if (name == fileName) library = target
        }
        System.load((library ?: throw UnsatisfiedLinkError("The bundle at $directoryResource has no $fileName.")).absolutePath)
    }

    /** `macos-arm64`, `linux-x64`, `windows-x64` and so on, from the `os.name` and `os.arch` system properties. */
    fun platformDirectory(osName: String, osArch: String): String {
        val name = osName.lowercase()
        val os = when {
            "mac" in name || "darwin" in name -> "macos"
            "win" in name -> "windows"
            else -> "linux"
        }
        val cpu = if (osArch.lowercase() in setOf("aarch64", "arm64")) "arm64" else "x64"
        return "$os-$cpu"
    }

    fun libraryFileName(platform: String): String = when {
        platform.startsWith("macos") -> "lib$LIBRARY_NAME.dylib"
        platform.startsWith("windows") -> "$LIBRARY_NAME.dll"
        else -> "lib$LIBRARY_NAME.so"
    }

    fun missingLibraryMessage(platform: String): String =
        "libmpv-kotlin-multiplatform has no native library for $platform in this jar. " +
            "Put ${libraryFileName(platform)} on java.library.path, " +
            "or pass its full path with -D$PATH_PROPERTY=<path>."
}
