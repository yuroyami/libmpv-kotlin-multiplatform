package io.github.yuroyami.libmpv.buildtools

/** The Kotlin source of `BuildInfo`, from the text of depinfo.sh. Throws [IllegalStateException] naming any absent pin. */
fun renderBuildInfo(depinfo: String, version: String, minSdk: Int): String =
    BuildInfoSource.render(BuildInfoSource.parsePins(depinfo), version, minSdk)

/** Writes BuildInfo.kt from the pins in buildscripts/include/depinfo.sh, so code and docs cannot disagree about what is inside. */
object BuildInfoSource {
    private val pinLine = Regex("""^v_([a-z0-9_]+)=(\S+)$""", RegexOption.MULTILINE)

    /** Every `v_<name>=<value>` line of depinfo.sh, keyed by name. */
    fun parsePins(text: String): Map<String, String> =
        pinLine.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

    /** The Kotlin source of BuildInfo. Throws [IllegalStateException] naming any pin that is absent. */
    fun render(pins: Map<String, String>, version: String, minSdk: Int): String {
        fun pin(key: String): String = pins[key] ?: error("depinfo.sh has no v_$key")
        return """
            |// Written by `./kotlin do updateBuildInfo` from buildscripts/include/depinfo.sh. Do not edit.
            |package io.github.yuroyami.libmpv
            |
            |/**
            | * What this build of libmpv-kotlin-multiplatform contains. Readable before [MPVLib.create]. Once the core is
            | * initialised, mpv reports the same through its `mpv-version` and `ffmpeg-version` properties.
            | */
            |public object BuildInfo {
            |    /** The libmpv-kotlin-multiplatform artifact version. */
            |    public const val LIBMPV_KMP: String = "$version"
            |    /** The mpv release, without the leading v. */
            |    public const val MPV: String = "${pin("mpv")}"
            |    /** The FFmpeg release, without the leading n. */
            |    public const val FFMPEG: String = "${pin("ffmpeg")}"
            |    public const val LIBASS: String = "${pin("libass")}"
            |    public const val LIBPLACEBO: String = "${pin("libplacebo")}"
            |    public const val DAV1D: String = "${pin("dav1d")}"
            |    public const val MBEDTLS: String = "${pin("mbedtls")}"
            |    public const val HARFBUZZ: String = "${pin("harfbuzz")}"
            |    public const val FREETYPE: String = "${pin("freetype")}"
            |    public const val FRIBIDI: String = "${pin("fribidi")}"
            |    public const val LIBUNIBREAK: String = "${pin("unibreak")}"
            |    public const val LUA: String = "${pin("lua")}"
            |    /** The Android NDK the libraries were compiled with. */
            |    public const val NDK: String = "${pin("ndk")}"
            |    /** The lowest Android API level the libraries were compiled for. */
            |    public const val MIN_SDK: Int = $minSdk
            |    /** The ABIs the AAR carries. */
            |    public val ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            |}
            |""".trimMargin()
    }
}
