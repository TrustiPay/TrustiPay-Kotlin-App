package app.trustipay.voice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object PcmAudioPreprocessor {
    private const val BytesPerSample = 2
    private const val FrameMs = 30
    private const val PaddingMs = 240
    const val MinimumSpeechBytes = 24_000

    fun trimToSpeech(
        pcm: ByteArray,
        sampleRate: Int = LocalAudioRecorder.SampleRate,
    ): ByteArray {
        val normalized = trimToCompleteSamples(pcm)
        if (normalized.size < MinimumSpeechBytes) return ByteArray(0)

        val frameBytes = ((sampleRate * BytesPerSample * FrameMs) / 1000)
            .coerceAtLeast(BytesPerSample)
            .let { it - (it % BytesPerSample) }
        val paddingBytes = ((sampleRate * BytesPerSample * PaddingMs) / 1000)
            .let { it - (it % BytesPerSample) }

        val frameRms = mutableListOf<Double>()
        var frameStart = 0
        while (frameStart + BytesPerSample <= normalized.size) {
            val frameEnd = min(frameStart + frameBytes, normalized.size)
            frameRms.add(rms(normalized, frameStart, frameEnd))
            frameStart += frameBytes
        }

        if (frameRms.isEmpty()) return ByteArray(0)

        val maxRms = frameRms.maxOrNull() ?: 0.0
        if (maxRms < 300.0) return ByteArray(0)

        val sortedRms = frameRms.sorted()
        val noiseFloor = sortedRms[(sortedRms.size * 0.25).toInt().coerceIn(0, sortedRms.lastIndex)]
        val speechThreshold = maxOf(250.0, noiseFloor * 2.1, maxRms * 0.08)

        val firstSpeechFrame = frameRms.indexOfFirst { it >= speechThreshold }
        val lastSpeechFrame = frameRms.indexOfLast { it >= speechThreshold }
        if (firstSpeechFrame == -1 || lastSpeechFrame == -1) return ByteArray(0)

        val start = max(0, firstSpeechFrame * frameBytes - paddingBytes)
        val end = min(normalized.size, (lastSpeechFrame + 1) * frameBytes + paddingBytes)
            .let { it - (it % BytesPerSample) }

        if (end <= start) return ByteArray(0)
        val speech = normalized.copyOfRange(start, end)
        return if (speech.size >= MinimumSpeechBytes) speech else ByteArray(0)
    }

    fun durationSeconds(pcm: ByteArray): Double =
        trimToCompleteSamples(pcm).size.toDouble() / (LocalAudioRecorder.SampleRate * BytesPerSample)

    fun trimToCompleteSamples(pcm: ByteArray): ByteArray =
        if (pcm.size % BytesPerSample == 0) pcm else pcm.copyOf(pcm.size - 1)

    private fun rms(
        pcm: ByteArray,
        start: Int,
        end: Int,
    ): Double {
        var sum = 0.0
        var samples = 0
        var index = start
        val safeEnd = min(end, pcm.size - 1)
        while (index < safeEnd) {
            val low = pcm[index].toInt() and 0xFF
            val high = pcm[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            samples += 1
            index += BytesPerSample
        }
        return if (samples == 0) 0.0 else sqrt(sum / samples)
    }
}
