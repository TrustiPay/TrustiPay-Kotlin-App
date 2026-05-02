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
        val nativeSupport = NativeTranscriptionCompatibility.check()
        check(nativeSupport.isSupported) {
            nativeSupport.message
        }

        transcriptionMutex.withLock {
            val callbackScope = this@coroutineScope
            val partialLock = Any()
            var partialText = ""

            val result = withContext(Dispatchers.IO) {
                try {
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
                                    onPartialText(sanitizeWhisperHallucination(nextText))
                                }
                            }
                        },
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: error("Cactus STT returned null result or crashed.")

            if (!result.success) {
                val errorMsg = result.errorMessage ?: "Unknown error"
                error("Transcription failed: $errorMsg")
            }

            val rawText = result.text?.trim().orEmpty().ifBlank {
                synchronized(partialLock) { partialText.trim() }
            }
            sanitizeWhisperHallucination(rawText)
        }
    }

    private fun sanitizeWhisperHallucination(text: String): String {
        // Strip out Whisper internal tokens like <|startoftranscript|> if they leak
        val cleaned = text.replace(Regex("<\\|.*?\\|>"), "").trim()
        
        if (cleaned.isEmpty()) return ""
        
        // 1. Block known short hallucinations and common noise markers
        val normalized = cleaned.lowercase().replace(Regex("[.\\s]"), "")
        val knownHallucinations = setOf("(", "[", "]", "...", "thankyou", "subtitles", "you", "thanksforwatching", "hello")
        if (knownHallucinations.contains(normalized) || cleaned.length < 2) return ""

        // 2. Character-level repetition check
        val charCounts = cleaned.groupingBy { it }.eachCount()
        val mostFrequentChar = charCounts.maxByOrNull { it.value }
        if (mostFrequentChar != null && mostFrequentChar.value > cleaned.length * 0.4) {
            val distinctChars = charCounts.size
            if (distinctChars < 4 && cleaned.length > 5) return ""
        }

        // 3. Word-level repetition check
        val words = cleaned.split(Regex("[\\s.,!?]+")).filter { it.isNotBlank() }
        if (words.size > 3) {
            val wordCounts = words.groupingBy { it.lowercase() }.eachCount()
            val mostFrequentWord = wordCounts.maxByOrNull { it.value }!!
            
            // If a single word makes up more than 50% of the transcript, it's a loop
            if (mostFrequentWord.value > words.size * 0.5) return ""
        }
        
        return cleaned
    }

    suspend fun transcribe(audioBuffer: ByteArray): String = transcribeLive(audioBuffer)

    override fun close() {
        stt.reset()
    }

    private companion object {
        // Using natural language for the initial prompt instead of internal tokens
        const val MultilingualPrompt = "Transcription of a voice request in Sinhala or English."
    }
}
