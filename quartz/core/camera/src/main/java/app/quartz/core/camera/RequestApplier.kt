package app.quartz.core.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.os.Build
import android.util.Range
import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.lens.ZoomMath
import app.quartz.core.caps.natural.NaturalPolicy
import app.quartz.core.caps.natural.NaturalRequest
import app.quartz.core.caps.natural.Tonemap
import app.quartz.core.settings.FlashMode
import kotlin.math.pow

/** Writes [NaturalRequest] and [Controls] into a CaptureRequest.Builder. */
internal object RequestApplier {

    fun natural(b: CaptureRequest.Builder, n: NaturalRequest) {
        val sdk = Build.VERSION.SDK_INT
        b.set(CaptureRequest.CONTROL_SCENE_MODE, n.sceneMode)
        b.set(CaptureRequest.CONTROL_EFFECT_MODE, n.effectMode)
        b.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, n.faceDetect)
        n.noiseReduction?.let { b.set(CaptureRequest.NOISE_REDUCTION_MODE, it) }
        n.edge?.let { b.set(CaptureRequest.EDGE_MODE, it) }
        n.hotPixel?.let { b.set(CaptureRequest.HOT_PIXEL_MODE, it) }
        n.shading?.let { b.set(CaptureRequest.SHADING_MODE, it) }
        n.aberration?.let { b.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, it) }
        if (sdk >= 28) n.distortion?.let { b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, it) }
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, n.videoStabilization)
        n.ois?.let { b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, it) }
        if (sdk >= 30) n.extendedSceneMode?.let { b.set(CaptureRequest.CONTROL_EXTENDED_SCENE_MODE, it) }
        if (sdk >= 34 && n.autoframingOff) b.set(CaptureRequest.CONTROL_AUTOFRAMING, CameraMetadata.CONTROL_AUTOFRAMING_OFF)
        when (val t = n.tonemap) {
            Tonemap.PresetSrgb -> {
                b.set(CaptureRequest.TONEMAP_MODE, C2.TONEMAP_PRESET_CURVE)
                b.set(CaptureRequest.TONEMAP_PRESET_CURVE, C2.TONEMAP_PRESET_CURVE_SRGB)
            }
            is Tonemap.Curve -> {
                b.set(CaptureRequest.TONEMAP_MODE, C2.TONEMAP_CONTRAST_CURVE)
                b.set(CaptureRequest.TONEMAP_CURVE, TonemapCurve(t.points, t.points, t.points))
            }
            is Tonemap.Device -> t.mode?.let { b.set(CaptureRequest.TONEMAP_MODE, it) }
        }
    }

    /**
     * Controls for the repeating (preview/record) request.
     * [previewMaxExposureNs] keeps a long manual shutter from freezing the
     * viewfinder; ISO is raised to match so preview brightness is still right.
     */
    fun controls(
        b: CaptureRequest.Builder,
        cam: CameraInfo,
        c: Controls,
        video: Boolean,
        previewMaxExposureNs: Long? = 100_000_000L,
        priorityMetering: Pair<Long, Int>? = null,
    ) {
        val sdk = Build.VERSION.SDK_INT
        // Zoom
        c.zoom?.let { z ->
            val zr = z.zoomRatio
            if (zr != null && sdk >= 30) {
                b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zr)
            } else if (z.cropFactor > 1.001f) {
                cam.activeArray?.let { a ->
                    val r = ZoomMath.cropRegion(app.quartz.core.caps.Rect(0, 0, a.width, a.height), z.cropFactor)
                    b.set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(r.left, r.top, r.right, r.bottom))
                }
            }
        }

        // Exposure
        val manualIso = c.iso
        val manualExp = c.exposureNs
        val fullManual = manualIso != null && manualExp != null && cam.manualSensor
        val priority = !fullManual && (manualIso != null || manualExp != null) && cam.manualSensor
        val nativePriority = priority && sdk >= 36 && cam.aePriorityModes.isNotEmpty()
        when {
            fullManual -> manual(b, cam, manualExp!!, manualIso!!, previewMaxExposureNs)
            nativePriority -> {
                b.set(CaptureRequest.CONTROL_AE_MODE, C2.AE_ON)
                if (manualExp != null && C2.AE_PRIORITY_EXPOSURE_TIME in cam.aePriorityModes) {
                    b.set(CaptureRequest.CONTROL_AE_PRIORITY_MODE, C2.AE_PRIORITY_EXPOSURE_TIME)
                    b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampExp(cam, manualExp))
                } else if (manualIso != null && C2.AE_PRIORITY_SENSITIVITY in cam.aePriorityModes) {
                    b.set(CaptureRequest.CONTROL_AE_PRIORITY_MODE, C2.AE_PRIORITY_SENSITIVITY)
                    b.set(CaptureRequest.SENSOR_SENSITIVITY, clampIso(cam, manualIso))
                }
            }
            priority && priorityMetering != null -> {
                // Emulated priority: keep the exposure (t × ISO) metered when the
                // priority was set, and solve for the free value.
                val (t0, iso0) = priorityMetering
                val ev = t0.toDouble() * iso0
                val exp = manualExp ?: clampExp(cam, (ev / manualIso!!).toLong())
                val iso = manualIso ?: clampIso(cam, (ev / manualExp!!).toInt())
                manual(b, cam, exp, iso, previewMaxExposureNs)
            }
            else -> {
                val aeMode = when {
                    c.torch -> C2.AE_ON
                    !video && c.flash == FlashMode.AUTO && C2.AE_ON_AUTO_FLASH in cam.aeModes -> C2.AE_ON_AUTO_FLASH
                    !video && c.flash == FlashMode.ON && C2.AE_ON_ALWAYS_FLASH in cam.aeModes -> C2.AE_ON_ALWAYS_FLASH
                    else -> C2.AE_ON
                }
                check(NaturalPolicy.allowedAeMode(aeMode))
                b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, cam.aeCompRange.clamp(c.evIndex))
                b.set(CaptureRequest.CONTROL_AE_LOCK, c.aeLock)
            }
        }
        b.set(CaptureRequest.FLASH_MODE, if (c.torch) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
        c.fpsRange?.let { if (!fullManual) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(it.lower, it.upper)) }

        // Focus
        if (c.focusDiopters != null && cam.manualFocus) {
            b.set(CaptureRequest.CONTROL_AF_MODE, C2.AF_OFF)
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, c.focusDiopters.coerceIn(0f, cam.minFocusDistance))
        } else if (cam.hasAutoFocus) {
            val mode = when {
                video && C2.AF_CONTINUOUS_VIDEO in cam.afModes -> C2.AF_CONTINUOUS_VIDEO
                C2.AF_CONTINUOUS_PICTURE in cam.afModes -> C2.AF_CONTINUOUS_PICTURE
                C2.AF_AUTO in cam.afModes -> C2.AF_AUTO
                else -> null
            }
            mode?.let { b.set(CaptureRequest.CONTROL_AF_MODE, it) }
        }

        // Metering regions
        c.meteringRegion?.let { r ->
            val rect = MeteringRectangle(android.graphics.Rect(r.left, r.top, r.right, r.bottom), MeteringRectangle.METERING_WEIGHT_MAX - 1)
            if (cam.maxRegionsAe > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rect))
            if (cam.maxRegionsAf > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rect))
        }

        whiteBalance(b, cam, c, null, null)
    }

    /**
     * White balance. [lastGains]/[lastTransform] are the AWB result of a recent
     * frame, used for the warm/cool shift on cameras without CCT mode.
     */
    fun whiteBalance(
        b: CaptureRequest.Builder,
        cam: CameraInfo,
        c: Controls,
        lastGains: RggbChannelVector?,
        lastTransform: android.hardware.camera2.params.ColorSpaceTransform?,
    ) {
        val sdk = Build.VERSION.SDK_INT
        val cct = sdk >= 36 && C2.CC_CCT in cam.colorCorrectionModes
        when {
            c.kelvin != null && cct -> {
                b.set(CaptureRequest.CONTROL_AWB_MODE, C2.AWB_OFF)
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, C2.CC_CCT)
                b.set(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, c.kelvin)
                b.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, (c.tint * 50).toInt())
            }
            c.wbShift != 0f && lastGains != null && lastTransform != null && cam.manualPostProcessing && C2.AWB_OFF in cam.awbModes -> {
                val s = c.wbShift.coerceIn(-1f, 1f)
                val warm = 2.0.pow(0.35 * s).toFloat()
                b.set(CaptureRequest.CONTROL_AWB_MODE, C2.AWB_OFF)
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, C2.CC_TRANSFORM_MATRIX)
                b.set(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(lastGains.red * warm, lastGains.greenEven, lastGains.greenOdd, lastGains.blue / warm),
                )
                b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, lastTransform)
            }
            else -> {
                val mode = if (c.awbMode in cam.awbModes) c.awbMode else C2.AWB_AUTO
                b.set(CaptureRequest.CONTROL_AWB_MODE, mode)
                b.set(CaptureRequest.CONTROL_AWB_LOCK, c.awbLock)
                if (cam.manualPostProcessing) b.set(CaptureRequest.COLOR_CORRECTION_MODE, C2.CC_FAST)
            }
        }
    }

    private fun manual(b: CaptureRequest.Builder, cam: CameraInfo, exposureNs: Long, iso: Int, previewMax: Long?) {
        var exp = clampExp(cam, exposureNs)
        var sens = clampIso(cam, iso)
        if (previewMax != null && exp > previewMax) {
            sens = clampIso(cam, (sens * (exp.toDouble() / previewMax)).toInt())
            exp = previewMax
        }
        b.set(CaptureRequest.CONTROL_AE_MODE, C2.AE_OFF)
        b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
        b.set(CaptureRequest.SENSOR_SENSITIVITY, sens)
        b.set(CaptureRequest.SENSOR_FRAME_DURATION, exp)
        b.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 100)
    }

    fun clampExp(cam: CameraInfo, ns: Long): Long = cam.exposureRangeNs?.clamp(ns) ?: ns
    fun clampIso(cam: CameraInfo, iso: Int): Int = cam.isoRange?.clamp(iso) ?: iso
}
