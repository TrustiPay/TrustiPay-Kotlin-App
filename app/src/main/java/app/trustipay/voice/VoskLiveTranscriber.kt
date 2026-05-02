package app.trustipay.voice

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.Closeable
import java.io.IOException
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
            // Vosk requires a model directory. We assume it's in assets/vosk-model
            // and use StorageService to unpack it if needed.
            StorageService.unpack(context, "vosk-model", "model") { unpackedModel ->
                model = unpackedModel
                recognizer = Recognizer(model, 16000.0f)
                isInitialized = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun feedAudio(data: ByteArray, len: Int): String {
        if (!isInitialized || recognizer == null) return ""
        
        if (recognizer!!.acceptWaveform(data, len)) {
            val result = recognizer!!.result
            return parseVoskText(result)
        } else {
            val partial = recognizer!!.partialResult
            return parseVoskPartial(partial)
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
