package app.quartz.core.camera

import android.graphics.Bitmap
import android.media.Image
import kotlin.math.abs

/**
 * Preview-only viewfinder aids from a small YUV stream (plan §7.6):
 * luma histogram, focus peaking and zebras. Nothing here ever touches a
 * saved file.
 */
internal class Analyzer {
    @Volatile var peaking = false
    @Volatile var zebra = false
    @Volatile var histogram = false
    private var frame = 0

    /**
     * @param rotation clockwise degrees to turn sensor-oriented pixels upright
     *   in the portrait viewfinder; [mirror] for the front camera.
     */
    fun analyze(image: Image, rotation: Int, mirror: Boolean): AnalysisFrame? {
        if (!(peaking || zebra || histogram)) return null
        if (frame++ % 3 != 0) return null
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height

        val hist = IntArray(64)
        val step = 4
        val ow = w / step
        val oh = h / step
        val luma = IntArray(ow * oh)
        for (y in 0 until oh) {
            val row = y * step * rowStride
            for (x in 0 until ow) {
                val v = buf.get(row + x * step * pixelStride).toInt() and 0xFF
                luma[y * ow + x] = v
                hist[v shr 2]++
            }
        }
        val overlay = if (peaking || zebra) mask(luma, ow, oh, rotation, mirror) else null
        return AnalysisFrame(if (histogram) hist else IntArray(0), overlay)
    }

    private fun mask(l: IntArray, w: Int, h: Int, rotation: Int, mirror: Boolean): Bitmap {
        val px = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val v = l[i]
                if (zebra && v >= 250 && ((x + y) / 3) % 2 == 0) {
                    px[i] = ZEBRA
                    continue
                }
                if (peaking) {
                    val lap = abs(4 * v - l[i - 1] - l[i + 1] - l[i - w] - l[i + w])
                    if (lap > PEAK_THRESHOLD) px[i] = PEAK
                }
            }
        }
        return rotate(px, w, h, rotation, mirror)
    }

    private fun rotate(src: IntArray, w: Int, h: Int, rotation: Int, mirror: Boolean): Bitmap {
        val swap = rotation == 90 || rotation == 270
        val dw = if (swap) h else w
        val dh = if (swap) w else h
        val dst = IntArray(dw * dh)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var dx: Int
                val dy: Int
                when (rotation) {
                    90 -> { dx = h - 1 - y; dy = x }
                    180 -> { dx = w - 1 - x; dy = h - 1 - y }
                    270 -> { dx = y; dy = w - 1 - x }
                    else -> { dx = x; dy = y }
                }
                if (mirror) dx = dw - 1 - dx
                dst[dy * dw + dx] = src[y * w + x]
            }
        }
        return Bitmap.createBitmap(dst, dw, dh, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val PEAK_THRESHOLD = 60
        private const val PEAK = 0xFFFF3B6B.toInt()
        private const val ZEBRA = 0xCCFFFFFF.toInt()
    }
}

internal object Yuv {
    /** YUV_420_888 → NV21, honouring row and pixel strides. */
    fun toNv21(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val out = ByteArray(w * h * 3 / 2)
        val y = image.planes[0]
        var pos = 0
        val yBuf = y.buffer
        for (row in 0 until h) {
            val base = row * y.rowStride
            if (y.pixelStride == 1) {
                yBuf.position(base)
                yBuf.get(out, pos, w)
                pos += w
            } else {
                for (col in 0 until w) out[pos++] = yBuf.get(base + col * y.pixelStride)
            }
        }
        val u = image.planes[1]
        val v = image.planes[2]
        val uBuf = u.buffer
        val vBuf = v.buffer
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                out[pos++] = vBuf.get(row * v.rowStride + col * v.pixelStride)
                out[pos++] = uBuf.get(row * u.rowStride + col * u.pixelStride)
            }
        }
        return out
    }
}
