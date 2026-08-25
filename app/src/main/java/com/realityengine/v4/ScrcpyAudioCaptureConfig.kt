package com.realityengine.v4

/**
 * Configuration boundary for the scrcpy-server call-audio backend.
 *
 * scrcpy 4.x supports an audio-only server, voice-call capture and raw PCM.
 * The standalone raw_stream option disables device, frame, dummy-byte and
 * stream metadata so the socket reader receives only PCM16-LE payload bytes.
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
        "raw_stream=true"
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
