package app.quartz.core.caps

/**
 * Camera2 constant values, copied from android.hardware.camera2.CameraMetadata
 * and android.graphics.ImageFormat so the pure-Kotlin logic in this module
 * (policy, lenses, options) can be unit tested on the JVM without android.jar.
 * Values are part of the public, stable Android API.
 */
object C2 {
    // INFO_SUPPORTED_HARDWARE_LEVEL
    const val LEVEL_LIMITED = 0
    const val LEVEL_FULL = 1
    const val LEVEL_LEGACY = 2
    const val LEVEL_3 = 3
    const val LEVEL_EXTERNAL = 4

    // LENS_FACING
    const val FACING_FRONT = 0
    const val FACING_BACK = 1
    const val FACING_EXTERNAL = 2

    // REQUEST_AVAILABLE_CAPABILITIES
    const val CAP_BACKWARD_COMPATIBLE = 0
    const val CAP_MANUAL_SENSOR = 1
    const val CAP_MANUAL_POST_PROCESSING = 2
    const val CAP_RAW = 3
    const val CAP_READ_SENSOR_SETTINGS = 5
    const val CAP_BURST_CAPTURE = 6
    const val CAP_DEPTH_OUTPUT = 8
    const val CAP_CONSTRAINED_HIGH_SPEED_VIDEO = 9
    const val CAP_LOGICAL_MULTI_CAMERA = 11
    const val CAP_MONOCHROME = 12
    const val CAP_ULTRA_HIGH_RESOLUTION_SENSOR = 16
    const val CAP_DYNAMIC_RANGE_TEN_BIT = 18

    // NOISE_REDUCTION_MODE
    const val NR_OFF = 0
    const val NR_FAST = 1
    const val NR_HIGH_QUALITY = 2
    const val NR_MINIMAL = 3
    const val NR_ZERO_SHUTTER_LAG = 4

    // EDGE_MODE
    const val EDGE_OFF = 0
    const val EDGE_FAST = 1
    const val EDGE_HIGH_QUALITY = 2
    const val EDGE_ZERO_SHUTTER_LAG = 3

    // HOT_PIXEL_MODE, SHADING_MODE, COLOR_CORRECTION_ABERRATION_MODE, DISTORTION_CORRECTION_MODE
    const val OFF = 0
    const val FAST = 1
    const val HIGH_QUALITY = 2

    // TONEMAP_MODE
    const val TONEMAP_CONTRAST_CURVE = 0
    const val TONEMAP_FAST = 1
    const val TONEMAP_HIGH_QUALITY = 2
    const val TONEMAP_GAMMA_VALUE = 3
    const val TONEMAP_PRESET_CURVE = 4
    const val TONEMAP_PRESET_CURVE_SRGB = 0

    // CONTROL_VIDEO_STABILIZATION_MODE
    const val VSTAB_OFF = 0
    const val VSTAB_ON = 1
    const val VSTAB_PREVIEW = 2

    // LENS_OPTICAL_STABILIZATION_MODE
    const val OIS_OFF = 0
    const val OIS_ON = 1

    // CONTROL_AE_MODE
    const val AE_OFF = 0
    const val AE_ON = 1
    const val AE_ON_AUTO_FLASH = 2
    const val AE_ON_ALWAYS_FLASH = 3
    const val AE_ON_AUTO_FLASH_REDEYE = 4
    const val AE_ON_EXTERNAL_FLASH = 5
    const val AE_ON_LOW_LIGHT_BOOST = 6

    // CONTROL_AF_MODE
    const val AF_OFF = 0
    const val AF_AUTO = 1
    const val AF_MACRO = 2
    const val AF_CONTINUOUS_VIDEO = 3
    const val AF_CONTINUOUS_PICTURE = 4
    const val AF_EDOF = 5

    // CONTROL_AWB_MODE
    const val AWB_OFF = 0
    const val AWB_AUTO = 1
    const val AWB_INCANDESCENT = 2
    const val AWB_FLUORESCENT = 3
    const val AWB_WARM_FLUORESCENT = 4
    const val AWB_DAYLIGHT = 5
    const val AWB_CLOUDY = 6
    const val AWB_TWILIGHT = 7
    const val AWB_SHADE = 8

    // COLOR_CORRECTION_MODE
    const val CC_TRANSFORM_MATRIX = 0
    const val CC_FAST = 1
    const val CC_HIGH_QUALITY = 2
    const val CC_CCT = 3

    // CONTROL_AE_PRIORITY_MODE (API 36)
    const val AE_PRIORITY_OFF = 0
    const val AE_PRIORITY_SENSITIVITY = 1
    const val AE_PRIORITY_EXPOSURE_TIME = 2

    // LENS_INFO_FOCUS_DISTANCE_CALIBRATION
    const val FOCUS_UNCALIBRATED = 0
    const val FOCUS_APPROXIMATE = 1
    const val FOCUS_CALIBRATED = 2

    // SENSOR_INFO_TIMESTAMP_SOURCE
    const val TIMESTAMP_UNKNOWN = 0
    const val TIMESTAMP_REALTIME = 1

    // CONTROL_SCENE_MODE / EFFECT / EXTENDED_SCENE / FACE_DETECT
    const val SCENE_DISABLED = 0
    const val EFFECT_OFF = 0
    const val EXTENDED_SCENE_DISABLED = 0
    const val FACE_DETECT_OFF = 0

    // DynamicRangeProfiles
    const val DR_STANDARD = 1L
    const val DR_HLG10 = 2L
    const val DR_HDR10 = 4L

    // ImageFormat
    const val FORMAT_JPEG = 0x100
    const val FORMAT_YUV_420_888 = 0x23
    const val FORMAT_RAW_SENSOR = 0x20
    const val FORMAT_PRIVATE = 0x22
    const val FORMAT_HEIC = 0x48454946
    const val FORMAT_JPEG_R = 0x1005
}
