package app.quartz.core.media

import android.location.Location

/** What the camera reported for a capture, written into EXIF as-is (no invented values). */
data class CaptureMeta(
    val timestampMs: Long = System.currentTimeMillis(),
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val fNumber: Float? = null,
    val focalMm: Float? = null,
    val focal35Mm: Int? = null,
    val lensDescription: String? = null,
    /** EXIF orientation constant, only for files that don't carry their own (YUV-encoded crops). */
    val exifOrientation: Int? = null,
    val location: Location? = null,
    /** Short record of the processing that was requested and used, for UserComment. */
    val naturalSummary: String? = null,
    val softwareVersion: String = "",
    val flashFired: Boolean? = null,
    val whiteBalanceManual: Boolean = false,
)

data class MediaItem(
    val uri: android.net.Uri,
    val name: String,
    val mime: String,
    val dateTakenMs: Long,
    val video: Boolean,
)
