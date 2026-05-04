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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

class LocalWhisperTranscriber(
    private val modelSlug: String,
) : Closeable {
    private val stt = CactusSTT()
    private val transcriptionMutex = Mutex()

    fun isModelDownloaded(): Boolean =
        CactusModelManager.isModelDownloaded(modelSlug)

    fun isVoskModelDownloaded(): Boolean {
        val voskDir = File(modelStorageDirectory(), VoskModelFolder)
        return voskDir.exists() && voskDir.isDirectory && voskDir.listFiles()?.isNotEmpty() == true
    }

    fun deleteModel(): Boolean {
        val whisperDeleted = CactusModelManager.deleteModel(modelSlug)
        val voskDir = File(modelStorageDirectory(), VoskModelFolder)
        val voskDeleted = if (voskDir.exists()) voskDir.deleteRecursively() else false
        return whisperDeleted || voskDeleted
    }

    fun modelStorageDirectory(): String =
        CactusModelManager.getModelsDirectory()

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        stt.downloadModel(modelSlug)
    }

    suspend fun downloadVoskModel() = withContext(Dispatchers.IO) {
        if (isVoskModelDownloaded()) return@withContext

        val modelsDir = File(modelStorageDirectory())
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val tempZip = File(modelsDir, "vosk_tmp.zip")
        try {
            Log.d("VoskDownloader", "Downloading Vosk model from $VoskModelUrl")
            val connection = URL(VoskModelUrl).openConnection() as HttpURLConnection
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Failed to download Vosk model: ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("VoskDownloader", "Extracting Vosk model...")
            extractZipTo(tempZip, modelsDir)
            
            // The zip extracts to a folder like "vosk-model-small-en-us-0.15"
            // Find it and rename to "vosk-model"
            val extractedDir = modelsDir.listFiles { f -> f.isDirectory && f.name.contains("vosk-model") && f.name != VoskModelFolder }
                ?.firstOrNull()
            
            if (extractedDir != null) {
                val targetDir = File(modelsDir, VoskModelFolder)
                if (targetDir.exists()) targetDir.deleteRecursively()
                extractedDir.renameTo(targetDir)
                Log.d("VoskDownloader", "Vosk model setup complete at ${targetDir.absolutePath}")
            } else {
                error("Vosk model extraction failed: could not find extracted directory.")
            }

        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun extractZipTo(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    suspend fun initializeDownloadedModel() = withContext(Dispatchers.IO) {
        check(isModelDownloaded()) {
            "Voice model $modelSlug is not downloaded."
        }
        stt.initializeModel(CactusInitParams(model = modelSlug))
    }

    suspend fun transcribeLive(
        audioBuffer: ByteArray,
        prompt: String = MultilingualPrompt,
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
                    prompt = prompt,
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
            "hello", "insin", "insinhala", "inthesame", "thesamefor", "divdivdiv", "div", "(c)", "c", "",
            "සිංහල", "sinhala", "english", "thankyouforwatching", "please", "likeandsubscribe",
            "ස්තූතියි", "උපසිරැසි", "ස්තුතියි", "බොහොම ස්තූතියි"
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
        prompt: String = MultilingualPrompt,
        onPartialText: suspend (String) -> Unit = {},
    ): String = transcribeLive(audioBuffer, prompt, onPartialText)

    override fun close() {
        stt.reset()
    }

    companion object {
        const val MultilingualPrompt = "<|startoftranscript|><|transcribe|><|notimestamps|>Sinhala: මට සල්ලි යවන්න ඕනේ. English: I want to send money to Saman. TrustiPay."
        const val SinhalaPrompt = "<|startoftranscript|><|si|><|transcribe|><|notimestamps|>මට සල්ලි යවන්න ඕනේ."
        const val EnglishPrompt = "<|startoftranscript|><|en|><|transcribe|><|notimestamps|>I want to send money."
        const val VoskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        const val VoskModelFolder = "vosk-model"
    }
}
