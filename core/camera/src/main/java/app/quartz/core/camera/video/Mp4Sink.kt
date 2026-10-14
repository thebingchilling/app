package app.quartz.core.camera.video

import android.location.Location
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

enum class Track { VIDEO, AUDIO }

/** How presentation times are written. */
sealed interface Timing {
    /** Real time: sensor timestamps, audio aligned, pauses cut out. */
    data object Real : Timing

    /** Frames spaced 1/[fps] apart regardless of capture time (slow motion "conform"). */
    data class Fixed(val fps: Int) : Timing

    /** Timestamps are already the playback timeline (time-lapse renderer). */
    data object AsIs : Timing
}

/**
 * MediaMuxer wrapper: waits for every expected track's format, buffers early
 * samples, maps timestamps onto one timeline, drops paused spans, and keeps
 * each track's timestamps strictly increasing.
 */
class Mp4Sink(
    fd: FileDescriptor,
    private val expectedTracks: Set<Track>,
    private val timing: Timing,
    orientationHint: Int,
    location: Location?,
    /** Added to audio timestamps to put them on the camera's clock (µs). */
    private val audioOffsetUs: Long,
) {
    private val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
        setOrientationHint(orientationHint)
        location?.let { setLocation(it.latitude.toFloat(), it.longitude.toFloat()) }
    }
    private val trackIndex = mutableMapOf<Track, Int>()
    private val pending = ArrayDeque<Triple<Track, ByteArray, MediaCodec.BufferInfo>>()
    private val lastPts = mutableMapOf<Track, Long>()
    private var started = false
    private var baseUs = -1L
    private var frames = 0L
    var samplesWritten = 0L
        private set

    // Pause bookkeeping (camera clock, µs)
    private var paused = false
    private var pauseStartUs = -1L
    private var resumePending = false
    private var pausedTotalUs = 0L
    private var lastVideoRawUs = -1L
    private var frameDurUs = 33_333L

    @Synchronized
    fun setFrameRate(fps: Int) {
        frameDurUs = 1_000_000L / fps.coerceAtLeast(1)
    }

    @Synchronized
    fun addTrack(track: Track, format: MediaFormat) {
        if (trackIndex.containsKey(track)) return
        trackIndex[track] = muxer.addTrack(format)
        if (trackIndex.keys.containsAll(expectedTracks)) {
            muxer.start()
            started = true
            while (pending.isNotEmpty()) {
                val (t, bytes, info) = pending.removeFirst()
                writeNow(t, ByteBuffer.wrap(bytes), info)
            }
        }
    }

    @Synchronized
    fun pause() {
        if (paused) return
        paused = true
        pauseStartUs = lastVideoRawUs
    }

    @Synchronized
    fun resume() {
        if (!paused) return
        paused = false
        resumePending = true
    }

    @Synchronized
    fun write(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        if (!started) {
            if (pending.size > 600) pending.removeFirst()
            val copy = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.get(copy, 0, info.size)
            val i = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
            pending.addLast(Triple(track, copy, i))
            return
        }
        writeNow(track, buffer, info)
    }

    private fun writeNow(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val index = trackIndex[track] ?: return
        val pts = mapPts(track, info.presentationTimeUs) ?: return
        val last = lastPts[track]
        val fixed = if (last != null && pts <= last) last + 1 else pts
        lastPts[track] = fixed
        val out = MediaCodec.BufferInfo().apply { set(info.offset, info.size, fixed, info.flags) }
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        muxer.writeSampleData(index, buffer, out)
        samplesWritten++
    }

    /** Maps a codec timestamp onto the file's timeline, or null to drop the sample. */
    private fun mapPts(track: Track, rawUs: Long): Long? {
        when (timing) {
            is Timing.Fixed -> {
                if (track != Track.VIDEO) return null
                return frames++ * 1_000_000L / timing.fps
            }
            Timing.AsIs -> {
                if (baseUs < 0) baseUs = rawUs
                return (rawUs - baseUs).takeIf { it >= 0 }
            }
            Timing.Real -> {
                val camUs = if (track == Track.AUDIO) rawUs + audioOffsetUs else rawUs
                if (track == Track.VIDEO) {
                    if (baseUs < 0) baseUs = camUs
                    if (resumePending) {
                        resumePending = false
                        if (pauseStartUs >= 0) pausedTotalUs += (camUs - pauseStartUs - frameDurUs).coerceAtLeast(0)
                    }
                    if (paused) return null
                    lastVideoRawUs = camUs
                } else {
                    if (baseUs < 0 || paused || resumePending) return null
                }
                return (camUs - baseUs - pausedTotalUs).takeIf { it >= 0 }
            }
        }
    }

    @Synchronized
    fun close(): Boolean {
        val ok = started && samplesWritten > 0
        runCatching { if (started) muxer.stop() }
        runCatching { muxer.release() }
        return ok
    }
}
