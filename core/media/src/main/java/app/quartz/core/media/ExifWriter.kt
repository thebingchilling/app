package app.quartz.core.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Adds capture metadata to a JPEG on disk. ExifInterface rewrites only the
 * APP1 segment; the compressed image data is copied byte for byte.
 */
object ExifWriter {

    fun apply(file: File, meta: CaptureMeta, make: String, model: String) {
        val exif = ExifInterface(file)
        fun set(tag: String, value: String?) {
            if (value != null && exif.getAttribute(tag) == null) exif.setAttribute(tag, value)
        }
        fun force(tag: String, value: String?) {
            if (value != null) exif.setAttribute(tag, value)
        }
        val date = Date(meta.timestampMs)
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        set(ExifInterface.TAG_MAKE, make)
        set(ExifInterface.TAG_MODEL, model)
        force(ExifInterface.TAG_SOFTWARE, "Quartz ${meta.softwareVersion}".trim())
        set(ExifInterface.TAG_DATETIME_ORIGINAL, fmt.format(date))
        set(ExifInterface.TAG_DATETIME, fmt.format(date))
        set(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset(meta.timestampMs))
        meta.iso?.let { set(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
        meta.exposureNs?.let { set(ExifInterface.TAG_EXPOSURE_TIME, (it / 1e9).toString()) }
        meta.fNumber?.let { set(ExifInterface.TAG_F_NUMBER, it.toString()) }
        meta.focalMm?.let { set(ExifInterface.TAG_FOCAL_LENGTH, rational(it.toDouble())) }
        meta.focal35Mm?.let { set(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, it.toString()) }
        meta.lensDescription?.let { force(ExifInterface.TAG_LENS_MODEL, it) }
        meta.exifOrientation?.let { force(ExifInterface.TAG_ORIENTATION, it.toString()) }
        meta.naturalSummary?.let { force(ExifInterface.TAG_USER_COMMENT, it) }
        force(ExifInterface.TAG_WHITE_BALANCE, if (meta.whiteBalanceManual) "1" else "0")
        meta.location?.let { exif.setGpsInfo(it) }
        exif.saveAttributes()
    }

    private fun rational(v: Double): String = "${(v * 1000).toLong()}/1000"

    private fun offset(ms: Long): String {
        val off = TimeZone.getDefault().getOffset(ms) / 60000
        val sign = if (off < 0) "-" else "+"
        val a = kotlin.math.abs(off)
        return "%s%02d:%02d".format(Locale.US, sign, a / 60, a % 60)
    }

    /** EXIF orientation constant for a clockwise rotation in degrees. */
    fun orientationFor(degrees: Int, mirrored: Boolean = false): Int = when ((degrees % 360 + 360) % 360) {
        90 -> if (mirrored) ExifInterface.ORIENTATION_TRANSPOSE else ExifInterface.ORIENTATION_ROTATE_90
        180 -> if (mirrored) ExifInterface.ORIENTATION_FLIP_VERTICAL else ExifInterface.ORIENTATION_ROTATE_180
        270 -> if (mirrored) ExifInterface.ORIENTATION_TRANSVERSE else ExifInterface.ORIENTATION_ROTATE_270
        else -> if (mirrored) ExifInterface.ORIENTATION_FLIP_HORIZONTAL else ExifInterface.ORIENTATION_NORMAL
    }
}
