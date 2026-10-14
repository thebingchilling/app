package app.quartz.core.caps.probe

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import app.quartz.core.caps.EncoderInfo
import app.quartz.core.caps.Size
import app.quartz.core.caps.options.Codec
import app.quartz.core.caps.options.VideoOptions

/** Lists video encoders and which sizes and frame rates each accepts. */
object EncoderProbe {

    fun probe(candidateSizes: Set<Size>, candidateFps: Set<Int>): List<EncoderInfo> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val out = mutableListOf<EncoderInfo>()
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            for (codec in Codec.entries) {
                if (info.supportedTypes.none { it.equals(codec.mime, ignoreCase = true) }) continue
                val caps = runCatching { info.getCapabilitiesForType(codec.mime) }.getOrNull() ?: continue
                val video = caps.videoCapabilities ?: continue
                val support = mutableListOf<String>()
                for (s in candidateSizes) {
                    for (fps in candidateFps) {
                        val ok = runCatching { video.areSizeAndRateSupported(s.w, s.h, fps.toDouble()) }.getOrDefault(false)
                        if (ok) support += EncoderInfo.key(s.w, s.h, fps)
                    }
                }
                if (support.isEmpty()) continue
                val main10 = caps.profileLevels.any {
                    when (codec) {
                        Codec.HEVC -> it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                        Codec.AV1 -> it.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                        Codec.H264 -> false
                    }
                }
                val enc = caps.encoderCapabilities
                val modes = listOf(
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
                ).filter { runCatching { enc?.isBitrateModeSupported(it) == true }.getOrDefault(false) }
                out += EncoderInfo(
                    mime = codec.mime,
                    name = info.name,
                    hardware = isHardware(info),
                    maxBitrate = video.bitrateRange.upper,
                    main10 = main10,
                    bitrateModes = modes,
                    support = support.sorted(),
                )
            }
        }
        return out
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            info.isHardwareAccelerated && !info.isSoftwareOnly
        } else {
            val n = info.name.lowercase()
            !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw."))
        }

    val defaultFps: Set<Int> get() = VideoOptions.FPS.toSet() + setOf(240, 480, 960)
}
