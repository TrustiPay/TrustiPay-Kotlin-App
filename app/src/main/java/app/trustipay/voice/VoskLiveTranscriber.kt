package app.trustipay.voice

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoskLiveTranscriber(private val context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val lock = Any()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var committedText = ""

    @Volatile
    private var currentStatus = VoskLiveStatus.NotInitialized

    val isReady: Boolean
        get() = currentStatus.isReady

    fun status(): VoskLiveStatus = currentStatus

    suspend fun initialize(): VoskLiveStatus = withContext(Dispatchers.IO) {
        val existingStatus = currentStatus
        if (existingStatus.isReady) return@withContext existingStatus

        updateStatus(VoskLiveStatus.Initializing)

        // Try bundled assets first
        val assetModelDirectory = findBundledAssetModelDirectory()
        if (assetModelDirectory != null) {
            val unpackedModel = runCatching {
                unpackModel(assetModelDirectory)
            }.getOrElse { throwable ->
                return@withContext updateStatus(
                    VoskLiveStatus.unavailable(
                        "Vosk model '$assetModelDirectory' could not be unpacked: ${throwable.message.orEmpty()}"
                    )
                )
            }
            return@withContext finishInitialization(unpackedModel)
        }

        // Try downloaded model in internal storage
        val downloadedModelDir = File(appContext?.filesDir, "models/vosk-model")
        if (downloadedModelDir.exists() && downloadedModelDir.isDirectory) {
            val loadedModel = runCatching {
                Model(downloadedModelDir.absolutePath)
            }.getOrElse { throwable ->
                return@withContext updateStatus(
                    VoskLiveStatus.unavailable(
                        "Downloaded Vosk model could not be loaded: ${throwable.message.orEmpty()}"
                    )
                )
            }
            return@withContext finishInitialization(loadedModel)
        }

        updateStatus(
            VoskLiveStatus.unavailable(
                "No Vosk model found. It will be downloaded with the Whisper model."
            )
        )
    }

    private fun finishInitialization(loadedModel: Model): VoskLiveStatus {
        val newRecognizer = runCatching {
            createRecognizer(loadedModel)
        }.getOrElse { throwable ->
            loadedModel.close()
            return updateStatus(
                VoskLiveStatus.unavailable(
                    "Vosk recognizer could not be created: ${throwable.message.orEmpty()}"
                )
            )
        }

        synchronized(lock) {
            recognizer?.close()
            model?.close()
            model = loadedModel
            recognizer = newRecognizer
            committedText = ""
            currentStatus = VoskLiveStatus.Ready
        }
        return VoskLiveStatus.Ready
    }

    fun resetSession() {
        synchronized(lock) {
            committedText = ""
            val currentModel = model ?: return
            recognizer?.close()
            recognizer = runCatching {
                createRecognizer(currentModel)
            }.getOrElse { throwable ->
                currentStatus = VoskLiveStatus.unavailable(
                    "Vosk recognizer could not be reset: ${throwable.message.orEmpty()}"
                )
                null
            }
        }
    }

    fun feedAudio(data: ByteArray, len: Int): String {
        if (data.isEmpty()) return ""
        val safeLength = len.coerceIn(0, data.size)
        if (safeLength == 0) return ""

        return synchronized(lock) {
            val currentRecognizer = recognizer ?: return@synchronized ""
            if (!currentStatus.isReady) return@synchronized ""

            if (currentRecognizer.acceptWaveForm(data, safeLength)) {
                appendFinalText(parseTextField(currentRecognizer.result, "text"))
                committedText
            } else {
                combineText(committedText, parseTextField(currentRecognizer.partialResult, "partial"))
            }
        }
    }

    private fun findBundledAssetModelDirectory(): String? =
        AssetModelDirectories.firstOrNull { directory ->
            runCatching {
                appContext.assets.list(directory)?.isNotEmpty() == true
            }.getOrDefault(false)
        }

    private suspend fun unpackModel(assetModelDirectory: String): Model =
        suspendCancellableCoroutine { continuation ->
            StorageService.unpack(
                appContext,
                assetModelDirectory,
                TargetModelDirectory,
                { unpackedModel ->
                    if (continuation.isActive) {
                        continuation.resume(unpackedModel)
                    } else {
                        unpackedModel.close()
                    }
                },
                { exception: IOException ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }

    private fun createRecognizer(model: Model): Recognizer =
        Recognizer(model, LocalAudioRecorder.SampleRate.toFloat())

    private fun appendFinalText(text: String) {
        if (text.isBlank()) return
        committedText = combineText(committedText, text)
    }

    private fun parseTextField(json: String, field: String): String {
        return try {
            normalizeText(JSONObject(json).optString(field))
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateStatus(status: VoskLiveStatus): VoskLiveStatus {
        currentStatus = status
        return status
    }

    override fun close() {
        synchronized(lock) {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            committedText = ""
            currentStatus = VoskLiveStatus.NotInitialized
        }
    }

    private companion object {
        val AssetModelDirectories = listOf("model-en-us", "vosk-model")
        const val TargetModelDirectory = "vosk-live-model"
    }
}

data class VoskLiveStatus(
    val isReady: Boolean,
    val message: String,
) {
    companion object {
        val NotInitialized = VoskLiveStatus(
            isReady = false,
            message = "Vosk live transcription has not been initialized.",
        )
        val Initializing = VoskLiveStatus(
            isReady = false,
            message = "Preparing Vosk live transcription.",
        )
        val Ready = VoskLiveStatus(
            isReady = true,
            message = "Vosk live transcription is ready.",
        )

        fun unavailable(message: String) = VoskLiveStatus(
            isReady = false,
            message = message.ifBlank { "Vosk live transcription is unavailable." },
        )
    }
}

private fun combineText(first: String, second: String): String =
    listOf(first, second)
        .map(::normalizeText)
        .filter { it.isNotBlank() }
        .joinToString(" ")

private fun normalizeText(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()
