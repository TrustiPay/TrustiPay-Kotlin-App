package app.trustipay.voice

import android.app.Application
import app.trustipay.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class VoiceAssistantViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val modelName = BuildConfig.TRUSTIPAY_STT_MODEL
    private val recorder = LocalAudioRecorder()
    private val transcriber = LocalWhisperTranscriber(modelName)
    private val audioBuffer = RollingPcmBuffer(LocalAudioRecorder.MaxRecordingBytes)

    private val _uiState = MutableStateFlow(
        VoiceAssistantUiState(
            modelName = modelName,
            statusMessage = "Checking local voice model...",
            modelStorageDirectory = transcriber.modelStorageDirectory(),
        )
    )
    val uiState: StateFlow<VoiceAssistantUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var liveTranscriptionJob: Job? = null
    private var sessionId = 0

    init {
        refreshModelState()
    }

    fun refreshModelState() {
        if (transcriber.isModelDownloaded()) {
            initializeModel()
        } else {
            updateState {
                it.copy(
                    modelState = VoiceModelState.Missing,
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Download $modelName to enable local Sinhala and English voice requests.",
                    errorMessage = null,
                    modelStorageDirectory = transcriber.modelStorageDirectory(),
                )
            }
        }
    }

    fun downloadModel() {
        if (_uiState.value.modelState == VoiceModelState.Downloading) return

        viewModelScope.launch {
            stopListening()
            updateState {
                it.copy(
                    modelState = VoiceModelState.Downloading,
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Downloading $modelName for on-device Sinhala and English transcription...",
                    errorMessage = null,
                    modelStorageDirectory = transcriber.modelStorageDirectory(),
                )
            }

            try {
                transcriber.downloadModel()
                updateState {
                    it.copy(
                        modelState = VoiceModelState.Downloaded,
                        statusMessage = "$modelName downloaded. Initializing local transcription...",
                        errorMessage = null,
                    )
                }
                initializeModel()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                updateModelFailure(throwable, "Voice model download failed.")
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            stopListening()
            transcriber.close()
            val deleted = transcriber.deleteModel()
            updateState {
                it.copy(
                    modelState = VoiceModelState.Missing,
                    captureState = VoiceCaptureState.Idle,
                    transcript = "",
                    languageLabel = "Not detected",
                    statusMessage = if (deleted) {
                        "$modelName was deleted from app storage."
                    } else {
                        "$modelName was not found in app storage."
                    },
                    errorMessage = null,
                    modelStorageDirectory = transcriber.modelStorageDirectory(),
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Error,
                    statusMessage = "Microphone permission is required for local voice capture.",
                    errorMessage = "Allow microphone access and try again.",
                )
            }
        } else if (_uiState.value.modelState == VoiceModelState.Ready) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Ready. Tap Start and speak in Sinhala or English.",
                    errorMessage = null,
                )
            }
        }
    }

    fun startListening(hasMicrophonePermission: Boolean) {
        if (!hasMicrophonePermission) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Error,
                    statusMessage = "Microphone permission is required for local voice capture.",
                    errorMessage = "Grant microphone access to continue.",
                )
            }
            return
        }

        if (_uiState.value.modelState != VoiceModelState.Ready) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Error,
                    statusMessage = "Voice model is not ready yet.",
                    errorMessage = "Download and initialize $modelName first.",
                )
            }
            return
        }

        if (recordingJob?.isActive == true) return

        sessionId += 1
        val activeSession = sessionId
        audioBuffer.clear()

        updateState {
            it.copy(
                captureState = VoiceCaptureState.Listening,
                transcript = "",
                languageLabel = "Not detected",
                statusMessage = "Listening locally. Live text will appear as you speak.",
                errorMessage = null,
            )
        }

        liveTranscriptionJob?.cancel()
        liveTranscriptionJob = viewModelScope.launch {
            runLiveTranscriptionLoop(activeSession)
        }

        recordingJob = viewModelScope.launch {
            try {
                val recording = recorder.recordUntilStopped { chunk ->
                    audioBuffer.append(chunk)
                }

                liveTranscriptionJob?.cancel()
                liveTranscriptionJob = null

                sessionId += 1
                finalizeTranscription(sessionId, recording.audio, recording.reachedMaxDuration)
            } catch (throwable: Throwable) {
                liveTranscriptionJob?.cancel()
                liveTranscriptionJob = null
                if (throwable is CancellationException) throw throwable
                if (activeSession == sessionId) {
                    updateState {
                        it.copy(
                            captureState = VoiceCaptureState.Error,
                            statusMessage = "Voice capture failed.",
                            errorMessage = throwable.toFriendlyMessage("Recording failed."),
                        )
                    }
                }
            }
        }
    }

    fun stopListening() {
        recorder.stop()
    }

    fun cancelActiveWork() {
        sessionId += 1
        recorder.stop()
        recordingJob?.cancel()
        liveTranscriptionJob?.cancel()
        recordingJob = null
        liveTranscriptionJob = null
        if (_uiState.value.captureState == VoiceCaptureState.Listening ||
            _uiState.value.captureState == VoiceCaptureState.LiveTranscribing ||
            _uiState.value.captureState == VoiceCaptureState.Finalizing
        ) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Ready. Tap Start and speak in Sinhala or English.",
                    errorMessage = null,
                )
            }
        }
    }

    private fun initializeModel() {
        if (_uiState.value.modelState == VoiceModelState.Initializing) return

        viewModelScope.launch {
            updateState {
                it.copy(
                    modelState = VoiceModelState.Initializing,
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Initializing $modelName locally...",
                    errorMessage = null,
                )
            }

            try {
                transcriber.initializeDownloadedModel()
                updateState {
                    it.copy(
                        modelState = VoiceModelState.Ready,
                        captureState = VoiceCaptureState.Idle,
                        statusMessage = "Ready. Tap Start and speak in Sinhala or English.",
                        errorMessage = null,
                        modelStorageDirectory = transcriber.modelStorageDirectory(),
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                updateModelFailure(throwable, "Voice model initialization failed.")
            }
        }
    }

    private suspend fun runLiveTranscriptionLoop(activeSession: Int) {
        while (currentCoroutineContext().isActive && activeSession == sessionId) {
            delay(LiveTranscriptionIntervalMs)
            val audioSnapshot = audioBuffer.snapshot()
            if (audioSnapshot.size < LocalAudioRecorder.MinimumTranscriptionBytes) continue

            updateStateIfActive(activeSession) {
                it.copy(
                    captureState = VoiceCaptureState.LiveTranscribing,
                    statusMessage = "Live transcribing on device...",
                    errorMessage = null,
                )
            }

            try {
                val text = transcriber.transcribeLive(audioSnapshot) { partialText ->
                    updateTranscriptIfActive(activeSession, partialText)
                }
                updateTranscriptIfActive(activeSession, text)
                updateStateIfActive(activeSession) {
                    it.copy(
                        captureState = VoiceCaptureState.Listening,
                        statusMessage = "Listening locally. Updating live transcript...",
                        errorMessage = null,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                updateStateIfActive(activeSession) {
                    it.copy(
                        captureState = VoiceCaptureState.Listening,
                        statusMessage = "Listening locally. Live transcription will retry...",
                        errorMessage = throwable.toFriendlyMessage("Live transcription failed."),
                    )
                }
            }
        }
    }

    private suspend fun finalizeTranscription(
        activeSession: Int,
        audio: ByteArray,
        reachedMaxDuration: Boolean,
    ) {
        if (activeSession != sessionId) return

        if (audio.size < LocalAudioRecorder.MinimumTranscriptionBytes) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Please speak a little longer before stopping.",
                    errorMessage = null,
                )
            }
            return
        }

        updateState {
            it.copy(
                captureState = VoiceCaptureState.Finalizing,
                statusMessage = if (reachedMaxDuration) {
                    "Maximum recording length reached. Finalizing transcript on device..."
                } else {
                    "Finalizing transcript on device..."
                },
                errorMessage = null,
            )
        }

        try {
            val finalText = transcriber.transcribeLive(audio) { partialText ->
                updateTranscriptIfActive(activeSession, partialText)
            }
            if (activeSession != sessionId) return

            val cleanText = finalText.trim()
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Idle,
                    transcript = cleanText,
                    languageLabel = detectLanguageLabel(cleanText),
                    statusMessage = if (cleanText.isBlank()) {
                        "No speech was recognized. Try again closer to the microphone."
                    } else if (reachedMaxDuration) {
                        "Captured locally with $modelName. Recording stopped at the 30 second limit."
                    } else {
                        "Captured locally with $modelName."
                    },
                    errorMessage = null,
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            updateStateIfActive(activeSession) {
                it.copy(
                    captureState = VoiceCaptureState.Error,
                    statusMessage = "Voice request transcription failed.",
                    errorMessage = throwable.toFriendlyMessage("Transcription failed."),
                )
            }
        }
    }

    private fun updateTranscriptIfActive(activeSession: Int, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || activeSession != sessionId) return

        updateState {
            it.copy(
                transcript = cleanText,
                languageLabel = detectLanguageLabel(cleanText),
            )
        }
    }

    private fun updateModelFailure(throwable: Throwable, fallback: String) {
        updateState {
            it.copy(
                modelState = VoiceModelState.Failed,
                captureState = VoiceCaptureState.Idle,
                statusMessage = fallback,
                errorMessage = throwable.toFriendlyMessage(fallback),
                modelStorageDirectory = transcriber.modelStorageDirectory(),
            )
        }
    }

    private fun updateState(transform: (VoiceAssistantUiState) -> VoiceAssistantUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun updateStateIfActive(
        activeSession: Int,
        transform: (VoiceAssistantUiState) -> VoiceAssistantUiState,
    ) {
        if (activeSession == sessionId) {
            updateState(transform)
        }
    }

    override fun onCleared() {
        cancelActiveWork()
        transcriber.close()
        super.onCleared()
    }

    private companion object {
        const val LiveTranscriptionIntervalMs = 1_500L
    }
}

