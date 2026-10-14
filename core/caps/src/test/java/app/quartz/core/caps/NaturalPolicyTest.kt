package app.quartz.core.caps

import app.quartz.core.caps.natural.NaturalPolicy
import app.quartz.core.caps.natural.NaturalPrefs
import app.quartz.core.caps.natural.NaturalReporter
import app.quartz.core.caps.natural.Tonemap
import app.quartz.core.caps.natural.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalPolicyTest {
    private val prefs = NaturalPrefs()

    @Test
    fun everyFixtureGetsTheLowestEnhancementItLists() {
        for (caps in Fixtures.all) for (cam in caps.cameras) {
            val r = NaturalPolicy.resolve(cam, prefs, caps.sdk)
            // Never a HIGH_QUALITY / ZSL noise reduction or sharpening.
            assertTrue(r.noiseReduction == null || r.noiseReduction in listOf(C2.NR_OFF, C2.NR_MINIMAL, C2.NR_FAST))
            if (C2.NR_OFF in cam.nrModes) assertEquals(C2.NR_OFF, r.noiseReduction)
            else if (C2.NR_MINIMAL in cam.nrModes) assertEquals(C2.NR_MINIMAL, r.noiseReduction)
            assertTrue(r.edge == null || r.edge in listOf(C2.EDGE_OFF, C2.EDGE_FAST))
            if (C2.EDGE_OFF in cam.edgeModes) assertEquals(C2.EDGE_OFF, r.edge)
            assertEquals(C2.SCENE_DISABLED, r.sceneMode)
            assertEquals(C2.EFFECT_OFF, r.effectMode)
            assertEquals(C2.VSTAB_OFF, r.videoStabilization)
        }
    }

    @Test
    fun neverPicksAValueTheCameraDoesNotList() {
        for (caps in Fixtures.all) for (cam in caps.cameras) {
            val r = NaturalPolicy.resolve(cam, prefs, caps.sdk, video = true)
            r.noiseReduction?.let { assertTrue(it in cam.nrModes) }
            r.edge?.let { assertTrue(it in cam.edgeModes) }
            r.hotPixel?.let { assertTrue(it in cam.hotPixelModes) }
            r.shading?.let { assertTrue(it in cam.shadingModes) }
            r.aberration?.let { assertTrue(it in cam.aberrationModes) }
            r.distortion?.let { assertTrue(it in cam.distortionModes) }
        }
    }

    @Test
    fun srgbToneCurveWhereAvailable() {
        val main = Fixtures.logicalTriple.camera("0")!!
        assertEquals(Tonemap.PresetSrgb, NaturalPolicy.resolve(main, prefs, 35).tonemap)
        val curveOnly = main.copy(tonemapModes = listOf(C2.TONEMAP_CONTRAST_CURVE, C2.TONEMAP_FAST), maxTonemapPoints = 32)
        val t = NaturalPolicy.resolve(curveOnly, prefs, 35).tonemap
        assertTrue(t is Tonemap.Curve)
        val pts = (t as Tonemap.Curve).points
        assertEquals(64, pts.size)
        assertEquals(0f, pts[0], 0f)
        assertEquals(1f, pts[pts.size - 2], 1e-6f)
        assertEquals(1f, pts[pts.size - 1], 1e-4f)
        // Monotonic, and above the identity line (sRGB brightens mid-tones).
        for (i in 1 until pts.size / 2) {
            assertTrue(pts[i * 2] > pts[i * 2 - 2])
            assertTrue(pts[i * 2 + 1] >= pts[i * 2])
        }
        val legacy = Fixtures.legacy.camera("0")!!
        assertEquals(Tonemap.Device(null), NaturalPolicy.resolve(legacy, prefs, 27).tonemap)
    }

    @Test
    fun lensCorrectionsFollowThePreference() {
        val cam = Fixtures.logicalTriple.camera("0")!!
        val on = NaturalPolicy.resolve(cam, NaturalPrefs(lensCorrections = true), 35)
        val off = NaturalPolicy.resolve(cam, NaturalPrefs(lensCorrections = false), 35)
        assertEquals(C2.FAST, on.shading)
        assertEquals(C2.FAST, on.distortion)
        assertEquals(C2.OFF, off.shading)
        assertEquals(C2.OFF, off.aberration)
        assertEquals(C2.OFF, off.distortion)
        // Distortion correction key doesn't exist before API 28.
        assertNull(NaturalPolicy.resolve(cam, prefs, 27).distortion)
    }

    @Test
    fun stabilisationOnlyWhenOptedInForVideo() {
        val cam = Fixtures.logicalTriple.camera("0")!!
        assertEquals(C2.VSTAB_OFF, NaturalPolicy.resolve(cam, NaturalPrefs(videoStabilization = true), 35, video = false).videoStabilization)
        assertEquals(C2.VSTAB_ON, NaturalPolicy.resolve(cam, NaturalPrefs(videoStabilization = true), 35, video = true).videoStabilization)
        assertEquals(C2.OIS_ON, NaturalPolicy.resolve(cam, prefs, 35).ois)
    }

    @Test
    fun lowLightBoostIsNeverAllowed() {
        assertTrue(!NaturalPolicy.allowedAeMode(C2.AE_ON_LOW_LIGHT_BOOST))
        assertTrue(NaturalPolicy.allowedAeMode(C2.AE_ON))
    }

    @Test
    fun reportVerdicts() {
        assertEquals(Verdict.NATURAL, NaturalReporter.build(Fixtures.logicalTriple.camera("0")!!, prefs, 35).verdict)
        assertEquals(Verdict.PROCESSED, NaturalReporter.build(Fixtures.separateIds.camera("0")!!, prefs, 29).verdict)
        val observed = mapOf(NaturalReporter.NOISE_REDUCTION to C2.NR_HIGH_QUALITY)
        assertNotEquals(Verdict.NATURAL, NaturalReporter.build(Fixtures.logicalTriple.camera("0")!!, prefs, 35, observed).verdict)
    }
}
