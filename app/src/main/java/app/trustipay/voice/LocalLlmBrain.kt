package app.trustipay.voice

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLlmBrain internal constructor(
    context: Context,
    private val engineInitializer: LiteRtLmEngineInitializer = LiteRtLmEngineInitializer(),
) : Closeable {
    private val appContext = context.applicationContext
    private val initializeMutex = Mutex()
    private val analysisMutex = Mutex()

    @Volatile
    private var engine: LiteRtLmEngineHandle? = null

    @Volatile
    private var state: LlmAnalysisState = LlmAnalysisState.Missing

    @Volatile
    private var initializationError: String? = null

    fun isModelAvailable(): Boolean = findModelFile() != null

    fun analysisState(): LlmAnalysisState = state

    suspend fun initialize(): LlmAnalysisState = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            if (engine != null) {
                state = LlmAnalysisState.Ready
                return@withLock state
            }

            val modelFile = findModelFile()
            if (modelFile == null) {
                val searchDirs = searchDirectories().joinToString(", ") { it.absolutePath }
                initializationError = "LiteRT-LM model file not found. Ensure one of $PreferredModelFilenames is in: $searchDirs"
                state = LlmAnalysisState.Missing
                return@withLock state
            }

            state = LlmAnalysisState.Initializing
            initializationError = null

            runCatching {
                val initializedEngine = engineInitializer.initialize(
                    modelPath = modelFile.absolutePath,
                    cacheDir = liteRtLmCacheDirectory().absolutePath,
                )
                engine = initializedEngine
                state = LlmAnalysisState.Ready
                Log.d(Tag, "LiteRT-LM initialized with ${modelFile.absolutePath}")
            }.onFailure { throwable ->
                initializationError = throwable.toLlmFriendlyMessage("LiteRT-LM initialization failed.")
                state = LlmAnalysisState.Failed
                closeEngine()
                Log.e(Tag, "LiteRT-LM initialization failed", throwable)
            }

            state
        }
    }

    suspend fun processRequest(transcript: String): LlmAnalysisResult = withContext(Dispatchers.IO) {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isBlank()) {
            return@withContext LlmAnalysisResult.Failure(
                rawTranscript = transcript,
                message = "Cannot analyze an empty transcript.",
            )
        }

        analysisMutex.withLock {
            val initializedState = initialize()
            val currentEngine = engine
            if (initializedState != LlmAnalysisState.Ready || currentEngine == null) {
                return@withLock LlmAnalysisResult.Unavailable(
                    rawTranscript = cleanTranscript,
                    message = initializationError ?: "LiteRT-LM request analysis is unavailable.",
                )
            }

            runCatching {
                currentEngine.createConversation(intentConversationConfig()).use { conversation ->
                    val response = conversation.sendMessage(intentPrompt(cleanTranscript))
                    BankingIntentToolCallParser.parse(
                        toolCalls = response.toolCalls,
                        rawTranscript = cleanTranscript,
                    )
                }
            }.getOrElse { throwable ->
                LlmAnalysisResult.Failure(
                    rawTranscript = cleanTranscript,
                    message = throwable.toLlmFriendlyMessage("LiteRT-LM request analysis failed."),
                )
            }
        }
    }

    private fun findModelFile(): File? {
        val directories = searchDirectories()
        directories.firstExistingNamedModel()?.let { return it }
        return directories.firstExistingLiteRtLmModel()
    }

    private fun searchDirectories(): List<File> = listOfNotNull(
        File(appContext.filesDir, "models"),
        appContext.filesDir,
        appContext.getExternalFilesDir(null)?.let { File(it, "models") },
        appContext.getExternalFilesDir(null),
        File("/data/local/tmp"),
    )

    private fun List<File>.firstExistingNamedModel(): File? {
        for (directory in this) {
            val files = directory.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                if (PreferredModelFilenames.any { it.equals(file.name, ignoreCase = true) }) {
                    return file
                }
            }
        }
        return null
    }

    private fun List<File>.firstExistingLiteRtLmModel(): File? =
        asSequence()
            .flatMap { directory ->
                directory.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && it.extension.equals("litertlm", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?: emptySequence()
            }
            .firstOrNull()

    private fun liteRtLmCacheDirectory(): File =
        File(appContext.cacheDir, "litertlm").also { it.mkdirs() }

    private fun intentConversationConfig(): ConversationConfig =
        ConversationConfig(
            systemInstruction = Contents.of(SystemInstruction),
            tools = listOf(tool(TrustiPayIntentToolSet())),
            automaticToolCalling = false,
        )

    private fun intentPrompt(transcript: String): String =
        """
        Select exactly one TrustiPay banking intent tool for this voice transcript.
        Use send_money for transfers, pay_bill for bill payments, check_balance for balance questions, and unknown_request when unclear.
        Do not answer with prose or JSON. Return only the tool call.

        Voice transcript:
        $transcript
        """.trimIndent()

    override fun close() {
        closeEngine()
        state = LlmAnalysisState.Missing
        initializationError = null
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val Tag = "LocalLlmBrain"
        const val SystemInstruction = "You extract safe draft banking intents for TrustiPay. Never execute a banking action."

        val PreferredModelFilenames = listOf(
            "model.litertlm",
            "Gemma-4-E2B-it.bin",
            "gemma-4-e2b-it.bin",
            "gemma-2b-it-cpu-int4.bin",
            "model.bin",
        )
    }
}

