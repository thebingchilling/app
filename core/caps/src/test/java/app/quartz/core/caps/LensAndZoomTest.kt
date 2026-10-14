package app.quartz.core.caps

import app.quartz.core.caps.lens.LensMap
import app.quartz.core.caps.lens.MeteringMath
import app.quartz.core.caps.lens.ZoomMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensAndZoomTest {

    @Test
    fun logicalCameraLensesAndRatios() {
        val set = LensMap.build(Fixtures.logicalTriple, C2.FACING_BACK)!!
        assertEquals("0", set.primary.id)
        assertTrue(set.usesZoomRatio)
        assertEquals(3, set.lenses.size)
        assertEquals(0.5f, set.widest.ratio, 0.001f) // HAL range wins for the widest lens
        assertEquals(1f, set.main.ratio, 0.01f)
        assertEquals(18f / 6.9f, set.longest.ratio, 0.1f)
        // Physical lenses that can't be opened alone are streamed through the logical camera.
        assertEquals("0", set.longest.openId)
        assertEquals("4", set.longest.physicalId)
        assertEquals(listOf("0.5", "1×", "2", "2.5"), ZoomMath.pills(set, emptyList(), 10f).map { LensMap.ratioLabel(it) })
    }

    @Test
    fun separateCameraIdsWithoutZoomRatio() {
        val set = LensMap.build(Fixtures.separateIds, C2.FACING_BACK, sdk = 29)!!
        assertNull(set.zoomRatioRange)
        assertEquals(listOf("2", "0", "3"), set.lenses.map { it.openId })
        val t = ZoomMath.target(set, 3f, 10f)
        assertEquals("3", t.cameraId)
        assertNull(t.zoomRatio)
        assertTrue(t.cropFactor >= 1f)
        val wide = ZoomMath.target(set, set.widest.ratio, 10f)
        assertEquals("2", wide.cameraId)
    }

    @Test
    fun zoomRatioTargetsAndClamping() {
        val set = LensMap.build(Fixtures.logicalTriple, C2.FACING_BACK)!!
        val t = ZoomMath.target(set, 3f, 10f)
        assertEquals("0", t.cameraId)
        assertEquals(3f, t.zoomRatio!!, 0.001f)
        assertEquals(10f, ZoomMath.target(set, 50f, 10f).zoomRatio!!, 0.001f)
        assertEquals(0.5f, ZoomMath.target(set, 0.1f, 10f).zoomRatio!!, 0.001f)
        val locked = ZoomMath.target(set, 4f, 10f, lock = set.main)
        assertNull(locked.zoomRatio)
        assertEquals(4f, locked.cropFactor, 0.01f)
    }

    @Test
    fun legacyHasOneLens() {
        val set = LensMap.build(Fixtures.legacy, C2.FACING_BACK)!!
        assertEquals(1, set.lenses.size)
        assertEquals(listOf(1f, 2f), ZoomMath.pills(set, emptyList(), 10f))
        assertNotNull(LensMap.build(Fixtures.logicalTriple, C2.FACING_FRONT))
    }

    @Test
    fun cropRegionIsCentred() {
        val r = ZoomMath.cropRegion(Rect(0, 0, 4000, 3000), 2f)
        assertEquals(Rect(1000, 750, 3000, 2250), r)
    }

    @Test
    fun tapMapsIntoSensorCoordinates() {
        // Back camera, sensor 90°: top-right of the portrait view is the sensor's top-left.
        val (sx, sy) = MeteringMath.viewToSensor(1f, 0f, 90, front = false)
        assertEquals(0f, sx, 1e-6f)
        assertEquals(0f, sy, 1e-6f)
        val (cx, cy) = MeteringMath.viewToSensor(0.5f, 0.5f, 270, front = true)
        assertEquals(0.5f, cx, 1e-6f)
        assertEquals(0.5f, cy, 1e-6f)
        assertEquals(90, MeteringMath.captureRotation(90, 0, front = false))
        assertEquals(270, MeteringMath.captureRotation(270, 0, front = true))
        assertEquals(180, MeteringMath.captureRotation(90, 90, front = false))
    }

    @Test
    fun ratioLabels() {
        assertEquals("0.5", LensMap.ratioLabel(0.5f))
        assertEquals("0.6", LensMap.ratioLabel(0.6f))
        assertEquals("1×", LensMap.ratioLabel(1.0f))
        assertEquals("2", LensMap.ratioLabel(2f))
        assertEquals("5", LensMap.ratioLabel(4.9f))
        assertEquals("3.5", LensMap.ratioLabel(3.5f))
    }
}
