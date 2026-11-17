package io.github.yuroyami.libmpv.buildtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildInfoSourceTest {
    private val depinfo = """
        #!/bin/bash -e
        v_ndk=29.0.14206865
        v_mpv=0.41.0
        v_ffmpeg=9.0.1
        v_libass=0.17.5
        v_libplacebo=7.360.1
        v_dav1d=1.5.4
        v_mbedtls=3.6.7
        v_harfbuzz=14.4.0
        v_freetype=2.14.3
        v_fribidi=1.0.16
        v_unibreak=7.0
        v_lua=5.2.4
        v_libxml2=2.15.4
        v_fontconfig=2.18.3
        v_alsa=1.2.16.1
        v_zlib=1.3.2
        dep_mpv=(ffmpeg libass lua libplacebo)
    """.trimIndent()

    @Test
    fun pinsAreReadFromTheShellFile() {
        val pins = BuildInfoSource.parsePins(depinfo)
        assertEquals("0.41.0", pins["mpv"])
        assertEquals("9.0.1", pins["ffmpeg"])
        assertEquals(16, pins.size)
    }

    @Test
    fun theRenderedObjectCarriesEveryPin() {
        val source = renderBuildInfo(depinfo, "0.1.0", 21)
        assertTrue("package io.github.yuroyami.libmpv" in source)
        assertTrue("public object BuildInfo" in source)
        assertTrue("public const val LIBMPV_KMP: String = \"0.1.0\"" in source)
        assertTrue("public const val MPV: String = \"0.41.0\"" in source)
        assertTrue("public const val NDK: String = \"29.0.14206865\"" in source)
        assertTrue("public const val MIN_SDK: Int = 21" in source)
    }

    @Test
    fun aMissingPinFailsNamingIt() {
        val failure = assertFailsWith<IllegalStateException> {
            renderBuildInfo(depinfo.lines().filterNot { it.startsWith("v_libass=") }.joinToString("\n"), "0.1.0", 21)
        }
        assertTrue("v_libass" in failure.message.orEmpty())
    }

    /** The whole file, as Gradle's GenerateBuildInfoTask wrote it, apart from the first comment line. */
    @Test
    fun theRenderedFileMatchesTheGradleOutput() {
        val expected = """
            |// Written by `./kotlin do updateBuildInfo` from buildscripts/include/depinfo.sh. Do not edit.
            |package io.github.yuroyami.libmpv
            |
            |/**
            | * What this build of libmpv-kotlin-multiplatform contains. Readable before [MPVLib.create]. Once the core is
            | * initialised, mpv reports the same through its `mpv-version` and `ffmpeg-version` properties.
            | */
            |public object BuildInfo {
            |    /** The libmpv-kotlin-multiplatform artifact version. */
            |    public const val LIBMPV_KMP: String = "0.3.0"
            |    /** The mpv release, without the leading v. */
            |    public const val MPV: String = "0.41.0"
            |    /** The FFmpeg release, without the leading n. */
            |    public const val FFMPEG: String = "9.0.1"
            |    public const val LIBASS: String = "0.17.5"
            |    public const val LIBPLACEBO: String = "7.360.1"
            |    public const val DAV1D: String = "1.5.4"
            |    public const val MBEDTLS: String = "3.6.7"
            |    public const val HARFBUZZ: String = "14.4.0"
            |    public const val FREETYPE: String = "2.14.3"
            |    public const val FRIBIDI: String = "1.0.16"
            |    public const val LIBUNIBREAK: String = "7.0"
            |    public const val LUA: String = "5.2.4"
            |    /** libxml2: FFmpeg's DASH demuxer, and fontconfig's configuration parser. */
            |    public const val LIBXML2: String = "2.15.4"
            |    /** fontconfig, libass's font provider on Android and Linux. macOS and iOS use CoreText, Windows DirectWrite. */
            |    public const val FONTCONFIG: String = "2.18.3"
            |    /** alsa-lib, linked into the Linux libraries only. */
            |    public const val ALSA_LIB: String = "1.2.16.1"
            |    /** zlib, built into the Windows library only. The other platforms link the system's. */
            |    public const val ZLIB: String = "1.3.2"
            |    /** The Android NDK the libraries were compiled with. */
            |    public const val NDK: String = "29.0.14206865"
            |    /** The lowest Android API level the libraries were compiled for. */
            |    public const val MIN_SDK: Int = 21
            |    /** The ABIs the AAR carries. */
            |    public val ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            |}
            |""".trimMargin()
        assertEquals(expected, renderBuildInfo(depinfo, "0.3.0", 21))
    }
}
