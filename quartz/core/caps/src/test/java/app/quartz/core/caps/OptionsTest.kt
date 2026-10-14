package app.quartz.core.caps

import app.quartz.core.caps.options.Aspect
import app.quartz.core.caps.options.BitrateLevel
import app.quartz.core.caps.options.Bitrates
import app.quartz.core.caps.options.Codec
import app.quartz.core.caps.options.PhotoOptions
import app.quartz.core.caps.options.VideoOptions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsTest {
    private val main = Fixtures.logicalTriple.camera("0")!!
    private val encoders = Fixtures.logicalTriple.encoders

    @Test
    fun videoGridOnlyHasWhatCameraAndEncoderBothDo() {
        val cells = VideoOptions.cells(main, encoders)
        val labels = cells.map { it.label }
        assertTrue("4K · 60" in labels)
        assertTrue("1080p · 24" in labels)
        // 25 and 50 fps aren't fixed AE ranges on this camera.
        assertFalse(cells.any { it.fps == 25 || it.fps == 50 })
        // Hardware encoders only; HEVC and H.264 both available.
        assertEquals(listOf(Codec.H264, Codec.HEVC), cells.first().codecs)
        assertEquals(listOf(Codec.HEVC), cells.first { it.label == "4K · 30" }.tenBitCodecs)
        // The telephoto's minimum frame duration caps it at 30 fps.
        val tele = Fixtures.logicalTriple.camera("4")!!
        assertFalse(VideoOptions.cells(tele, encoders).any { it.fps > 30 })
    }

    @Test
    fun nearestCellAndDefaults() {
        val tele = VideoOptions.cells(Fixtures.logicalTriple.camera("4")!!, encoders)
        val near = VideoOptions.nearest(tele, Size(3840, 2160), 60)!!
        assertEquals(Size(3840, 2160), near.size)
        assertEquals(30, near.fps)
        val d = VideoOptions.default(VideoOptions.cells(main, encoders))!!
        assertEquals(Size(1920, 1080), d.size)
        assertEquals(30, d.fps)
        assertEquals(Codec.HEVC, VideoOptions.defaultCodec(d))
    }

    @Test
    fun slowMotionFromHighSpeedConfigs() {
        val slow = VideoOptions.slowMotion(main, encoders)
        assertTrue(slow.any { it.fps == 240 && it.size == Size(1920, 1080) })
        assertTrue(VideoOptions.slowMotion(Fixtures.legacy.camera("0")!!, encoders).isEmpty())
    }

    @Test
    fun photoSizesAndAspects() {
        val sizes = PhotoOptions.sizes(main)
        assertEquals(Size(4032, 3024), sizes.first().size)
        assertEquals(listOf(Aspect.R4_3, Aspect.R16_9, Aspect.R1_1), PhotoOptions.aspects(main))
        assertEquals(Size(4000, 2250), PhotoOptions.default(main, Aspect.R16_9)!!.size)
    }

    @Test
    fun bitrates() {
        val std = Bitrates.standard(Size(3840, 2160), 30, Codec.HEVC)
        assertTrue(std in 30_000_000..40_000_000)
        assertEquals(200_000_000, Bitrates.resolve(BitrateLevel.MAX, 0f, Size(3840, 2160), 30, Codec.HEVC, 200_000_000))
        assertEquals(20_000_000, Bitrates.resolve(BitrateLevel.CUSTOM, 20f, Size(1920, 1080), 30, Codec.HEVC, 200_000_000))
    }

    @Test
    fun capsRoundTripThroughJson() {
        val json = Json { encodeDefaults = false }
        for (caps in Fixtures.all) {
            val back = json.decodeFromString(DeviceCaps.serializer(), json.encodeToString(DeviceCaps.serializer(), caps))
            assertEquals(caps, back)
            assertTrue(back.encoders.first().supports(1920, 1080, 30))
        }
    }
}
