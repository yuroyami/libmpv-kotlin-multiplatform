package io.github.yuroyami.libmpv

public fun Mpv.expandText(text: String): MpvResult<String> = command(MpvCommands.expandText(text)).map { it.asString().orEmpty() }
public fun Mpv.expandPath(path: String): MpvResult<String> = command(MpvCommands.expandPath(path)).map { it.asString().orEmpty() }

/**
 * A `screenshot-raw` image in the byte order an ARGB_8888 bitmap stores (R, G, B, A), without row
 * padding. `rgba` with no padding is returned as it is. Null for a format that does not fit: `rgba64`.
 */
internal fun rgbaPixels(data: ByteArray, width: Int, height: Int, stride: Int, format: String): ByteArray? {
    val swap = when (format) {
        "rgba" -> false
        "bgra", "bgr0" -> true
        else -> return null
    }
    val row = width * 4
    require(stride >= row && data.size >= stride * (height - 1) + row) { "screenshot-raw data is too short for ${width}x$height" }
    if (!swap && stride == row && data.size == row * height) return data
    val out = ByteArray(row * height)
    for (y in 0 until height) data.copyInto(out, y * row, y * stride, y * stride + row)
    if (swap) {
        val opaque = format == "bgr0"
        for (i in out.indices step 4) {
            val blue = out[i]
            out[i] = out[i + 2]
            out[i + 2] = blue
            if (opaque) out[i + 3] = -1
        }
    }
    return out
}
