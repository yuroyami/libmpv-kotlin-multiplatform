package io.github.yuroyami.libmpv

/** A valid 8 kHz mono 16-bit PCM WAV of silence: a 44 byte header and zeroed samples. */
internal object SilentWav {
    fun bytes(seconds: Int): ByteArray {
        val sampleRate = 8000
        val dataSize = sampleRate * 2 * seconds
        fun le(v: Int, n: Int) = ByteArray(n) { (v ushr (8 * it)).toByte() }
        val header = "RIFF".encodeToByteArray() + le(36 + dataSize, 4) + "WAVE".encodeToByteArray() +
            "fmt ".encodeToByteArray() + le(16, 4) + le(1, 2) + le(1, 2) +
            le(sampleRate, 4) + le(sampleRate * 2, 4) + le(2, 2) + le(16, 2) +
            "data".encodeToByteArray() + le(dataSize, 4)
        return header + ByteArray(dataSize)
    }
}
