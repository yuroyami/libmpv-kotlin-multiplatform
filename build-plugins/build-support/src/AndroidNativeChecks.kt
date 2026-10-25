package io.github.yuroyami.libmpvkt.buildtools

import java.io.File

/**
 * Refuses Android native libraries that are incomplete, misaligned, linked for a newer API than the
 * AAR's minSdk, compiled for another Kotlin package, or carrying an old libc++. Each rule is a
 * failure that reached a device once, or would have.
 */
object AndroidNativeChecks {
    /** Every Android ABI the release carries, in the order the builds run. */
    val ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** Google Play requires 16 KB page alignment on 64-bit devices; the 32-bit ABIs are reported only. */
    val ABIS_REQUIRING_16K: Set<String> = setOf("arm64-v8a", "x86_64")

    const val MIN_ALIGN: Long = 16384

    /** The library the JNI sources compile to. The probe symbol must be in it. */
    const val JNI_LIB: String = "libmpvkt_jni.so"

    /** The C++ library. The libc++ marker must be in it. */
    const val LIBCXX: String = "libc++_shared.so"

    /** The ABIs in [env], a comma-separated list such as `LIBMPVKT_ABIS`. All of [ABIS] when it is unset or blank. */
    fun requiredAbis(env: String?): List<String> =
        env?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.takeIf { it.isNotEmpty() } ?: ABIS

    /**
     * Every problem in [root] for [abis], as one sentence each. Empty means ready. [probeSymbol] is
     * searched in [JNI_LIB] and [libcxxMarker] in [LIBCXX]; null skips the search.
     */
    fun findProblems(
        root: File,
        abis: List<String>,
        files: List<String>,
        minApi: Int,
        probeSymbol: String?,
        libcxxMarker: String?,
    ): List<String> {
        val problems = mutableListOf<String>()
        for (abi in abis) {
            val dir = File(root, abi)
            for (lib in files) {
                val file = File(dir, lib)
                if (!file.isFile) {
                    problems += "$abi/$lib is missing"
                    continue
                }
                // Only the headers, a few hundred bytes: the libraries are 142 MB together.
                val header = runCatching { Elf.headerBytes(file) }.getOrNull()
                val aligns = header?.let { runCatching { Elf.loadAlignments(it) }.getOrNull() }
                if (header == null || aligns == null) {
                    problems += "$abi/$lib is not an ELF file"
                    continue
                }
                val smallest = aligns.minOrNull() ?: 0L
                if (smallest < MIN_ALIGN && abi in ABIS_REQUIRING_16K) {
                    problems += "$abi/$lib has a LOAD segment aligned to $smallest bytes; 16384 is required"
                }
                val api = Elf.androidApiLevel(header)
                if (api != null && api > minApi) {
                    problems += "$abi/$lib is linked for API $api, above the AAR's minSdk $minApi"
                }
                // The two text searches read their whole file; both libraries are small once stripped.
                if (lib == JNI_LIB && probeSymbol != null && !file.readBytes().containsAscii(probeSymbol)) {
                    problems += "$abi/$lib does not export $probeSymbol; the C side was compiled for another package"
                }
                if (lib == LIBCXX && libcxxMarker != null && !file.readBytes().containsAscii(libcxxMarker)) {
                    problems += "$abi/$lib is an older libc++ than the NDK r29 one mpv needs"
                }
            }
        }
        return problems
    }

    /** The failure text for [problems]. */
    fun failureMessage(problems: List<String>): String =
        "Native libraries are not ready to ship:\n" + problems.joinToString("\n") { "  - $it" } +
            "\nBuild them with buildscripts/buildall.sh --arch <arch>, or fetch a release's with " +
            ".github/scripts/fetch-natives.sh <tag>. A laptop with one architecture sets LIBMPVKT_ABIS=<abi>."

    /**
     * Copies `<abi>/<file>` from [root] to `lib/<abi>/<file>` in [out] for every ABI that has all [files].
     * [out] is cleared first. Returns one warning per ABI that is left out.
     */
    fun packageNatives(root: File, files: List<String>, out: File): List<String> {
        out.deleteRecursively()
        out.mkdirs()
        val warnings = mutableListOf<String>()
        for (abi in ABIS) {
            val missing = files.filterNot { File(root, "$abi/$it").isFile }
            if (missing.isNotEmpty()) {
                val what = if (missing.size == files.size) "has none of the libraries" else "lacks ${missing.joinToString()}"
                warnings += "$abi $what, so the jar carries no $abi libraries"
                continue
            }
            val target = File(out, "lib/$abi").apply { mkdirs() }
            files.forEach { File(root, "$abi/$it").copyTo(File(target, it), overwrite = true) }
        }
        return warnings
    }

    private fun ByteArray.containsAscii(text: String): Boolean =
        String(this, Charsets.ISO_8859_1).contains(text)
}
