package app.trustipay.voice

import android.app.Application
import app.trustipay.BuildConfig
import app.trustipay.offline.OfflineFeatureFlagProvider
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class VoiceAssistantViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val modelName = BuildConfig.TRUSTIPAY_STT_MODEL
    private val recorder = LocalAudioRecorder()
    private val transcriber = LocalWhisperTranscriber(modelName)
    private val voskTranscriber = VoskLiveTranscriber(application)
    private val llmBrain = LocalLlmBrain(application)
    private val audioBuffer = RollingPcmBuffer(LocalAudioRecorder.MaxRecordingBytes)
    private val nativeSupport = NativeTranscriptionCompatibility.check()

    private val _uiState = MutableStateFlow(
        VoiceAssistantUiState(
            modelName = modelName,
            isDeviceSupported = nativeSupport.isSupported,
            statusMessage = "Checking local voice model...",
            modelStorageDirectory = transcriber.modelStorageDirectory(),
            liveTranscriptionLabel = onDevicePipelineLabel(),
            llmAnalysisState = llmBrain.analysisState(),
        )
    )
    val uiState: StateFlow<VoiceAssistantUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var liveTranscriptionJob: Job? = null
    private var lastLiveSpeechBytes = 0
    private var sessionId = 0

    init {
        refreshModelState()
    }

    fun refreshModelState() {
        if (!nativeSupport.isSupported) {
            updateUnsupportedDeviceState()
            return
        }

        if (transcriber.isModelDownloaded()) {
            initializeModel()
            viewModelScope.launch(Dispatchers.IO) {
                val analysisState = llmBrain.initialize()
                updateState {
                    it.copy(
                        statusMessage = readyStatusMessage(),
                        llmAnalysisState = analysisState,
                    )
                }
            }
        } else {
            updateState {
                it.copy(
                    modelState = VoiceModelState.Missing,
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Download $modelName to enable local Sinhala and English voice requests.",
                    errorMessage = null,
                    modelStorageDirectory = transcriber.modelStorageDirectory(),
                    liveTranscriptionLabel = onDevicePipelineLabel(),
                    llmAnalysisState = llmBrain.analysisState(),
                )
            }
        }
    }

    fun downloadModel() {
        if (!nativeSupport.isSupported) {
            updateUnsupportedDeviceState()
            return
        }

        if (_uiState.value.modelState == VoiceModelState.Downloading) return

        viewModelScope.launch {
            stopListening()
            updateState {
                it.copy(
                    modelState = VoiceModelState.Downloading,
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = "Downloading models for on-device transcription...",
                    errorMessage = null,
                    modelStorageDirectory = transcriber.modelStorageDirectory(),
                )
            }

            try {
                val whisperJob = async { transcriber.downloadModel() }
                val voskJob = async { transcriber.downloadVoskModel() }
                
                awaitAll(whisperJob, voskJob)
                
                updateState {
                    it.copy(
                        modelState = VoiceModelState.Downloaded,
                        statusMessage = "Models downloaded. Initializing local transcription...",
                        errorMessage = null,
                    )
                }
                initializeModel()
                val analysisState = withContext(Dispatchers.IO) {
                    llmBrain.initialize()
                }
                updateState {
                    it.copy(
                        statusMessage = readyStatusMessage(),
                        llmAnalysisState = analysisState,
                    )
                }
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
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
                    liveTranscriptionLabel = onDevicePipelineLabel(),
                    llmAnalysisState = llmBrain.analysisState(),
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
                    statusMessage = readyStatusMessage(voskTranscriber.status()),
                    errorMessage = null,
                    liveTranscriptionLabel = onDevicePipelineLabel(),
                    llmAnalysisState = llmBrain.analysisState(),
                )
            }
        }
    }

    fun startListening(hasMicrophonePermission: Boolean) {
        if (!nativeSupport.isSupported) {
            updateUnsupportedDeviceState()
            return
        }

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
        lastLiveSpeechBytes = 0
        if (voskTranscriber.isReady) {
            voskTranscriber.resetSession()
        }
        val useVoskLive = voskTranscriber.isReady

        updateState {
            it.copy(
                captureState = VoiceCaptureState.Listening,
                transcript = "",
                languageLabel = "Not detected",
                statusMessage = if (useVoskLive) {
                    "Listening locally. Vosk live text will appear as you speak."
                } else {
                    "Listening locally. Whisper preview will appear when enough speech is captured."
                },
                errorMessage = null,
                liveTranscriptionLabel = onDevicePipelineLabel(),
                pendingBankingIntent = null,
            )
        }

        liveTranscriptionJob?.cancel()
        liveTranscriptionJob = if (useVoskLive) {
            null
        } else {
            viewModelScope.launch {
                runLiveTranscriptionLoop(activeSession)
            }
        }

        recordingJob = viewModelScope.launch {
            try {
                val recording = recorder.recordUntilStopped { chunk ->
                    audioBuffer.append(chunk)
                    if (useVoskLive) {
                        val liveText = voskTranscriber.feedAudio(chunk, chunk.size)
                        if (liveText.isNotBlank()) {
                            updateTranscriptIfActive(activeSession, liveText)
                        }
                    }
                }

                sessionId += 1
                finalizeTranscription(sessionId, recording.audio, recording.reachedMaxDuration)
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                liveTranscriptionJob?.cancel()
                liveTranscriptionJob = null
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

    fun setLanguage(language: AssistantLanguage) {
        updateState { it.copy(selectedLanguage = language) }
    }

    fun cancelActiveWork() {
        sessionId += 1
        recorder.stop()
        recordingJob?.cancel()
        liveTranscriptionJob?.cancel()
        recordingJob = null
        liveTranscriptionJob = null
        if ((_uiState.value.captureState == VoiceCaptureState.Listening ||
            _uiState.value.captureState == VoiceCaptureState.LiveTranscribing ||
            _uiState.value.captureState == VoiceCaptureState.Finalizing)
        ) {
            updateState {
                it.copy(
                    captureState = VoiceCaptureState.Idle,
                    statusMessage = readyStatusMessage(voskTranscriber.status()),
                    errorMessage = null,
                    liveTranscriptionLabel = onDevicePipelineLabel(),
                    llmAnalysisState = llmBrain.analysisState(),
                )
            }
        }
    }

    fun consumePendingBankingIntent() {
        updateState {
            it.copy(pendingBankingIntent = null)
        }
    }

    private fun initializeModel() {
        if (!nativeSupport.isSupported) {
            updateUnsupportedDeviceState()
            return
        }

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
                        statusMessage = readyStatusMessage(voskTranscriber.status()),
                        errorMessage = null,
                        modelStorageDirectory = transcriber.modelStorageDirectory(),
                        liveTranscriptionLabel = onDevicePipelineLabel(),
                        llmAnalysisState = llmBrain.analysisState(),
                    )
                }
                initializeVoskLiveTranscriber()
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                updateModelFailure(throwable, "Voice model initialization failed.")
            }
        }
    }

    private fun initializeVoskLiveTranscriber() {
        viewModelScope.launch {
            val liveStatus = voskTranscriber.initialize()
            updateState {
                if (it.modelState != VoiceModelState.Ready ||
                    it.captureState != VoiceCaptureState.Idle
                ) {
                    it.copy(liveTranscriptionLabel = onDevicePipelineLabel(liveStatus))
                } else {
                    it.copy(
                        statusMessage = readyStatusMessage(liveStatus),
                        errorMessage = null,
                        liveTranscriptionLabel = onDevicePipelineLabel(liveStatus),
                        llmAnalysisState = llmBrain.analysisState(),
                    )
                }
            }
        }
    }

    private suspend fun runLiveTranscriptionLoop(activeSession: Int) {
        while (currentCoroutineContext().isActive && activeSession == sessionId) {
            delay(LiveTranscriptionIntervalMs)
            val audioSnapshot = audioBuffer.snapshot()
            if (audioSnapshot.size < LocalAudioRecorder.MinimumTranscriptionBytes) continue

            val speechSnapshot = PcmAudioPreprocessor.trimToSpeech(audioSnapshot)
            if (speechSnapshot.size < PcmAudioPreprocessor.MinimumSpeechBytes) continue
            if (speechSnapshot.size < lastLiveSpeechBytes + MinimumNewSpeechBytesForLiveUpdate) continue

            updateStateIfActive(activeSession) {
                it.copy(
                    captureState = VoiceCaptureState.LiveTranscribing,
                    statusMessage = "Creating Whisper live preview on device...",
                    errorMessage = null,
                )
            }

            try {
                val prompt = when (_uiState.value.selectedLanguage) {
                    AssistantLanguage.Sinhala -> LocalWhisperTranscriber.SinhalaPrompt
                    AssistantLanguage.English -> LocalWhisperTranscriber.EnglishPrompt
                    AssistantLanguage.Auto -> LocalWhisperTranscriber.MultilingualPrompt
                }
                val text = transcriber.transcribeLive(speechSnapshot, prompt) { partialText ->
                    updateTranscriptIfActive(activeSession, partialText)
                }
                if (text.isNotBlank()) {
                    lastLiveSpeechBytes = speechSnapshot.size
                    updateTranscriptIfActive(activeSession, text)
                }
                updateStateIfActive(activeSession) {
                    it.copy(
                        captureState = VoiceCaptureState.Listening,
                        statusMessage = "Listening locally. Updating Whisper preview...",
                        errorMessage = null,
                    )
                }
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                updateStateIfActive(activeSession) {
                    it.copy(
                        captureState = VoiceCaptureState.Listening,
                        statusMessage = "Listening locally. Whisper preview will retry...",
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
                    "Maximum recording length reached. Finalizing with $modelName..."
                } else {
                    "Finalizing with $modelName..."
                },
                errorMessage = null,
            )
        }

        try {
            val resultText = try {
                val prompt = when (_uiState.value.selectedLanguage) {
                    AssistantLanguage.Sinhala -> LocalWhisperTranscriber.SinhalaPrompt
                    AssistantLanguage.English -> LocalWhisperTranscriber.EnglishPrompt
                    AssistantLanguage.Auto -> LocalWhisperTranscriber.MultilingualPrompt
                }
                transcriber.transcribe(audio, prompt) { partialText ->
                    updateTranscriptIfActive(activeSession, partialText)
                }
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                // LOG the failure but don't crash yet, we might have a Vosk fallback
                android.util.Log.e("VoiceAssistantVM", "Whisper finalization failed: ${throwable.message}")
                null
            }

            if (activeSession != sessionId) return

            // FALLBACK logic: Use Whisper result if successful, otherwise use what we have from Vosk
            val currentLiveText = _uiState.value.transcript.trim()
            val cleanText = (resultText?.trim() ?: "").ifBlank { currentLiveText }
            
            if (cleanText.isNotBlank()) {
                updateState {
                    it.copy(
                        statusMessage = if (resultText == null && currentLiveText.isNotBlank()) {
                            "Whisper failed, using live preview for analysis..."
                        } else {
                            "Understanding your request..."
                        },
                        transcript = cleanText,
                        languageLabel = detectLanguageLabel(cleanText)
                    )
                }
                
                val llmResult = llmBrain.processRequest(cleanText)
                val finalDisplay = llmResult.toVoiceAssistantDisplay()
                val sendMoneyIntent = (llmResult as? LlmAnalysisResult.Success)
                    ?.intent
                    ?.takeIf { it.request == BankingIntentType.SendMoney }

                val isOffline = !NetworkUtils.isOnline(getApplication())
                val offlinePaymentsEnabled = OfflineFeatureFlagProvider.current.offlinePaymentsEnabled

                updateState {
                    it.copy(
                        captureState = VoiceCaptureState.Idle,
                        transcript = finalDisplay,
                        languageLabel = detectLanguageLabel(cleanText),
                        statusMessage = captureCompleteMessage(
                            reachedMaxDuration = reachedMaxDuration,
                            usedLiveFallback = resultText == null,
                            filledPaymentDraft = sendMoneyIntent != null,
                            isOffline = isOffline && offlinePaymentsEnabled
                        ),
                        errorMessage = null,
                        liveTranscriptionLabel = onDevicePipelineLabel(),
                        llmAnalysisState = llmBrain.analysisState(),
                        pendingBankingIntent = sendMoneyIntent,
                        isOfflineSuggested = isOffline && offlinePaymentsEnabled && sendMoneyIntent != null,
                    )
                }
            } else {
                updateState {
                    it.copy(
                        captureState = VoiceCaptureState.Idle,
                        transcript = "",
                        languageLabel = "Not detected",
                        statusMessage = "No speech was recognized. Try again closer to the microphone.",
                        errorMessage = null,
                        liveTranscriptionLabel = onDevicePipelineLabel(),
                        llmAnalysisState = llmBrain.analysisState(),
                    )
                }
            }
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
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
                isDeviceSupported = nativeSupport.isSupported,
                modelState = VoiceModelState.Failed,
                captureState = VoiceCaptureState.Idle,
                statusMessage = fallback,
                errorMessage = throwable.toFriendlyMessage(fallback),
                modelStorageDirectory = transcriber.modelStorageDirectory(),
                liveTranscriptionLabel = onDevicePipelineLabel(),
                llmAnalysisState = llmBrain.analysisState(),
            )
        }
    }

    private fun updateUnsupportedDeviceState() {
        updateState {
            it.copy(
                isDeviceSupported = false,
                modelState = VoiceModelState.Failed,
                captureState = VoiceCaptureState.Idle,
                statusMessage = "Local Cactus Whisper STT is not supported on this CPU.",
                errorMessage = nativeSupport.message,
                modelStorageDirectory = transcriber.modelStorageDirectory(),
                liveTranscriptionLabel = onDevicePipelineLabel(),
                llmAnalysisState = llmBrain.analysisState(),
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

    private fun readyStatusMessage(
        liveStatus: VoskLiveStatus = voskTranscriber.status(),
    ): String {
        val baseMessage = when {
            liveStatus.isReady -> "Ready. Vosk live preview and $modelName finalization are available."
            liveStatus == VoskLiveStatus.NotInitialized ||
                liveStatus == VoskLiveStatus.Initializing ->
                "Ready. Preparing Vosk live preview; $modelName finalization is available."
            else -> "Ready. $modelName finalization is available; ${liveStatus.message}"
        }
        
        return when (llmBrain.analysisState()) {
            LlmAnalysisState.Ready -> baseMessage
            LlmAnalysisState.Initializing -> "$baseMessage (Preparing LiteRT-LM request analysis)"
            LlmAnalysisState.Failed -> "$baseMessage (LiteRT-LM request analysis unavailable)"
            LlmAnalysisState.Missing -> if (llmBrain.isModelAvailable()) {
                "$baseMessage (Preparing LiteRT-LM request analysis)"
            } else {
                "$baseMessage (Note: LLM model not found for request analysis)"
            }
        }
    }

    private fun onDevicePipelineLabel(
        liveStatus: VoskLiveStatus = voskTranscriber.status(),
    ): String = if (liveStatus.isReady) {
        "Vosk live + $modelName finalization"
    } else {
        "$modelName finalization on device"
    }

    override fun onCleared() {
        cancelActiveWork()
        transcriber.close()
        voskTranscriber.close()
        llmBrain.close()
        super.onCleared()
    }

    private companion object {
        const val LiveTranscriptionIntervalMs = 2_500L
        const val MinimumNewSpeechBytesForLiveUpdate = 12_000
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

private fun captureCompleteMessage(
    reachedMaxDuration: Boolean,
    usedLiveFallback: Boolean,
    filledPaymentDraft: Boolean,
    isOffline: Boolean = false,
): String =
    when {
        isOffline && filledPaymentDraft -> "Offline? Initiating offline payment draft. Review below."
        filledPaymentDraft -> "Payment form filled from your voice request. Review before continuing."
        reachedMaxDuration -> "Captured locally. Recording stopped at the 30 second limit."
        usedLiveFallback -> "Captured locally (via live fallback)."
        else -> "Captured locally."
    }

private fun LlmAnalysisResult.toVoiceAssistantDisplay(): String =
    when (this) {
        is LlmAnalysisResult.Success -> intent.toVoiceAssistantDisplay()
        is LlmAnalysisResult.Unavailable,
        is LlmAnalysisResult.Failure -> "Analysis paused: ${toJsonString()}\n\nRaw transcript: $rawTranscript"
    }

private fun BankingIntent.toVoiceAssistantDisplay(): String =
    when (request) {
        BankingIntentType.SendMoney -> buildList {
            add("Send money draft")
            to?.takeIf { it.isNotBlank() }?.let { add("To: $it") }
            amount?.let { add("Amount: Rs. ${it.toAmountText()}") }
            reason?.takeIf { it.isNotBlank() }?.let { add("Note: $it") }
        }.joinToString("\n")
        BankingIntentType.PayBill -> buildList {
            add("Bill payment draft")
            to?.takeIf { it.isNotBlank() }?.let { add("Biller: $it") }
            amount?.let { add("Amount: Rs. ${it.toAmountText()}") }
            reason?.takeIf { it.isNotBlank() }?.let { add("Note: $it") }
        }.joinToString("\n")
        BankingIntentType.CheckBalance -> "Balance check request"
        BankingIntentType.Unknown -> reason?.takeIf { it.isNotBlank() } ?: "Unknown voice request"
    }

private fun Double.toAmountText(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

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
