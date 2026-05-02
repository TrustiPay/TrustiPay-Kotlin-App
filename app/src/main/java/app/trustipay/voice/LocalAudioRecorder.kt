package app.trustipay.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class LocalAudioRecorder {
    @Volatile
    private var recording = false

    suspend fun recordUntilStopped(
        onChunk: suspend (ByteArray) -> Unit = {},
    ): AudioRecordingResult = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(DefaultBufferBytes)
        val recorder = createRecorder(bufferSize * 2)
        val buffer = ByteArray(bufferSize)
        val output = ByteArrayOutputStream()

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Audio recorder could not be initialized."
        }

        recording = true
        runCatching {
            recorder.startRecording()
        }.onFailure { throwable ->
            recorder.release()
            recording = false
            throw IllegalStateException("Microphone could not start recording.", throwable)
        }

        try {
            while (recording && currentCoroutineContext().isActive) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> {
                        output.write(buffer, 0, read)
                        onChunk(buffer.copyOf(read))
                        if (output.size() >= MaxRecordingBytes) {
                            recording = false
                        }
                    }
                    read < 0 -> throw IllegalStateException("Microphone read failed with code $read.")
                }
            }
        } finally {
            recording = false
            runCatching { recorder.stop() }
            recorder.release()
        }

        AudioRecordingResult(
            audio = output.toByteArray(),
            reachedMaxDuration = output.size() >= MaxRecordingBytes,
        )
    }

    fun stop() {
        recording = false
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(bufferSize: Int): AudioRecord =
        AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

    companion object {
        const val SampleRate = 16_000
        const val MinimumTranscriptionBytes = 32_000
        private const val BytesPerSample = 2
        private const val DefaultBufferBytes = 4_096
        private const val MaxRecordingSeconds = 30
        const val MaxRecordingBytes = SampleRate * BytesPerSample * MaxRecordingSeconds
    }
}

data class AudioRecordingResult(
    val audio: ByteArray,
    val reachedMaxDuration: Boolean,
)
