# Reality Engine V4

![Android Build](https://github.com/carterdcrum-crypto/RealityEngineV4/actions/workflows/android.yml/badge.svg)

**Reality Engine** is an experimental Android phone and live-conversation copilot built to make calls easier to follow, understand, and respond to in real time.

Instead of acting like a traditional dialer alone, Reality Engine adds a second layer to the call experience: live transcription, speaker-aware conversation context, AI-generated response coaching, conversation-signal analysis, caller memory, and post-call review tools.

The goal is simple: while you are talking to someone, the app should help you keep track of what is being said, remember important context, notice meaningful changes or inconsistencies, and decide what to say next without having to leave the call screen.

---

## What Reality Engine Does

### Live transcription
Reality Engine can turn supported call audio into readable text while a call is happening. Deepgram is used as the live speech-to-text provider, with the API key and transcription model configured inside the app.

When speaker identification is available, the transcript can distinguish between the user and the caller so the conversation is easier to follow.

### AI response coach
During a conversation, Reality Engine can generate ranked response suggestions based on the current transcript and context.

The coaching system supports multiple AI providers and routing modes, including:

- Groq
- Google Gemini
- Cerebras
- Mistral
- OpenRouter

Providers can be configured from **Settings → Intelligence**, and the app can choose between them according to the selected routing preference. API keys are entered locally in the app rather than committed to this repository.

### Live conversation signals
Reality Engine tracks several types of conversational cues during a call:

- **Acoustic signals** — changes in characteristics of speech and voice behavior.
- **Linguistic signals** — changes in wording, phrasing, distancing, cognitive-load-style markers, and other language patterns.
- **Factual / consistency signals** — possible conflicts between what is being said now and context previously saved for that caller.

These signals are intended to help the user notice moments worth paying attention to. **They are not proof that someone is lying and should not be treated as a scientific lie detector.**

Optional haptic alerts can quietly notify the user when multiple signal categories become elevated at the same time.

### Caller memory
Reality Engine maintains useful context for individual callers, including information such as:

- important facts and previous topics
- preferences and personal context
- prior conversation details
- transcripts and call summaries
- possible consistency conflicts across conversations

Completed-call transcripts can be stored privately on the device. Optional Supabase integration is available for caller-memory cloud synchronization using a project URL and publishable key.

### After-call intelligence
After a call, Reality Engine can organize the conversation into a reviewable record with transcript context, summaries, notable moments, signal activity, and caller-memory updates.

The project also contains dedicated screens for transcript history, saved recordings, caller memory, relationship timelines, post-call review, and post-call intelligence.

### Calling experience
Reality Engine is designed to participate directly in Android's calling experience rather than operate only as a separate note-taking app.

The project includes:

- Android dial intents
- an `InCallService`
- a dedicated in-call activity
- contact and call-history integration
- call controls and audio-routing logic
- optional Shizuku-assisted call-audio setup
- an in-call soundboard

Android does not provide a general supported API for injecting arbitrary media directly into the cellular-call uplink. The soundboard therefore uses the audio behavior documented by the app rather than pretending that unsupported capability exists.

---

## Core Philosophy

Reality Engine is being built around four ideas:

1. **Useful in the moment.** Information should appear while it can still help the conversation.
2. **Context over verdicts.** Signals should give the user more context, not make unsupported claims about another person.
3. **Local-first control.** Sensitive settings, transcripts, and user-controlled features should remain as private and explicit as practical.
4. **User choice.** Coaching, haptics, recording, AI providers, audio setup, and cloud memory are configurable rather than forced.

---

## Privacy and Recording

Reality Engine handles potentially sensitive call and conversation information. Anyone building or using the project is responsible for following the laws and consent requirements that apply where they live.

Call recording is optional and visibly indicated by the app. The recording workflow is designed to require an explicit post-call choice to save or permanently delete a recorded call.

Do not commit private API keys, credentials, recordings, transcripts, or personal caller data to the repository.

---

## Current Technology

Reality Engine V4 is a native Android project written primarily in **Kotlin**.

- Android compile/target SDK: **35**
- Minimum Android SDK: **26**
- Java / JVM target: **17**
- UI: Android Views + Jetpack Compose components
- Networking: OkHttp
- Call integration: Android Telecom / `InCallService`
- Optional elevated Android integration: Shizuku
- Live transcription: Deepgram
- AI coaching: configurable multi-provider routing
- Optional caller-memory sync: Supabase

The Gradle build also downloads a pinned `scrcpy-server` asset and verifies its SHA-256 before packaging it, preventing an unexpected server binary from being silently substituted during the build.

---

## Building the Project

The repository includes a GitHub Actions workflow at:

```text
.github/workflows/android.yml
```

The main CI pipeline builds the debug APK, runs unit tests, and—after a successful main build—publishes the latest updater APK used by Reality Engine's in-app update system.

For a local Android build, use JDK 17 and a compatible Android SDK, then run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The application ID is:

```text
com.realityengine.v4
```

---

## Initial Setup

A fresh installation is expected to require some configuration before all features are available.

Inside the app, the setup flow covers areas such as:

1. Selecting Reality Engine as the default phone application where required.
2. Granting the Android permissions needed for the features you choose to use.
3. Configuring Shizuku if using the supported Shizuku-assisted audio path.
4. Adding a Deepgram API key for live transcription.
5. Adding one or more supported AI-provider keys for response coaching.
6. Optionally configuring Supabase caller-memory synchronization.
7. Choosing coaching, haptic, recording, model, and routing preferences.

API-provider setup links are available directly beneath the corresponding key fields in Settings.

---

## Project Status

Reality Engine V4 is an actively developed experimental project. Features, provider integrations, Android audio behavior, analysis models, and UI flows may change as the project evolves.

The project is intended as a conversation-assistance system—not as a substitute for professional judgment, factual verification, medical or psychological assessment, or a validated deception-detection instrument.

---

## Developer

**Carter Crum**

Reality Engine V4 is being developed as an exploration of what a phone call can become when transcription, memory, conversational analysis, and AI coaching are integrated directly into the calling interface.
