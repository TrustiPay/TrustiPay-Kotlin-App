package app.trustipay.voice

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmBrain(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var initializationError: String? = null

    fun isModelAvailable(): Boolean {
        return findModelFile() != null
    }

    private fun findModelFile(): File? {
        val possibleLocations = listOf(
            context.filesDir,
            context.getExternalFilesDir(null),
            File("/data/local/tmp"), // for adb pushed files
            context.cacheDir,
            // Search in Cactus models directory too as a fallback
            File(context.filesDir, "cactus_models")
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
        Log.w("LocalLlmBrain", "No model found in searched directories: $possibleLocations")
        return null
    }

    fun initialize() {
        if (llmInference != null) return
        
        val modelFile = findModelFile()
        if (modelFile == null) {
            initializationError = "Model file not found. Please ensure one of $MODEL_FILENAMES is in ${context.filesDir} or ${context.getExternalFilesDir(null)}"
            return
        }

        try {
            Log.d("LocalLlmBrain", "Initializing LLM with: ${modelFile.absolutePath}")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            initializationError = null
            Log.d("LocalLlmBrain", "LLM initialized successfully")
        } catch (e: Exception) {
            initializationError = "Initialization failed: ${e.message}"
            Log.e("LocalLlmBrain", "Failed to create LlmInference", e)
        }
    }

    suspend fun processRequest(transcript: String): String = withContext(Dispatchers.IO) {
        val llm = llmInference
        if (llm == null) {
            val error = initializationError ?: "LLM not initialized or model missing"
            return@withContext "{\"error\": \"$error\"}"
        }
        
        val prompt = """
            You are an offline banking assistant for TrustiPay. 
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
            llm.generateResponse(prompt)
        } catch (e: Exception) {
            "{\"error\": \"${e.message}\"}"
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private val MODEL_FILENAMES = listOf(
            "gemma-2b-it-cpu-int4.bin",
            "gemma-2b-it-gpu-int4.bin",
            "gemma-1.1-2b-it-cpu-int4.bin",
            "gemma-4-e2b-it.bin", // User's specific name
            "model.bin"
        )
    }
}
