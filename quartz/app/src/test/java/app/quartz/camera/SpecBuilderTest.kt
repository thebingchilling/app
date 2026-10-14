package app.quartz.camera

import app.quartz.core.camera.SessionKind
import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.DeviceCaps
import app.quartz.core.caps.EncoderInfo
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.HighSpeedConfig
import app.quartz.core.caps.IntSpan
import app.quartz.core.caps.LongSpan
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Rect
import app.quartz.core.caps.Size
import app.quartz.core.caps.SizeF
import app.quartz.core.caps.StreamSize
import app.quartz.core.caps.lens.LensMap
import app.quartz.core.settings.Mode
import app.quartz.core.settings.PhotoConfig
import app.quartz.core.settings.PhotoFormat
import app.quartz.core.settings.QuartzSettings
import app.quartz.core.settings.VideoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecBuilderTest {
    private fun cam(id: String, focal: Float, facing: Int = C2.FACING_BACK) = CameraInfo(
        id = id, facing = facing, hardwareLevel = C2.LEVEL_FULL,
        capabilities = listOf(C2.CAP_BACKWARD_COMPATIBLE, C2.CAP_MANUAL_SENSOR, C2.CAP_RAW, C2.CAP_CONSTRAINED_HIGH_SPEED_VIDEO),
        focalLengths = listOf(focal), physicalSizeMm = SizeF(6.4f, 4.8f), pixelArray = Size(4000, 3000), activeArray = Rect(0, 0, 4000, 3000),
        isoRange = IntSpan(50, 3200), exposureRangeNs = LongSpan(10_000, 2_000_000_000),
        outputs = mapOf(
            Outputs.JPEG to listOf(StreamSize(4000, 3000), StreamSize(4000, 2250)),
            Outputs.RAW to listOf(StreamSize(4000, 3000)),
            Outputs.YUV to listOf(StreamSize(4000, 3000), StreamSize(640, 480)),
            Outputs.VIDEO to listOf(StreamSize(3840, 2160, 16_666_666), StreamSize(1920, 1080, 16_666_666)),
            Outputs.PREVIEW to listOf(StreamSize(1440, 1080), StreamSize(1920, 1080), StreamSize(2560, 1920)),
        ),
        highSpeed = listOf(HighSpeedConfig(1920, 1080, listOf(FpsRange(30, 240), FpsRange(240, 240)))),
        aeFpsRanges = listOf(FpsRange(30, 30), FpsRange(60, 60), FpsRange(15, 30)),
    )

    private val caps = DeviceCaps(
        fingerprint = "t", manufacturer = "t", model = "t", sdk = 29, synthetic = true,
        cameras = listOf(cam("0", 4.7f), cam("2", 2.2f), cam("1", 3f, C2.FACING_FRONT)),
        encoders = listOf(
            EncoderInfo("video/hevc", "hw", true, 100_000_000, support = listOf("3840x2160@30", "3840x2160@60", "1920x1080@30", "1920x1080@60", "1920x1080@240")),
        ),
    )
    private val set = LensMap.build(caps, C2.FACING_BACK, 29)!!

    @Test
    fun photoUsesDefaultSizeAndMatchingPreview() {
        val sel = SpecBuilder.build(caps, QuartzSettings(), set, 1f)!!
        assertEquals(SessionKind.PHOTO, sel.spec.kind)
        assertEquals(Size(4000, 3000), sel.spec.still!!.size)
        // Largest 4:3 viewfinder stream no bigger than 1920×1440.
        assertEquals(Size(1440, 1080), sel.spec.previewSize)
        assertNull(sel.adaptedNote)
    }

    @Test
    fun zoomBelowOneSwitchesToTheUltrawideCamera() {
        val sel = SpecBuilder.build(caps, QuartzSettings(), set, set.widest.ratio)!!
        assertEquals("2", sel.spec.cameraId)
        assertEquals("2", sel.settingsKey)
    }

    @Test
    fun videoAndAdaptation() {
        val s = QuartzSettings(mode = Mode.VIDEO, video = mapOf("0" to VideoConfig(3840, 2160, 60)))
        val sel = SpecBuilder.build(caps, s, set, 1f)!!
        assertEquals(Size(3840, 2160), sel.spec.video!!.size)
        assertEquals(60, sel.spec.video!!.fps)
        assertEquals(Size(1920, 1080), sel.spec.previewSize)
        assertEquals(FpsRange(60, 60), sel.spec.previewFps)
    }

    @Test
    fun slowMotionPreviewMatchesVideoSize() {
        val sel = SpecBuilder.build(caps, QuartzSettings(mode = Mode.SLOW_MOTION), set, 1f)!!
        assertEquals(SessionKind.HIGH_SPEED, sel.spec.kind)
        assertEquals(sel.spec.video!!.size, sel.spec.previewSize)
        assertEquals(240, sel.spec.video!!.fps)
    }

    @Test
    fun rawRequestedWhereUnavailableFallsBackToJpeg() {
        val noRaw = caps.copy(cameras = caps.cameras.map { it.copy(capabilities = listOf(C2.CAP_BACKWARD_COMPATIBLE)) })
        val s = QuartzSettings(photo = mapOf("0" to PhotoConfig(format = PhotoFormat.RAW_JPEG)))
        val sel = SpecBuilder.build(noRaw, s, LensMap.build(noRaw, C2.FACING_BACK, 29)!!, 1f)!!
        assertEquals(PhotoFormat.JPEG, sel.spec.still!!.format)
        // Long exposure needs manual sensor control, so it falls back to Photo.
        val le = SpecBuilder.build(noRaw, s.copy(mode = Mode.LONG_EXPOSURE), LensMap.build(noRaw, C2.FACING_BACK, 29)!!, 1f)!!
        assertEquals(Mode.PHOTO, le.mode)
    }

    @Test
    fun timelapseHasInterval() {
        val sel = SpecBuilder.build(caps, QuartzSettings(mode = Mode.TIMELAPSE), set, 1f)
        assertNotNull(sel)
        assertTrue(sel!!.spec.timelapse!!.intervalMs > 0)
    }
}
