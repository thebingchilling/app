package app.quartz.core.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves captures into the shared DCIM/Quartz folder through MediaStore
 * (scoped storage on Android 10+, the DATA column on 8–9), marked pending
 * while they're being written.
 */
class MediaWriter(private val context: Context) {

    private val resolver get() = context.contentResolver
    var softwareVersion: String = ""

    fun fileName(prefix: String, timeMs: Long = System.currentTimeMillis(), suffix: String = ""): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date(timeMs)) + suffix

    /** Writes HAL-encoded JPEG bytes unchanged, adds EXIF, publishes. */
    suspend fun saveJpeg(bytes: ByteArray, name: String, meta: CaptureMeta, target: Uri? = null): Uri =
        withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("cap", ".jpg", context.cacheDir)
            try {
                tmp.writeBytes(bytes)
                runCatching { ExifWriter.apply(tmp, meta.copy(softwareVersion = softwareVersion), Build.MANUFACTURER, Build.MODEL) }
                if (target != null) {
                    resolver.openOutputStream(target, "w")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    target
                } else {
                    insertImage("$name.jpg", "image/jpeg", meta.timestampMs) { out -> tmp.inputStream().use { it.copyTo(out) } }
                }
            } finally {
                tmp.delete()
            }
        }

    /** HEIC from the camera carries its own EXIF; write it as is. */
    suspend fun saveHeic(bytes: ByteArray, name: String, timeMs: Long): Uri = withContext(Dispatchers.IO) {
        insertImage("$name.heic", "image/heif", timeMs) { it.write(bytes) }
    }

    suspend fun saveDng(name: String, timeMs: Long, write: (OutputStream) -> Unit): Uri = withContext(Dispatchers.IO) {
        insertImage("$name.dng", "image/x-adobe-dng", timeMs, write)
    }

    private fun insertImage(displayName: String, mime: String, timeMs: Long, write: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_TAKEN, timeMs)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$FOLDER")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, legacyFile(displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused $displayName")
        try {
            resolver.openOutputStream(uri, "w")!!.use(write)
            publish(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** A pending video entry with a file descriptor the muxer writes into. */
    class PendingVideo(val uri: Uri, val pfd: ParcelFileDescriptor)

    fun createVideo(name: String, timeMs: Long, target: Uri? = null): PendingVideo {
        if (target != null) {
            return PendingVideo(target, resolver.openFileDescriptor(target, "rw")!!)
        }
        val displayName = "$name.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, timeMs)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$FOLDER")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, legacyFile(displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused $displayName")
        return PendingVideo(uri, resolver.openFileDescriptor(uri, "rw")!!)
    }

    fun finishVideo(video: PendingVideo, keep: Boolean) {
        runCatching { video.pfd.close() }
        if (keep) publish(video.uri) else runCatching { resolver.delete(video.uri, null, null) }
    }

    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    private fun legacyFile(displayName: String): File {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), FOLDER)
        dir.mkdirs()
        return File(dir, displayName)
    }

    /** Most recent Quartz captures (photos and videos), newest first. */
    suspend fun recent(limit: Int = 100): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<MediaItem>()
        fun query(collection: Uri, video: Boolean) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_TAKEN,
            )
            val (selection, args) = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("${Environment.DIRECTORY_DCIM}/$FOLDER%")
            } else {
                @Suppress("DEPRECATION")
                "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/${Environment.DIRECTORY_DCIM}/$FOLDER/%")
            }
            runCatching {
                resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_TAKEN} DESC")?.use { c ->
                    while (c.moveToNext() && out.size < limit * 2) {
                        val id = c.getLong(0)
                        out += MediaItem(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(1) ?: "",
                            mime = c.getString(2) ?: "",
                            dateTakenMs = c.getLong(3),
                            video = video,
                        )
                    }
                }
            }
        }
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        out.sortedByDescending { it.dateTakenMs }.take(limit)
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    companion object {
        const val FOLDER = "Quartz"
    }
}
