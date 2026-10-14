package app.quartz.core.caps.probe

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.DeviceCaps
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Builds [DeviceCaps] (plan §4.1) and caches it per OS build. Reading
 * characteristics doesn't open any camera, so this is quick and has no
 * visible effect; it re-runs whenever Build.FINGERPRINT changes.
 */
class CapabilityProbe(private val context: Context) {

    private val cacheFile get() = File(context.filesDir, "device_caps.json")

    suspend fun load(probeHiddenIds: Boolean = false, force: Boolean = false): DeviceCaps = withContext(Dispatchers.IO) {
        if (!force) {
            runCatching { JSON.decodeFromString(DeviceCaps.serializer(), cacheFile.readText()) }.getOrNull()
                ?.takeIf { it.fingerprint == Build.FINGERPRINT && it.schema == DeviceCaps.SCHEMA }
                ?.let { return@withContext it }
        }
        val caps = build(probeHiddenIds)
        runCatching { cacheFile.writeText(JSON.encodeToString(DeviceCaps.serializer(), caps)) }
        caps
    }

    /** Writes a shareable copy for bug reports and returns it. */
    suspend fun export(caps: DeviceCaps): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        File(dir, "quartz-caps-${Build.MANUFACTURER}-${Build.MODEL}.json".replace(' ', '_')).apply {
            writeText(JSON_PRETTY.encodeToString(DeviceCaps.serializer(), caps))
        }
    }

    private fun build(probeHiddenIds: Boolean): DeviceCaps {
        val manager = context.getSystemService(CameraManager::class.java)
        val listed = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        val cameras = mutableListOf<CameraInfo>()
        val seen = mutableSetOf<String>()

        fun add(id: String, openable: Boolean, parent: String?) {
            if (!seen.add(id)) return
            val c = runCatching { manager.getCameraCharacteristics(id) }.getOrNull() ?: return
            val info = runCatching { CapsReader.read(id, c, openable, parent) }
                .onFailure { Log.w(TAG, "camera $id", it) }
                .getOrNull() ?: return
            cameras += info
            info.physicalIds.forEach { pid -> add(pid, pid in listed, id) }
        }

        listed.forEach { add(it, true, null) }
        if (probeHiddenIds) {
            // Some OEM builds can open lenses they don't list (plan §4.1, step 1).
            for (n in 0..63) {
                val id = n.toString()
                if (id in seen) continue
                if (runCatching { manager.getCameraCharacteristics(id) }.isSuccess) add(id, true, null)
            }
        }

        val sizes = mutableSetOf<Size>()
        cameras.forEach { cam ->
            cam.sizes(Outputs.VIDEO).forEach { sizes += it.size }
            cam.highSpeed.forEach { sizes += it.size }
        }
        val encoders = runCatching { EncoderProbe.probe(sizes, EncoderProbe.defaultFps) }
            .onFailure { Log.w(TAG, "encoders", it) }
            .getOrDefault(emptyList())

        val audio = context.getSystemService(AudioManager::class.java)
        val unprocessed = audio?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        return DeviceCaps(
            fingerprint = Build.FINGERPRINT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            sdk = Build.VERSION.SDK_INT,
            cameras = cameras,
            encoders = encoders,
            audioUnprocessed = unprocessed,
        )
    }

    companion object {
        private const val TAG = "QuartzProbe"
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val JSON_PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}
