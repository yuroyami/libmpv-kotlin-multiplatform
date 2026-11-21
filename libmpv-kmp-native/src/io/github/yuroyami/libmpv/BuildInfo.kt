// Written by `./kotlin do updateBuildInfo` from buildscripts/include/depinfo.sh. Do not edit.
package io.github.yuroyami.libmpv

/**
 * What this build of libmpv-kotlin-multiplatform contains. Readable before any core exists. Once a core is
 * initialised, mpv reports the same through its `mpv-version` and `ffmpeg-version` properties.
 */
public object BuildInfo {
    /** The libmpv-kotlin-multiplatform artifact version. */
    public const val LIBMPV_KMP: String = "0.3.0"
    /** The mpv release, without the leading v. */
    public const val MPV: String = "0.42.0"
    /** The FFmpeg release, without the leading n. */
    public const val FFMPEG: String = "9.1"
    public const val LIBASS: String = "0.17.5"
    public const val LIBPLACEBO: String = "7.387.0"
    public const val DAV1D: String = "1.5.5"
    public const val MBEDTLS: String = "3.6.8"
    public const val HARFBUZZ: String = "15.2.0"
    public const val FREETYPE: String = "2.15.0"
    public const val FRIBIDI: String = "1.0.18"
    public const val LIBUNIBREAK: String = "8.0.1"
    public const val LUA: String = "5.2.4"
    /** libxml2: FFmpeg's DASH demuxer, and fontconfig's configuration parser. */
    public const val LIBXML2: String = "2.15.4"
    /** fontconfig, libass's font provider on Android and Linux. macOS and iOS use CoreText, Windows DirectWrite. */
    public const val FONTCONFIG: String = "2.18.4"
    /** alsa-lib, linked into the Linux libraries only. */
    public const val ALSA_LIB: String = "1.2.16.1"
    /** zlib, built into the Windows library only. The other platforms link the system's. */
    public const val ZLIB: String = "1.4"
    /** The Android NDK the libraries were compiled with. */
    public const val NDK: String = "30.0.16248370"
    /** The lowest Android API level the libraries were compiled for. */
    public const val MIN_SDK: Int = 21
    /** The ABIs the AAR carries. */
    public val ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
