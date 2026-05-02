package app.trustipay.voice

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmBrain(private val context: Context) {
    private var llmInference: LlmInference? = null

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
            val file = File(dir, MODEL_FILENAME)
            if (file.exists()) return file
        }
        return null
    }

    fun getModelPath(): String? = findModelFile()?.absolutePath

    fun initialize() {
        if (llmInference != null) return
        val modelPath = getModelPath() ?: return

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .build()
        
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun processRequest(transcript: String): String = withContext(Dispatchers.IO) {
        val llm = llmInference ?: return@withContext "{\"error\": \"LLM not initialized or model missing\"}"
        
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
        // Updated to match the Gemma model mentioned by the user
        private const val MODEL_FILENAME = "gemma-4-e2b-it.bin"
    }
}
