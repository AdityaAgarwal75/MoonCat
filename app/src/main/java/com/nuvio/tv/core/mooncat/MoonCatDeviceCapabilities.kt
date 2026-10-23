package com.nuvio.tv.core.mooncat

import android.media.MediaCodecList

/** Query once, off the UI thread. Software-only 4K decoders do not qualify. */
object MoonCatDeviceCapabilities {
    val supports4k: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codec ->
                !codec.isEncoder && !codec.name.startsWith("OMX.google.") &&
                    !codec.name.startsWith("c2.android.") && codec.supportedTypes.any { mime ->
                    mime in setOf("video/hevc", "video/avc", "video/av01", "video/x-vnd.on2.vp9") &&
                        runCatching { codec.getCapabilitiesForType(mime).videoCapabilities
                            ?.areSizeAndRateSupported(3840, 2160, 30.0) == true }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
    }
}
