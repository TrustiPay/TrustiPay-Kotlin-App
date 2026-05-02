package app.trustipay.voice

import android.os.Build
import java.io.File

object NativeTranscriptionCompatibility {
    fun check(): NativeTranscriptionSupport {
        val supportedAbis = Build.SUPPORTED_64_BIT_ABIS.toSet()
        if ("arm64-v8a" !in supportedAbis) {
            return NativeTranscriptionSupport(
                isSupported = false,
                message = "Local Cactus Whisper STT requires an arm64-v8a device.",
            )
        }

        val cpuInfo = runCatching {
            File("/proc/cpuinfo").readText()
        }.getOrDefault("")
        val features = cpuInfo.lineSequence()
            .filter { it.startsWith("Features") }
            .flatMap { it.substringAfter(':', "").trim().splitToSequence(Regex("\\s+")) }
            .filter { it.isNotBlank() }
            .toSet()

        // Cactus' current Android STT native library can SIGILL on ARMv8.0-only CPUs
        // during spectrogram generation. Require ARMv8.2 FP16 features before calling it.
        val requiredFeatures = setOf("fphp", "asimdhp")
        val missingFeatures = requiredFeatures - features
        if (missingFeatures.isNotEmpty()) {
            return NativeTranscriptionSupport(
                isSupported = false,
                message = "This phone's CPU does not support the native instructions required by Cactus Whisper STT. Use a conservatively built whisper.cpp engine for this device.",
            )
        }

        return NativeTranscriptionSupport(isSupported = true)
    }
}

data class NativeTranscriptionSupport(
    val isSupported: Boolean,
    val message: String = "",
)
