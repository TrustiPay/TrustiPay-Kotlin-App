# Implementation Plan: Offline AI Voice Assistant with LiteRT-LM

Transform the current voice assistant into a robust, offline-first experience similar to Alexa/Siri, capable of understanding and formatting user requests using on-device Large Language Models.

## User Review Required

> [!IMPORTANT]
> **LLM Model File**: This plan uses the MediaPipe GenAI (LiteRT) SDK. You will need to provide a compatible model file (e.g., Gemma 2b or Falcon 1b in `.bin` or `.tflite` format) on the device or in the app's assets for the "LLM Brain" to function.

## Proposed Changes

### Core Logic & Brain

#### [NEW] [LocalLlmBrain.kt](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/app/src/main/java/app/trustipay/voice/LocalLlmBrain.kt)
- Create a wrapper around `LlmInference` (LiteRT-LM) to process text and output JSON.
- Define the system prompt to guide the model into a "Banking Assistant" persona.

#### [LocalWhisperTranscriber.kt](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/app/src/main/java/app/trustipay/voice/LocalWhisperTranscriber.kt)
- Fix the `Error: completion failed with code -1` by ensuring proper cleanup and initialization.
- Refine the natural language prompt to be even simpler to avoid decoder resets.

#### [VoiceAssistantViewModel.kt](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/app/src/main/java/app/trustipay/voice/VoiceAssistantViewModel.kt)
- Orchestrate the new 3-step pipeline:
    1. **Detect Language**: Use Whisper's output to identify Sinhala/English.
    2. **Transcribe**: Real-time feedback with aggressive hallucination filtering.
    3. **Reason**: Feed the final transcript to `LocalLlmBrain` and update the UI with the structured JSON results.

### Dependencies & Configuration

#### [libs.versions.toml](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/gradle/libs.versions.toml)
- Add `com.google.mediapipe:tasks-genai:0.10.35`.

#### [build.gradle.kts](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/app/build.gradle.kts)
- Include the MediaPipe GenAI dependency.

### UI Improvements

#### [VoiceAssistantScreen.kt](file:///media/chinthana/Storage/Education/SLIIT/KotlinApp/TrustiPay/app/src/main/java/app/trustipay/ui/screens/VoiceAssistantScreen.kt)
- Add a visual "Thinking" state when the LLM is processing the request.
- Display the formatted JSON request to the user for confirmation.

## Verification Plan

### Automated Tests
- `gradle build` to verify dependency integration.
- Unit tests for `LocalLlmBrain` using mock text inputs.

### Manual Verification
1. **Language Detection**: Speak in Sinhala and verify the label "Sinhala" appears correctly.
2. **Real-time Transcription**: Verify "Hello. Hello." loops and `<|...|>` tokens no longer appear.
3. **LLM Formatting**: Speak "Send 500 rupees to Saman for lunch" and verify the screen shows a structured JSON object: `{ "request": "send_money", "to": "Saman", "amount": 500, ... }`.