private fun detectLanguageLabel(text: String): String {
    val hasSinhala = text.any { it in '\u0D80'..'\u0DFF' }
    val hasEnglish = text.any { it in 'A'..'Z' || it in 'a'..'z' }

    return when {
        hasSinhala && hasEnglish -> "Mixed Sinhala + English"
        hasSinhala -> "Sinhala"
        hasEnglish -> "English"
        else -> "Not detected"
    }
}

private fun Throwable.toFriendlyMessage(fallback: String): String {
    val message = message.orEmpty()
    val lowerMessage = message.lowercase()

    return when {
        this is OutOfMemoryError -> "The device ran out of memory while loading or transcribing with the voice model."
        lowerMessage.contains("unable to resolve host") ||
            lowerMessage.contains("failed to connect") ||
            lowerMessage.contains("timeout") ||
            lowerMessage.contains("network") -> "Network is required only to download the voice model. Check your connection and retry."
        lowerMessage.contains("download") -> "The voice model could not be downloaded. Retry, or delete the partial model and try again."
        lowerMessage.contains("microphone") ||
            lowerMessage.contains("audio recorder") ||
            lowerMessage.contains("record") -> message.ifBlank { "The microphone is unavailable. Another app may be using it." }
        lowerMessage.contains("permission") -> "Microphone permission is required for local voice capture."
        lowerMessage.contains("model") ||
            lowerMessage.contains("context") ||
            lowerMessage.contains("native") -> message.ifBlank { "The local voice model could not be initialized on this device." }
        else -> message.ifBlank { fallback }
    }
}
