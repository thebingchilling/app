package app.quartz.core.camera

import android.media.MediaActionSound
import android.os.Build

/** System camera sounds. Plays even when muted where the law requires it. */
class Sounds {
    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }
    var enabled = true

    private val required: Boolean
        get() = Build.VERSION.SDK_INT >= 33 && MediaActionSound.mustPlayShutterSound()

    fun shutter() = play(MediaActionSound.SHUTTER_CLICK)
    fun startRecording() = play(MediaActionSound.START_VIDEO_RECORDING)
    fun stopRecording() = play(MediaActionSound.STOP_VIDEO_RECORDING)

    private fun play(id: Int) {
        if (enabled || required) sound.play(id)
    }

    fun release() = sound.release()
}
