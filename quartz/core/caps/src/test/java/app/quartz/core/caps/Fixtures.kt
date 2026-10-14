package app.quartz.core.caps

/**
 * Hand-written device fixtures (synthetic = true) modelled on common camera
 * layouts. Real probe dumps go in alongside these as they are collected
 * (plan §13.1): Settings → About this device → Export capability report.
 */
object Fixtures {
    private fun sizes(vararg s: Pair<Int, Int>, frameNs: Long = 33_333_333L) = s.map { StreamSize(it.first, it.second, frameNs) }

    private val fullRanges = listOf(FpsRange(15, 30), FpsRange(30, 30), FpsRange(24, 24), FpsRange(60, 60), FpsRange(15, 60))

    private fun back(
        id: String,
        focal: Float,
        sensorW: Float,
        sensorH: Float,
        openable: Boolean = true,
        parent: String? = null,
        level: Int = C2.LEVEL_3,
        extraCaps: List<Int> = emptyList(),
        nr: List<Int> = listOf(C2.NR_OFF, C2.NR_FAST, C2.NR_HIGH_QUALITY, C2.NR_MINIMAL, C2.NR_ZERO_SHUTTER_LAG),
        edge: List<Int> = listOf(C2.EDGE_OFF, C2.EDGE_FAST, C2.EDGE_HIGH_QUALITY, C2.EDGE_ZERO_SHUTTER_LAG),
        tonemap: List<Int> = listOf(C2.TONEMAP_CONTRAST_CURVE, C2.TONEMAP_FAST, C2.TONEMAP_HIGH_QUALITY, C2.TONEMAP_GAMMA_VALUE, C2.TONEMAP_PRESET_CURVE),
        video: List<StreamSize> = sizes(3840 to 2160, 1920 to 1080, 1280 to 720, frameNs = 16_666_666L),
        jpeg: List<StreamSize> = sizes(4032 to 3024, 4000 to 2250, 3024 to 3024, 1920 to 1080),
        zoom: FloatSpan? = null,
        physical: List<String> = emptyList(),
    ) = CameraInfo(
        id = id,
        openable = openable,
        logicalParent = parent,
        physicalIds = physical,
        facing = C2.FACING_BACK,
        hardwareLevel = level,
        capabilities = listOf(C2.CAP_BACKWARD_COMPATIBLE, C2.CAP_MANUAL_SENSOR, C2.CAP_MANUAL_POST_PROCESSING, C2.CAP_RAW) + extraCaps,
        sensorOrientation = 90,
        focalLengths = listOf(focal),
        apertures = listOf(1.8f),
        physicalSizeMm = SizeF(sensorW, sensorH),
        pixelArray = Size(4032, 3024),
        activeArray = Rect(0, 0, 4032, 3024),
        oisModes = listOf(C2.OIS_OFF, C2.OIS_ON),
        isoRange = IntSpan(50, 6400),
        exposureRangeNs = LongSpan(10_000, 1_000_000_000),
        minFocusDistance = 10f,
        focusCalibration = C2.FOCUS_CALIBRATED,
        aeModes = listOf(C2.AE_OFF, C2.AE_ON, C2.AE_ON_AUTO_FLASH, C2.AE_ON_ALWAYS_FLASH),
        afModes = listOf(C2.AF_OFF, C2.AF_AUTO, C2.AF_CONTINUOUS_VIDEO, C2.AF_CONTINUOUS_PICTURE),
        awbModes = listOf(C2.AWB_OFF, C2.AWB_AUTO, C2.AWB_INCANDESCENT, C2.AWB_DAYLIGHT),
        nrModes = nr,
        edgeModes = edge,
        tonemapModes = tonemap,
        hotPixelModes = listOf(C2.OFF, C2.FAST, C2.HIGH_QUALITY),
        shadingModes = listOf(C2.OFF, C2.FAST, C2.HIGH_QUALITY),
        aberrationModes = listOf(C2.OFF, C2.FAST, C2.HIGH_QUALITY),
        distortionModes = listOf(C2.OFF, C2.FAST),
        videoStabModes = listOf(C2.VSTAB_OFF, C2.VSTAB_ON),
        aeCompRange = IntSpan(-12, 12),
        aeCompStep = 1f / 6,
        maxTonemapPoints = 128,
        flash = true,
        maxDigitalZoom = 8f,
        zoomRatioRange = zoom,
        timestampSource = C2.TIMESTAMP_REALTIME,
        maxRegionsAe = 1,
        maxRegionsAf = 1,
        outputs = mapOf(
            Outputs.JPEG to jpeg,
            Outputs.YUV to sizes(4032 to 3024, 1920 to 1080, 640 to 480),
            Outputs.RAW to sizes(4032 to 3024),
            Outputs.PRIVATE to sizes(4032 to 3024, 1920 to 1080),
            Outputs.VIDEO to video,
            Outputs.PREVIEW to sizes(1920 to 1440, 1920 to 1080, 1440 to 1080, 1280 to 720),
        ),
        highSpeed = listOf(HighSpeedConfig(1920, 1080, listOf(FpsRange(30, 240), FpsRange(240, 240))), HighSpeedConfig(1280, 720, listOf(FpsRange(30, 240), FpsRange(240, 240)))),
        aeFpsRanges = fullRanges,
    )

