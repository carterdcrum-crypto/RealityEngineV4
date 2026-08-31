package com.realityengine.v4

/** Beginner-friendly copy for the first-launch setup walkthrough. */
object WalkthroughContent {
    data class Step(val title: String, val body: String, val actionLabel: String? = null)

    val steps = listOf(
        Step(
            "Welcome to Reality Engine",
            "Reality Engine can act as your phone app and conversation copilot. This setup will walk you through everything you need. You do not need technical experience."
        ),
        Step(
            "1. Make Reality Engine your phone app",
            "Because Reality Engine is installed directly from our GitHub APK instead of an app store, Android 16 may require you to enable Allow restricted settings before it will let a sideloaded app become your default phone app. This is an Android security safeguard for apps installed outside a recognized store; it does not give Reality Engine any extra permission by itself. If Android blocks the default-phone choice, open Reality Engine's App info, use the menu to choose Allow restricted settings, then return here. When asked, choose Reality Engine as your default phone app. You can change this later in Android Settings.",
            "Choose default phone app"
        ),
        Step(
            "2. Allow the basics",
            "Allow microphone access so Reality Engine can use supported audio sources. Contacts and call history are optional, but enabling them lets the app show names, recent calls, and caller profiles.",
            "Review permissions"
        ),
        Step(
            "3. Connect Shizuku",
            "Shizuku helps Reality Engine request access to call-audio features Android normally keeps restricted. Install and start Shizuku, return here, then approve Reality Engine when Shizuku asks. The walkthrough will show your connection status so you know when this step is complete.",
            "Check Shizuku"
        ),
        Step(
            "4. Add transcription",
            "Reality Engine uses Deepgram for live speech-to-text. Open Settings, enter your Deepgram API key, and save it. Your key is configuration for your account; never share it with other people.",
            "Open transcription settings"
        ),
        Step(
            "5. Turn on the response coach",
            "The response coach uses the configured AI service to suggest possible replies during a conversation. Add the required key in Settings and make sure Response coach is enabled.",
            "Open coach settings"
        ),
        Step(
            "6. Check call audio",
            "Before relying on transcription, use the Call audio check in Settings. Reality Engine will tell you whether the phone can provide a supported audio route or what still needs attention.",
            "Check call audio"
        ),
        Step(
            "You're ready",
            "Make a normal call from Reality Engine. During supported calls, the call screen can show live transcript text, conversation signals, and response suggestions. You can reopen this walkthrough from Settings whenever you want."
        )
    )
}
