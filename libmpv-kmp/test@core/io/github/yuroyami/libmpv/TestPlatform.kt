package io.github.yuroyami.libmpv

/** A new core, not initialised. Android creates it with the app's context. */
internal expect fun createTestCore(): Mpv

/** Writes [bytes] to a file named [name] in a temporary folder and answers its path. */
internal expect fun writeTestFile(name: String, bytes: ByteArray): String

/** True on an Android device, where the full Android build of mpv runs. */
internal expect val testRunsOnAndroid: Boolean

/** False where the build of mpv has no Lua scripting: the iOS trees, because iOS has no `system()`. */
internal expect val testCoreHasLua: Boolean
