package app.trustipay.voice

import com.cactus.CactusInitParams
import com.cactus.CactusModelManager
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.TranscriptionMode
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

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
            val speechAudio = PcmAudioPreprocessor.trimToSpeech(audioBuffer)
            if (speechAudio.isEmpty()) return@withLock ""
            val speechDurationSeconds = PcmAudioPreprocessor.durationSeconds(speechAudio)

            val result = withContext(Dispatchers.IO) {
                stt.transcribe(
                    prompt = MultilingualPrompt,
                    params = CactusTranscriptionParams(model = modelSlug),
                    mode = TranscriptionMode.LOCAL,
                    audioBuffer = speechAudio,
                    onToken = null,
                )
            } ?: error("Cactus STT returned null result or crashed. Internal state reset performed.")

            if (!result.success) {
                val errorMsg = result.errorMessage ?: "Unknown error"
                Log.e("CactusSTT", "Transcription failed: $errorMsg (Model: $modelSlug)")
                error("Transcription failed: $errorMsg")
            }

            val cleanText = sanitizeWhisperHallucination(
                text = result.text?.trim().orEmpty(),
                audioDurationSeconds = speechDurationSeconds,
            )
            if (cleanText.isNotBlank()) {
                onPartialText(cleanText)
            }
            cleanText
        }
    }

    private fun sanitizeWhisperHallucination(
        text: String,
        audioDurationSeconds: Double,
    ): String {
        val cleaned = text
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace(Regex("</?[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        
        val normalized = cleaned.lowercase().replace(Regex("[.\\s/_-]"), "")
        val knownHallucinations = setOf(
            "(", "[", "]", "...", "thankyou", "subtitles", "you", "thanksforwatching", 
            "hello", "insin", "insinhala", "inthesame", "thesamefor", "divdivdiv", "div", "(c)", "c", ""
        )
        if (knownHallucinations.contains(normalized) ||
            cleaned.all { it == '.' || it == ' ' || it == '/' || it == '(' || it == ')' } ||
            cleaned.length < 2 ||
            (cleaned.startsWith("(") && cleaned.endsWith(")") && cleaned.length < 5)
        ) {
            return ""
        }

        if (Regex("(?i)(\\bdiv\\b[\\s/.,]*){3,}").containsMatchIn(cleaned)) return ""
        if (Regex("(?i)(the same for\\s*){2,}").containsMatchIn(cleaned)) return ""

        val words = cleaned.split(Regex("[\\s.,!?/]+")).filter { it.isNotBlank() }
        val maxReasonableWords = max(12, (audioDurationSeconds * 4.8).roundToInt() + 6)
        if (words.size > maxReasonableWords && isLoopingTranscript(words)) return ""

        if (words.size > 4) {
            if (isLoopingTranscript(words)) return ""
        }

        if (words.size > 2) {
            val wordCounts = words.groupingBy { it.lowercase() }.eachCount()
            val mostFrequentWord = wordCounts.maxByOrNull { it.value }!!
            if (mostFrequentWord.value > words.size * 0.42 && mostFrequentWord.key.length > 2) return ""
        }

        val charCounts = cleaned.groupingBy { it }.eachCount()
        val mostFrequentChar = charCounts.maxByOrNull { it.value }
        if (mostFrequentChar != null && mostFrequentChar.value > cleaned.length * 0.3) { 
            val distinctChars = charCounts.size
            if (distinctChars < 4 && cleaned.length > 3) return ""
        }
        
        return cleaned
    }

    private fun isLoopingTranscript(words: List<String>): Boolean {
        val normalizedWords = words.map { it.lowercase() }
        for (gramSize in 1..5) {
            if (normalizedWords.size < gramSize * 3) continue
            val nGrams = normalizedWords.windowed(gramSize).map { it.joinToString(" ") }
            val mostCommon = nGrams.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val coveredWords = mostCommon.value * gramSize
            if (mostCommon.value >= 3 && coveredWords >= normalizedWords.size * 0.45) {
                return true
            }
        }
        return false
    }

    suspend fun transcribe(
        audioBuffer: ByteArray,
        onPartialText: suspend (String) -> Unit = {},
    ): String = transcribeLive(audioBuffer, onPartialText)

    override fun close() {
        stt.reset()
    }

    private companion object {
        const val MultilingualPrompt = "<|startoftranscript|><|transcribe|><|notimestamps|>"
    }
}
