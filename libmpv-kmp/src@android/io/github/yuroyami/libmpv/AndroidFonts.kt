package io.github.yuroyami.libmpv

import android.content.Context
import android.system.Os
import java.io.File

/**
 * Points fontconfig, libass's font provider on Android, at the system fonts. Android has no `fonts.conf`,
 * so this writes one and names it in `FONTCONFIG_FILE`. An app that sets `FONTCONFIG_FILE` itself keeps its own.
 */
internal object AndroidFonts {
    fun install(context: Context) {
        if (!Os.getenv(VARIABLE).isNullOrEmpty()) return
        val directory = File(context.noBackupFilesDir, "libmpv-kmp").apply { mkdirs() }
        val cache = File(context.cacheDir, "libmpv-kmp-fontconfig")
        val file = File(directory, "fonts.conf")
        val text = config(cache)
        if (!file.isFile || file.readText() != text) {
            val partial = File.createTempFile("fonts-", ".partial", directory)
            partial.writeText(text)
            if (!partial.renameTo(file)) partial.delete()
        }
        Os.setenv(VARIABLE, file.absolutePath, true)
    }

    /**
     * The system font folders, a cache in the app's cache folder, and the generic families. Without the
     * aliases, `sans-serif` (mpv's default `sub-font`) and Arial would match whatever font sorts first.
     */
    fun config(cache: File): String = """
        |<?xml version="1.0"?>
        |<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
        |<fontconfig>
        |  <dir>/system/fonts</dir>
        |  <dir>/product/fonts</dir>
        |  <cachedir>${cache.absolutePath}</cachedir>
        |  <alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>
        |  <alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias>
        |  <alias><family>monospace</family><prefer><family>Droid Sans Mono</family><family>Cutive Mono</family></prefer></alias>
        |  <alias binding="same"><family>Arial</family><family>Helvetica</family><family>Verdana</family><family>Tahoma</family><family>Trebuchet MS</family><default><family>sans-serif</family></default></alias>
        |  <alias binding="same"><family>Times New Roman</family><family>Times</family><family>Georgia</family><default><family>serif</family></default></alias>
        |  <alias binding="same"><family>Courier New</family><family>Courier</family><family>Consolas</family><default><family>monospace</family></default></alias>
        |  <match target="pattern">
        |    <test qual="all" name="family" compare="not_eq"><string>sans-serif</string></test>
        |    <test qual="all" name="family" compare="not_eq"><string>serif</string></test>
        |    <test qual="all" name="family" compare="not_eq"><string>monospace</string></test>
        |    <edit name="family" mode="append_last"><string>sans-serif</string></edit>
        |  </match>
        |</fontconfig>
        |""".trimMargin()

    private const val VARIABLE = "FONTCONFIG_FILE"
}
