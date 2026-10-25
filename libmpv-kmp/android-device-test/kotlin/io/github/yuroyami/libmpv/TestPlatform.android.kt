package io.github.yuroyami.libmpv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File

private val context: Context get() = ApplicationProvider.getApplicationContext()

internal actual fun createTestCore(): Mpv = Mpv.create(context)

internal actual fun writeTestFile(name: String, bytes: ByteArray): String =
    File(context.cacheDir, name).apply { writeBytes(bytes) }.absolutePath

internal actual val testRunsOnAndroid: Boolean = true
