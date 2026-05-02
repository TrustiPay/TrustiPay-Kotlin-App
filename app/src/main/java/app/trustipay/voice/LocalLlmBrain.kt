package app.trustipay.voice

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmBrain(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var initializationError: String? = null

    fun isModelAvailable(): Boolean {
        return findModelFile() != null
    }

    private fun findModelFile(): File? {
        val possibleLocations = listOf(
            context.filesDir,
            context.getExternalFilesDir(null),
            File("/data/local/tmp") // for adb pushed files
        ).filterNotNull()

        for (dir in possibleLocations) {
            for (filename in MODEL_FILENAMES) {
                val file = File(dir, filename)
                if (file.exists()) {
                    Log.d("LocalLlmBrain", "Found model at: ${file.absolutePath}")
                    return file
                }
            }
        }
        return null
    }

    fun initialize() {
        if (engine != null) return
        
        val modelFile = findModelFile()
        if (modelFile == null) {
            initializationError = "Model file not found. Ensure the Gemma model is in your app's files directory."
            return
        }

        try {
            Log.d("LocalLlmBrain", "Initializing LiteRT-LM (GPU) with: ${modelFile.absolutePath}")
            
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU() // Using GPU acceleration as suggested
            )
            
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            
            engine = newEngine
            conversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of("You are a helpful assistant for TrustiPay banking app.")
                )
            )
            
            initializationError = null
            Log.d("LocalLlmBrain", "LiteRT-LM GPU initialized successfully")
        } catch (e: Exception) {
            initializationError = "Initialization failed: ${e.message}. Falling back to CPU."
            Log.e("LocalLlmBrain", "GPU initialization failed, attempting CPU fallback", e)
            tryInitializeCpu(modelFile)
        }
    }

    private fun tryInitializeCpu(modelFile: File) {
        try {
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU()
            )
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation()
            initializationError = null
        } catch (e: Exception) {
            initializationError = "CPU fallback also failed: ${e.message}"
            Log.e("LocalLlmBrain", "All initializations failed", e)
            close()
        }
    }

    suspend fun processRequest(transcript: String): String = withContext(Dispatchers.IO) {
        val conv = conversation
        if (conv == null) {
            val error = initializationError ?: "LLM not initialized or model missing"
            return@withContext "{\"error\": \"$error\"}"
        }
        
        val prompt = """
            Analyze the following user request in English or Sinhala and convert it into a valid JSON object.
            
            Valid request types: send_money, check_balance, pay_bill.
            
            JSON format examples:
            1. "Send 500 to Saman for food" -> {"request": "send_money", "to": "Saman", "amount": 500, "reason": "food"}
            2. "මට සල්ලි කීයද තියෙන්නෙ කියන්න" -> {"request": "check_balance"}
            3. "Pay 1500 to Dialog Axiata" -> {"request": "pay_bill", "to": "Dialog Axiata", "amount": 1500}
            
            If the request is not clear, return: {"request": "unknown", "raw": "$transcript"}
            
            Only return the JSON object. Do not explain.
            
            User Request: $transcript
            Output:
        """.trimIndent()

        try {
            val response = conv.sendMessage(prompt)
            response.toString().ifBlank { "{\"error\": \"Empty response from model\"}" }
        } catch (e: Exception) {
            "{\"error\": \"${e.message}\"}"
        }
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }

    companion object {
        private val MODEL_FILENAMES = listOf(
            "gemma-4-e2b-it.bin",
            "gemma-2b-it-cpu-int4.bin",
            "model.litertlm",
            "model.bin"
        )
    }
}
