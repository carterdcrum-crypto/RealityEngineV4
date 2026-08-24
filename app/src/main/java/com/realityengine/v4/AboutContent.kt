package com.realityengine.v4

/** Plain-language product copy shared by About and onboarding surfaces. */
object AboutContent {
    const val TITLE = "About Reality Engine"
    const val DEVELOPER = "Developed by: Carter Crum"
    const val INTRO = "Reality Engine is a live conversation copilot designed to help you follow calls, notice useful context, and decide what to say next without complicated tools."

    data class Capability(val title: String, val description: String)

    val capabilities = listOf(
        Capability("Live transcription", "Turns supported call audio into readable conversation text while the call is happening."),
        Capability("Know who said what", "Separates caller and user speech when speaker identification is available, making the conversation easier to follow."),
        Capability("Response coach", "Suggests useful ways to respond based on the conversation so you are not stuck searching for the right words."),
        Capability("Live conversation signals", "Highlights notable changes in speech, wording, and factual consistency. These are conversation cues, not proof that someone is lying."),
        Capability("Conversation memory", "Keeps useful caller context such as important facts, recent topics, preferences, and previous call information."),
        Capability("Consistency checks", "Compares new conversation details with saved context and surfaces possible conflicts for you to review."),
        Capability("Optional haptic alerts", "Can quietly notify you when several conversation signals become elevated at the same time."),
        Capability("After-call insights", "Organizes useful call information and signal context after the conversation so important moments are easier to review."),
        Capability("Your controls", "Lets you manage coaching, haptics, calling setup, audio access, and service configuration from Settings.")
    )
}
