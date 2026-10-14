package app.quartz.core.settings

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.EncoderInfo
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import app.quartz.core.caps.StreamSize
import app.quartz.core.caps.options.Aspect
import app.quartz.core.caps.options.Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverTest {
    private val cam = CameraInfo(
        id = "0", facing = C2.FACING_BACK, hardwareLevel = C2.LEVEL_FULL,
        capabilities = listOf(C2.CAP_BACKWARD_COMPATIBLE),
        outputs = mapOf(
            Outputs.JPEG to listOf(StreamSize(4000, 3000), StreamSize(4000, 2250)),
            Outputs.VIDEO to listOf(StreamSize(3840, 2160, 33_333_333), StreamSize(1920, 1080, 16_666_666)),
        ),
        aeFpsRanges = listOf(FpsRange(30, 30), FpsRange(60, 60)),
    )
    private val enc = listOf(
        EncoderInfo("video/avc", "hw.avc", true, 100_000_000, support = listOf("3840x2160@30", "1920x1080@30", "1920x1080@60")),
    )

    @Test
    fun photoFallsBackWhenRawOrSizeUnavailable() {
        val r = Resolver.photo(cam, PhotoConfig(4032, 3024, format = PhotoFormat.RAW_JPEG), Aspect.R4_3)!!
        assertEquals(PhotoFormat.JPEG, r.format)
        assertEquals(Size(4000, 3000), r.size)
        assertTrue(r.adapted)
        val ok = Resolver.photo(cam, PhotoConfig(4000, 2250), Aspect.R16_9)!!
        assertFalse(ok.adapted)
    }

    @Test
    fun videoAdaptsToNearestCell() {
        val r = Resolver.video(cam, enc, VideoConfig(3840, 2160, 60, codec = Codec.HEVC))!!
        assertEquals(Size(3840, 2160), r.cell.size)
        assertEquals(30, r.cell.fps)
        assertEquals(Codec.H264, r.codec)
        assertEquals(DynamicRange.SDR, r.dynamicRange)
        assertTrue(r.adapted)
        val d = Resolver.video(cam, enc, VideoConfig())!!
        assertEquals(Size(1920, 1080), d.cell.size)
        assertFalse(d.adapted)
    }
}
