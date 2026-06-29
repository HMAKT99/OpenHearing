package app.openhearing.core.audio

import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.media.AudioFormat as PlatformAudioFormat

/**
 * Low-latency capture -> process -> playback loop for assist mode, backed by
 * AudioRecord + AudioTrack (float PCM, mono). The supplied [AudioProcessor] runs
 * on a dedicated urgent-audio thread, block by block; its last stage is the
 * safety limiter (see [app.openhearing.core.audio.dsp.HearingAssistChain]).
 *
 * Requires the RECORD_AUDIO permission (the caller must hold it before [start]).
 * This is the Android I/O shell; all DSP is the testable pure-Kotlin core. Verify
 * latency and behaviour on-device (see docs/DEVICE_TESTING.md) — it can't be
 * unit-tested here.
 */
class AndroidAudioEngine : AudioEngine {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    override val isRunning: Boolean get() = running

    override fun start(format: AudioFormat, processor: AudioProcessor) {
        if (running) return
        running = true
        thread =
            Thread({ runLoop(format, processor) }, "OpenHearing-Assist").apply {
                start()
            }
    }

    override fun stop() {
        running = false
        thread?.join(STOP_JOIN_TIMEOUT_MS)
        thread = null
    }

    // Fail-closed on a missing permission: we deliberately swallow and go silent
    // rather than risk any uncontrolled audio path.
    @Suppress("SwallowedException")
    private fun runLoop(format: AudioFormat, processor: AudioProcessor) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val channelIn = PlatformAudioFormat.CHANNEL_IN_MONO
        val channelOut = PlatformAudioFormat.CHANNEL_OUT_MONO
        val encoding = PlatformAudioFormat.ENCODING_PCM_FLOAT
        val bytesPerBlock = format.framesPerBlock * Float.SIZE_BYTES

        val recordBytes =
            maxOf(AudioRecord.getMinBufferSize(format.sampleRateHz, channelIn, encoding), bytesPerBlock * 4)
        val trackBytes =
            maxOf(AudioTrack.getMinBufferSize(format.sampleRateHz, channelOut, encoding), bytesPerBlock * 4)

        var record: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            record =
                AudioRecord.Builder()
                    // VOICE_COMMUNICATION enables the platform AEC/NS where available,
                    // which also helps suppress feedback.
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(
                        PlatformAudioFormat.Builder()
                            .setSampleRate(format.sampleRateHz)
                            .setEncoding(encoding)
                            .setChannelMask(channelIn)
                            .build(),
                    )
                    .setBufferSizeInBytes(recordBytes)
                    .build()

            track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        PlatformAudioFormat.Builder()
                            .setSampleRate(format.sampleRateHz)
                            .setEncoding(encoding)
                            .setChannelMask(channelOut)
                            .build(),
                    )
                    .setBufferSizeInBytes(trackBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()

            val block = FloatArray(format.framesPerBlock)
            record.startRecording()
            track.play()

            while (running) {
                val read = record.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                // Process exactly the frames we read.
                val toProcess = if (read == block.size) block else block.copyOf(read)
                processor.process(toProcess)
                track.write(toProcess, 0, read, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: SecurityException) {
            // Missing RECORD_AUDIO permission — fail closed (silent), never loud.
            running = false
        } finally {
            record?.runCatchingRelease()
            track?.runCatchingRelease { stop() }
        }
    }

    private fun AudioRecord.runCatchingRelease() {
        runCatching { if (recordingState != AudioRecord.RECORDSTATE_STOPPED) stop() }
        runCatching { release() }
    }

    private inline fun AudioTrack.runCatchingRelease(pre: AudioTrack.() -> Unit) {
        runCatching { pre() }
        runCatching { release() }
    }

    private companion object {
        const val STOP_JOIN_TIMEOUT_MS = 500L
    }
}
