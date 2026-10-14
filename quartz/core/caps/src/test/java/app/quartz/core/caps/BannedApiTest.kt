package app.quartz.core.caps

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard for the "never enhance" contract (plan §13.1): fails the build if any
 * module's source uses an API that exists only to enhance images.
 */
class BannedApiTest {
    private val banned = listOf(
        "CameraExtensionCharacteristics",
        "CameraExtensionSession",
        "ExtensionSessionConfiguration",
        "camera.extensions",
        "ImageFormat.JPEG_R",
        "NOISE_REDUCTION_MODE_HIGH_QUALITY",
        "NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG",
        "EDGE_MODE_HIGH_QUALITY",
        "EDGE_MODE_ZERO_SHUTTER_LAG",
        "CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY",
        "TEMPLATE_ZERO_SHUTTER_LAG",
        "CONTROL_SCENE_MODE_HDR",
        "CONTROL_SCENE_MODE_NIGHT",
        "CONTROL_EXTENDED_SCENE_MODE_BOKEH",
    )

    @Test
    fun noEnhancementApisInSource() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: error("repo root not found")
        val hits = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "test" }
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> banned.firstOrNull { line.contains(it) }?.let { "${f.relativeTo(root)}:${i + 1}: $it" } } }
            .toList()
        assertTrue("Enhancement APIs found:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
