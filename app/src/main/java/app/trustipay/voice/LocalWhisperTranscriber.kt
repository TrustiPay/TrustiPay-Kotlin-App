package app.trustipay.voice

import com.cactus.CactusInitParams
import com.cactus.CactusModelManager
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.TranscriptionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

class LocalWhisperTranscriber(
    private val modelSlug: String,
) : Closeable {
    private val stt = CactusSTT()
    private val transcriptionMutex = Mutex()

    fun isModelDownloaded(): Boolean =
        CactusModelManager.isModelDownloaded(modelSlug)

    fun deleteModel(): Boolean =
        CactusModelManager.deleteModel(modelSlug)

    fun modelStorageDirectory(): String =
        CactusModelManager.getModelsDirectory()

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        stt.downloadModel(modelSlug)
    }

    suspend fun initializeDownloadedModel() = withContext(Dispatchers.IO) {
        check(isModelDownloaded()) {
            "Voice model $modelSlug is not downloaded."
        }
        stt.initializeModel(CactusInitParams(model = modelSlug))
    }

    suspend fun transcribeLive(
        audioBuffer: ByteArray,
        onPartialText: suspend (String) -> Unit = {},
    ): String = coroutineScope {
        transcriptionMutex.withLock {
            val callbackScope = this@coroutineScope
            val partialLock = Any()
            var partialText = ""

            val result = withContext(Dispatchers.IO) {
                stt.transcribe(
                    prompt = MultilingualPrompt,
                    params = CactusTranscriptionParams(model = modelSlug),
                    mode = TranscriptionMode.LOCAL,
                    audioBuffer = audioBuffer,
                    onToken = { token, _ ->
                        if (token.isNotBlank()) {
                            val nextText = synchronized(partialLock) {
                                partialText += token
                                partialText.trim()
                            }
                            callbackScope.launch(Dispatchers.Main) {
                                onPartialText(nextText)
                            }
                        }
                    },
                )
            } ?: error("No transcription result was returned.")

            check(result.success) {
                "Transcription failed."
            }

            result.text?.trim().orEmpty().ifBlank {
                synchronized(partialLock) { partialText.trim() }
            }
        }
    }

    suspend fun transcribe(audioBuffer: ByteArray): String = transcribeLive(audioBuffer)

    override fun close() {
        stt.reset()
    }

    private companion object {
        const val MultilingualPrompt = "<|startoftranscript|><|transcribe|><|notimestamps|>"
    }
}
