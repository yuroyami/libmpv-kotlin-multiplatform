// Written by `./kotlin do updateBuildInfo` from buildscripts/include/depinfo.sh. Do not edit.
package io.github.yuroyami.libmpv

/**
 * What this build of libmpv-kotlin-multiplatform contains. Readable before [MPVLib.create]. Once the core is
 * initialised, mpv reports the same through its `mpv-version` and `ffmpeg-version` properties.
 */
public object BuildInfo {
    /** The libmpv-kotlin-multiplatform artifact version. */
    public const val LIBMPV_KMP: String = "0.3.0"
    /** The mpv release, without the leading v. */
    public const val MPV: String = "0.41.0"
    /** The FFmpeg release, without the leading n. */
    public const val FFMPEG: String = "9.0.1"
    public const val LIBASS: String = "0.17.5"
    public const val LIBPLACEBO: String = "7.360.1"
    public const val DAV1D: String = "1.5.4"
    public const val MBEDTLS: String = "3.6.7"
    public const val HARFBUZZ: String = "14.4.0"
    public const val FREETYPE: String = "2.14.3"
    public const val FRIBIDI: String = "1.0.16"
    public const val LIBUNIBREAK: String = "7.0"
    public const val LUA: String = "5.2.4"
    /** The Android NDK the libraries were compiled with. */
    public const val NDK: String = "29.0.14206865"
    /** The lowest Android API level the libraries were compiled for. */
    public const val MIN_SDK: Int = 21
    /** The ABIs the AAR carries. */
    public val ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
