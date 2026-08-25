package com.realityengine.v4

/**
 * Shell-safe command serialization for launching the pinned scrcpy server.
 * Execution is intentionally kept behind a narrow boundary so the transport
 * can be swapped without changing the transcription pipeline.
 */
object ScrcpyShellCommand {
    fun build(scid: Int): String = ScrcpyAudioCaptureConfig
        .appProcessCommand(scid)
        .joinToString(" ") { shellQuote(it) }

    private fun shellQuote(value: String): String {
        if (value.all { it.isLetterOrDigit() || it in "_./:=,-" }) return value
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
