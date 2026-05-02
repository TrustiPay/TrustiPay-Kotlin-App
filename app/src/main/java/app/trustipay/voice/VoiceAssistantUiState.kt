package app.trustipay.voice

data class VoiceAssistantUiState(
    val modelName: String,
    val modelState: VoiceModelState = VoiceModelState.Missing,
    val captureState: VoiceCaptureState = VoiceCaptureState.Idle,
    val isDeviceSupported: Boolean = true,
    val transcript: String = "",
    val languageLabel: String = "Not detected",
    val statusMessage: String = "Voice model setup is required.",
    val errorMessage: String? = null,
    val modelStorageDirectory: String = "",
    val liveTranscriptionLabel: String = "Whisper finalization on device",
    val llmAnalysisState: LlmAnalysisState = LlmAnalysisState.Missing,
) {
    val isBusy: Boolean
        get() = modelState == VoiceModelState.Downloading ||
            modelState == VoiceModelState.Initializing ||
            captureState == VoiceCaptureState.Finalizing

    val canDownloadModel: Boolean
        get() = isDeviceSupported &&
            (modelState == VoiceModelState.Missing || modelState == VoiceModelState.Failed)

    val canDeleteModel: Boolean
        get() = modelState == VoiceModelState.Ready ||
            modelState == VoiceModelState.Downloaded ||
            modelState == VoiceModelState.Failed

    val canRequestRecording: Boolean
        get() = isDeviceSupported &&
            modelState == VoiceModelState.Ready &&
            captureState != VoiceCaptureState.Listening &&
            captureState != VoiceCaptureState.LiveTranscribing &&
            captureState != VoiceCaptureState.Finalizing
}

enum class VoiceModelState {
    Missing,
    Downloading,
    Downloaded,
    Initializing,
    Ready,
    Failed,
}

enum class VoiceCaptureState {
    Idle,
    Listening,
    LiveTranscribing,
    Finalizing,
    Error,
}
