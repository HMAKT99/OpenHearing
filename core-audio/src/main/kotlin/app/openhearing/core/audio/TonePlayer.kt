package app.openhearing.core.audio

import android.media.AudioAttributes
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays a generated tone buffer through the Android audio path (AudioTrack, float
 * PCM). The configured [OutputLimiter] is applied to every buffer **before** it
 * reaches the device — there is no output path that bypasses it (see docs/SAFETY.md).
 *
 * [stop] is the instant-mute hook: it pauses and flushes immediately, and any
 * in-flight [play] returns promptly.
 *
 * This class is the thin Android I/O shell; the testable tone math lives in
 * [ToneGenerator]. Not unit-tested here (requires a device) — see the Phase 1
 * device-test steps.
 */
class TonePlayer(
    private val sampleRateHz: Int = ToneGenerator.DEFAULT_SAMPLE_RATE_HZ,
    private val limiter: OutputLimiter = HardCeilingLimiter(DEFAULT_OUTPUT_CEILING),
) {
    @Volatile private var track: AudioTrack? = null

    @Volatile private var stopped = false

    /**
     * Limit, then play [buffer] to completion (mono). Suspends until the tone has
     * finished, [stop] is called, or the coroutine is cancelled.
     */
    suspend fun play(buffer: FloatArray) = withContext(Dispatchers.IO) {
        // SAFETY-CRITICAL: clamp every sample to the ceiling before output.
        limiter.processInPlace(buffer)
        stopped = false

        val minBytes =
            AudioTrack.getMinBufferSize(
                sampleRateHz,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT,
            )
        val bytes = maxOf(minBytes, buffer.size * Float.SIZE_BYTES)

        val t = build(bytes)
        track = t
        try {
            t.play()
            var offset = 0
            while (offset < buffer.size && !stopped) {
                coroutineContext.ensureActive()
                val written = t.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            // Wait for the device to actually drain (respecting stop/cancel).
            while (!stopped && t.playbackHeadPosition < buffer.size) {
                coroutineContext.ensureActive()
                Thread.sleep(DRAIN_POLL_MS)
            }
        } finally {
            releaseTrack(t)
        }
    }

    /** Instant mute: pause + flush now; a running [play] returns promptly. */
    fun stop() {
        stopped = true
        track?.let {
            runCatching {
                it.pause()
                it.flush()
            }
        }
    }

    /** Release any audio resources. Safe to call repeatedly. */
    fun release() {
        stop()
        track?.let { runCatching { it.release() } }
        track = null
    }

    private fun build(bufferBytes: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            android.media.AudioFormat.Builder()
                .setSampleRate(sampleRateHz)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()
        .also { it.setVolume(AudioTrack.getMaxVolume()) }

    private fun releaseTrack(t: AudioTrack) {
        runCatching {
            if (t.playState != AudioTrack.PLAYSTATE_STOPPED) t.stop()
        }
        runCatching { t.release() }
        if (track === t) track = null
    }

    companion object {
        /**
         * Backstop ceiling for the limiter on the playback path. Tones are already
         * generated well below this; the limiter catches anything pathological
         * before it can reach full scale.
         */
        const val DEFAULT_OUTPUT_CEILING = 0.9f
        private const val DRAIN_POLL_MS = 5L
    }
}
