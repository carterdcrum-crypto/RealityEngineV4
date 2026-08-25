package com.realityengine.v4

/**
 * Configuration boundary for the scrcpy-server call-audio backend.
 *
 * scrcpy 4.x supports an audio-only server, direct voice-call capture and raw
 * PCM output. Keeping these arguments in one place lets the upcoming Shizuku
 * shell bridge launch a pinned server/protocol version without mixing this
 * transport with the existing AudioRecord fallback.
 */
object ScrcpyAudioCaptureConfig {
    const val SERVER_VERSION = "4.0"
    const val SERVER_ASSET_NAME = "scrcpy-server-v4.0"
    const val DEVICE_SERVER_PATH = "/data/local/tmp/reality-scrcpy-server.jar"
    const val AUDIO_SOURCE = "voice-call"
    const val AUDIO_CODEC = "raw"

    /** Arguments passed after com.genymobile.scrcpy.Server and SERVER_VERSION. */
    fun serverArguments(scid: Int): List<String> = listOf(
        "scid=${scid.toUInt().toString(16)}",
        "log_level=warn",
        "video=false",
        "audio=true",
        "audio_source=$AUDIO_SOURCE",
        "audio_codec=$AUDIO_CODEC",
        "control=false",
        "cleanup=false",
        "send_device_meta=false",
        "send_frame_meta=false",
        "send_dummy_byte=false",
        "send_stream_meta=false"
    )

    /** Shell-side command shape. The bridge supplies the pinned server jar. */
    fun appProcessCommand(scid: Int): List<String> = listOf(
        "env",
        "CLASSPATH=$DEVICE_SERVER_PATH",
        "app_process",
        "/",
        "com.genymobile.scrcpy.Server",
        SERVER_VERSION
    ) + serverArguments(scid)
}
