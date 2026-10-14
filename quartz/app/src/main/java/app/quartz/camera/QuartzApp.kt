package app.quartz.camera

import android.app.Application
import app.quartz.core.camera.CameraEngine
import app.quartz.core.caps.probe.CapabilityProbe
import app.quartz.core.media.MediaWriter
import app.quartz.core.settings.SettingsRepository

/** Manual dependency graph: one of each for the whole app. */
class Graph(app: Application) {
    val settings = SettingsRepository(app)
    val probe = CapabilityProbe(app)
    val media = MediaWriter(app).apply { softwareVersion = BuildConfig.VERSION_NAME }
    val engine = CameraEngine(app, media)
}

class QuartzApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}
