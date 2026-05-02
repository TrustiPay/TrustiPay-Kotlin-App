package app.trustipay.voice

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VoskLiveTranscriber(private val context: Context) : Closeable {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            StorageService.unpack(context, "vosk-model", "model", 
                { unpackedModel ->
                    model = unpackedModel
                    try {
                        recognizer = Recognizer(model, 16000.0f)
                        isInitialized = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                { exception ->
                    exception.printStackTrace()
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun feedAudio(data: ByteArray, len: Int): String {
        val currentRecognizer = recognizer
        if (!isInitialized || currentRecognizer == null) return ""
        
        // Final attempt at fixing the method signature for 0.3.75
        // It likely requires shortArrayOf() or specific casting
        return if (currentRecognizer.acceptWaveForm(data, len)) {
            parseVoskText(currentRecognizer.result)
        } else {
            parseVoskPartial(currentRecognizer.partialResult)
        }
    }

    private fun parseVoskText(json: String): String {
        return try {
            JSONObject(json).getString("text")
        } catch (e: Exception) { "" } 
    }

    private fun parseVoskPartial(json: String): String {
        return try {
            JSONObject(json).getString("partial")
        } catch (e: Exception) { "" }
    }

    override fun close() {
        recognizer?.close()
        model?.close()
        isInitialized = false
    }
}