internal class LiteRtLmEngineInitializer(
    private val engineFactory: LiteRtLmEngineFactory = RealLiteRtLmEngineFactory(),
) {
    fun initialize(
        modelPath: String,
        cacheDir: String,
    ): LiteRtLmEngineHandle {
        val gpuFailure = runCatching {
            return createInitializedEngine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = cacheDir,
                )
            )
        }.exceptionOrNull()

        val cpuFailure = runCatching {
            return createInitializedEngine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir,
                )
            )
        }.exceptionOrNull()

        throw IllegalStateException(
            "GPU and CPU LiteRT-LM initialization failed. GPU: ${gpuFailure.messageOrType()}; CPU: ${cpuFailure.messageOrType()}",
            cpuFailure ?: gpuFailure,
        )
    }

    private fun createInitializedEngine(config: EngineConfig): LiteRtLmEngineHandle {
        val engine = engineFactory.create(config)
        return try {
            engine.initialize()
            engine
        } catch (throwable: Throwable) {
            engine.close()
            throw throwable
        }
    }
}

internal interface LiteRtLmEngineFactory {
    fun create(config: EngineConfig): LiteRtLmEngineHandle
}

internal interface LiteRtLmEngineHandle : Closeable {
    fun initialize()
    fun createConversation(config: ConversationConfig): LiteRtLmConversationHandle
}

internal interface LiteRtLmConversationHandle : Closeable {
    fun sendMessage(message: String): Message
}

private class RealLiteRtLmEngineFactory : LiteRtLmEngineFactory {
    override fun create(config: EngineConfig): LiteRtLmEngineHandle =
        RealLiteRtLmEngineHandle(Engine(config))
}

private class RealLiteRtLmEngineHandle(
    private val engine: Engine,
) : LiteRtLmEngineHandle {
    override fun initialize() {
        engine.initialize()
    }

    override fun createConversation(config: ConversationConfig): LiteRtLmConversationHandle =
        RealLiteRtLmConversationHandle(engine.createConversation(config))

    override fun close() {
        engine.close()
    }
}

private class RealLiteRtLmConversationHandle(
    private val conversation: Conversation,
) : LiteRtLmConversationHandle {
    override fun sendMessage(message: String): Message =
        conversation.sendMessage(message)

    override fun close() {
        conversation.close()
    }
}

private fun Throwable?.messageOrType(): String =
    this?.message?.ifBlank { this::class.java.simpleName } ?: "not attempted"

private fun Throwable.toLlmFriendlyMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
