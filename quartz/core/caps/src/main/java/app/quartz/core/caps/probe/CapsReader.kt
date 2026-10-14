package app.quartz.core.caps.probe

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.Key
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodec
import android.os.Build
import android.view.SurfaceHolder
import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.FloatSpan
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.HighSpeedConfig
import app.quartz.core.caps.IntSpan
import app.quartz.core.caps.LongSpan
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Rect
import app.quartz.core.caps.Size
import app.quartz.core.caps.SizeF
import app.quartz.core.caps.StreamSize
import app.quartz.core.caps.WbCalibration

/** Reads CameraCharacteristics into the serialisable [CameraInfo]. */
object CapsReader {

    fun read(id: String, c: CameraCharacteristics, openable: Boolean, logicalParent: String? = null): CameraInfo {
        val sdk = Build.VERSION.SDK_INT
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxResMap = if (sdk >= 31) {
            runCatching { c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION) }.getOrNull()
        } else {
            null
        }
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val physicalIds = if (sdk >= 28) runCatching { c.physicalCameraIds.toList() }.getOrDefault(emptyList()) else emptyList()

        return CameraInfo(
            id = id,
            openable = openable,
            logicalParent = logicalParent,
            physicalIds = physicalIds,
            facing = c.get(CameraCharacteristics.LENS_FACING) ?: C2.FACING_EXTERNAL,
            hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: C2.LEVEL_LEGACY,
            capabilities = caps,
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
            physicalSizeMm = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { SizeF(it.width, it.height) },
            pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { Size(it.width, it.height) },
            activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { Rect(it.left, it.top, it.right, it.bottom) },
            maxResActiveArray = if (sdk >= 31) {
                c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION)?.let { Rect(it.left, it.top, it.right, it.bottom) }
            } else {
                null
            },
            oisModes = ints(c, CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
            isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { IntSpan(it.lower, it.upper) },
            exposureRangeNs = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { LongSpan(it.lower, it.upper) },
            maxFrameDurationNs = c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION),
            minFocusDistance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            focusCalibration = c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?: C2.FOCUS_UNCALIBRATED,
            aeModes = ints(c, CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
            afModes = ints(c, CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
            awbModes = ints(c, CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
            nrModes = ints(c, CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES),
            edgeModes = ints(c, CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES),
            tonemapModes = ints(c, CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES),
            hotPixelModes = ints(c, CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES),
            shadingModes = ints(c, CameraCharacteristics.SHADING_AVAILABLE_MODES),
            aberrationModes = ints(c, CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES),
            distortionModes = if (sdk >= 28) ints(c, CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES) else emptyList(),
            sceneModes = ints(c, CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES),
            effectModes = ints(c, CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS),
            videoStabModes = ints(c, CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
            extendedSceneModes = if (sdk >= 30) {
                c.get(CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES)
                    ?.map { it.mode }.orEmpty()
            } else {
                emptyList()
            },
            aePriorityModes = if (sdk >= 36) ints(c, CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES) else emptyList(),
            colorCorrectionModes = if (sdk >= 36) ints(c, CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) else emptyList(),
            aeCompRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { IntSpan(it.lower, it.upper) } ?: IntSpan(0, 0),
            aeCompStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: (1f / 3),
            maxTonemapPoints = c.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: 0,
            flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f,
            zoomRatioRange = if (sdk >= 30) {
                c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { FloatSpan(it.lower, it.upper) }
            } else {
                null
            },
            timestampSource = c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ?: C2.TIMESTAMP_UNKNOWN,
            maxRegionsAe = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0,
            maxRegionsAf = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0,
            outputs = map?.let { outputs(it) }.orEmpty(),
            maxResOutputs = maxResMap?.let { outputs(it) }.orEmpty().filterKeys { it == Outputs.JPEG || it == Outputs.RAW || it == Outputs.YUV },
            highSpeed = map?.let { highSpeed(it) }.orEmpty(),
            aeFpsRanges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.map { FpsRange(it.lower, it.upper) }.orEmpty(),
            dynamicRangeProfiles = if (sdk >= 33) {
                c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)?.supportedProfiles?.toList().orEmpty()
            } else {
                emptyList()
            },
            wb = wbCalibration(c),
        )
    }

    private fun ints(c: CameraCharacteristics, key: Key<IntArray>): List<Int> =
        runCatching { c.get(key)?.toList() }.getOrNull().orEmpty()

    private fun outputs(map: StreamConfigurationMap): Map<String, List<StreamSize>> {
        val out = mutableMapOf<String, List<StreamSize>>()
        fun byFormat(key: String, format: Int) {
            if (!map.isOutputSupportedFor(format)) return
            val sizes = map.getOutputSizes(format)?.toList().orEmpty() + map.getHighResolutionOutputSizes(format)?.toList().orEmpty()
            out[key] = sizes.distinct().map {
                StreamSize(
                    it.width, it.height,
                    runCatching { map.getOutputMinFrameDuration(format, it) }.getOrDefault(0L),
                    runCatching { map.getOutputStallDuration(format, it) }.getOrDefault(0L),
                )
            }
        }
        byFormat(Outputs.JPEG, ImageFormat.JPEG)
        byFormat(Outputs.YUV, ImageFormat.YUV_420_888)
        byFormat(Outputs.RAW, ImageFormat.RAW_SENSOR)
        byFormat(Outputs.PRIVATE, ImageFormat.PRIVATE)
        if (Build.VERSION.SDK_INT >= 29) runCatching { byFormat(Outputs.HEIC, ImageFormat.HEIC) }
        out[Outputs.VIDEO] = map.getOutputSizes(MediaCodec::class.java).orEmpty().map {
            StreamSize(it.width, it.height, runCatching { map.getOutputMinFrameDuration(MediaCodec::class.java, it) }.getOrDefault(0L))
        }
        out[Outputs.PREVIEW] = map.getOutputSizes(SurfaceHolder::class.java).orEmpty().map {
            StreamSize(it.width, it.height, runCatching { map.getOutputMinFrameDuration(SurfaceHolder::class.java, it) }.getOrDefault(0L))
        }
        return out
    }

    private fun highSpeed(map: StreamConfigurationMap): List<HighSpeedConfig> =
        runCatching {
            map.highSpeedVideoSizes.orEmpty().map { s ->
                HighSpeedConfig(s.width, s.height, map.getHighSpeedVideoFpsRangesFor(s).orEmpty().map { FpsRange(it.lower, it.upper) })
            }
        }.getOrDefault(emptyList())

    private fun wbCalibration(c: CameraCharacteristics): WbCalibration? = runCatching {
        val i1 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1) ?: return null
        val i2 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt() ?: i1
        val m1 = c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) ?: return null
        val m2 = c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2) ?: m1
        fun flat(m: android.hardware.camera2.params.ColorSpaceTransform) =
            (0 until 3).flatMap { r -> (0 until 3).map { col -> m.getElement(col, r).toFloat() } }
        WbCalibration(i1, i2, flat(m1), flat(m2))
    }.getOrNull()
}