    private val encoders = listOf(
        EncoderInfo(
            mime = "video/hevc", name = "c2.qti.hevc.encoder", hardware = true, maxBitrate = 200_000_000, main10 = true,
            support = listOf(3840 to 2160, 1920 to 1080, 1280 to 720).flatMap { (w, h) -> listOf(24, 30, 60, 240).map { EncoderInfo.key(w, h, it) } },
        ),
        EncoderInfo(
            mime = "video/avc", name = "c2.qti.avc.encoder", hardware = true, maxBitrate = 160_000_000,
            support = listOf(3840 to 2160, 1920 to 1080, 1280 to 720).flatMap { (w, h) -> listOf(24, 30, 60, 240).map { EncoderInfo.key(w, h, it) } },
        ),
        EncoderInfo(
            mime = "video/avc", name = "c2.android.avc.encoder", hardware = false, maxBitrate = 12_000_000,
            support = listOf(EncoderInfo.key(1280, 720, 30)),
        ),
    )

    /** Pixel-like: one logical back camera ("0") over ultrawide, main, 5× telephoto, with ZOOM_RATIO. */
    val logicalTriple = DeviceCaps(
        fingerprint = "fixture/logical", manufacturer = "Fixture", model = "Logical triple", sdk = 35, synthetic = true,
        cameras = listOf(
            back("0", 6.9f, 9.8f, 7.4f, zoom = FloatSpan(0.5f, 30f), physical = listOf("2", "3", "4"), extraCaps = listOf(C2.CAP_LOGICAL_MULTI_CAMERA, C2.CAP_DYNAMIC_RANGE_TEN_BIT, C2.CAP_CONSTRAINED_HIGH_SPEED_VIDEO)).copy(dynamicRangeProfiles = listOf(C2.DR_STANDARD, C2.DR_HLG10)),
            back("2", 6.9f, 9.8f, 7.4f, openable = false, parent = "0"),
            back("3", 2.0f, 5.6f, 4.2f, openable = false, parent = "0"),
            back("4", 19.7f, 5.6f, 4.2f, openable = false, parent = "0", video = sizes(3840 to 2160, 1920 to 1080, frameNs = 33_333_333L)),
            back("1", 2.7f, 4.6f, 3.4f).copy(facing = C2.FACING_FRONT, sensorOrientation = 270),
        ),
        encoders = encoders,
    )

    /** Older phone: ultrawide and telephoto listed as separate camera IDs, no ZOOM_RATIO, no NR OFF. */
    val separateIds = DeviceCaps(
        fingerprint = "fixture/separate", manufacturer = "Fixture", model = "Separate IDs", sdk = 29, synthetic = true,
        cameras = listOf(
            back("0", 4.7f, 6.4f, 4.8f, level = C2.LEVEL_FULL, nr = listOf(C2.NR_FAST, C2.NR_HIGH_QUALITY, C2.NR_MINIMAL), edge = listOf(C2.EDGE_FAST, C2.EDGE_HIGH_QUALITY), tonemap = listOf(C2.TONEMAP_FAST, C2.TONEMAP_HIGH_QUALITY)),
            back("1", 2.7f, 4.6f, 3.4f).copy(facing = C2.FACING_FRONT, sensorOrientation = 270),
            back("2", 2.2f, 5.6f, 4.2f, level = C2.LEVEL_LIMITED),
            back("3", 6.0f, 4.0f, 3.0f, level = C2.LEVEL_LIMITED),
        ),
        encoders = encoders,
    )

    /** Budget LEGACY phone: one back camera, nothing manual, no processing controls. */
    val legacy = DeviceCaps(
        fingerprint = "fixture/legacy", manufacturer = "Fixture", model = "Legacy", sdk = 27, synthetic = true,
        cameras = listOf(
            back("0", 3.5f, 4.6f, 3.4f, level = C2.LEVEL_LEGACY, nr = listOf(C2.NR_FAST), edge = listOf(C2.EDGE_FAST), tonemap = emptyList())
                .copy(capabilities = listOf(C2.CAP_BACKWARD_COMPATIBLE), aeFpsRanges = listOf(FpsRange(15, 30), FpsRange(30, 30))),
        ),
        encoders = encoders,
    )

    val all = listOf(logicalTriple, separateIds, legacy)
}
