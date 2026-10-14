package app.quartz.core.camera.video

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import app.quartz.core.camera.AudioSpec

/**
 * Microphone → AAC on its own thread. Uses the UNPROCESSED source when the
 * device supports it (no AGC, noise suppression or wind filter), else
 * CAMCORDER.
 */
class AudioEncoder(private val spec: AudioSpec, private val unprocessedSupported: Boolean) {
    private val rate = 48_000
    private val channels = if (spec.stereo) 2 else 1
    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    var sink: Mp4Sink? = null

    /** Creates the microphone and encoder. Fails if RECORD_AUDIO isn't granted. */
    @SuppressLint("MissingPermission")
    fun prepare() {
        val channelMask = if (spec.stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val source = if (spec.unprocessed && unprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.CAMCORDER
        val r = AudioRecord(source, rate, channelMask, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, 16384))
        check(r.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, spec.bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        record = r
        codec = c
    }

    fun start() {
        val r = record ?: return
        val c = codec ?: return
        running = true
        c.start()
        r.startRecording()
        thread = Thread({ loop(r, c) }, "QuartzAudio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(3000)
        thread = null
    }

    fun release() {
        stop()
        runCatching { record?.release() }
        runCatching { codec?.release() }
        record = null
        codec = null
    }

    private fun loop(r: AudioRecord, c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        val bytesPerFrame = 2 * channels
        var totalFrames = 0L
        var startNs = -1L
        try {
            while (true) {
                val stopping = !running
                val inIndex = c.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = c.getInputBuffer(inIndex)!!
                    buf.clear()
                    val read = if (stopping) 0 else r.read(buf, minOf(buf.remaining(), 4096) / bytesPerFrame * bytesPerFrame)
                    if (startNs < 0 && read > 0) startNs = firstFrameTimeNs(r, read / bytesPerFrame)
                    val ptsUs = if (startNs < 0) 0 else (startNs + totalFrames * 1_000_000_000L / rate) / 1000
                    if (read > 0) totalFrames += read / bytesPerFrame
                    val flags = if (stopping) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    c.queueInputBuffer(inIndex, 0, maxOf(read, 0), ptsUs, flags)
                    if (stopping) {
                        drain(c, info, untilEos = true)
                        break
                    }
                }
                drain(c, info, untilEos = false)
            }
        } catch (e: Exception) {
            Log.w("QuartzAudio", "audio stopped", e)
        } finally {
            runCatching { r.stop() }
            runCatching { c.stop() }
        }
    }

    private fun drain(c: MediaCodec, info: MediaCodec.BufferInfo, untilEos: Boolean) {
        var waits = 0
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> sink?.addTrack(Track.AUDIO, c.outputFormat)
                outIndex >= 0 -> {
                    sink?.write(Track.AUDIO, c.getOutputBuffer(outIndex)!!, info)
                    c.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                !untilEos || ++waits > 200 -> return
            }
        }
    }

    /** CLOCK_MONOTONIC time of the first frame read. */
    private fun firstFrameTimeNs(r: AudioRecord, framesJustRead: Int): Long {
        val ts = AudioTimestamp()
        if (r.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
            return ts.nanoTime - ts.framePosition * 1_000_000_000L / rate
        }
        return System.nanoTime() - framesJustRead * 1_000_000_000L / rate
    }
}
