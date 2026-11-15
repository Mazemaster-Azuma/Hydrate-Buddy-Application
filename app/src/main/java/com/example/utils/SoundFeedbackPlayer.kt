package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

object SoundFeedbackPlayer {

    private val previewMutex = Mutex()
    @Volatile
    private var activePreviewPlayer: MediaPlayer? = null

    /**
     * Synthesizes a realistic, refreshing liquid water-droplet chime offline.
     * Safely ignores any device audio restrictions or emulator absence.
     */
    suspend fun playWaterDropChime(isGoalReached: Boolean = false) = withContext(Dispatchers.Default) {
        runCatching {
            val sampleRate = 22050
            val durationMs = if (isGoalReached) 420 else 220
            val numSamples = (sampleRate * durationMs) / 1000
            val buf = DoubleArray(numSamples)

            addWaterBubble(buf, sampleRate, startSec = 0.0, fStart = 500.0, fEnd = 1240.0, durSec = 0.085, amp = 0.45)

            if (isGoalReached) {
                addWaterBubble(buf, sampleRate, startSec = 0.095, fStart = 680.0, fEnd = 1580.0, durSec = 0.085, amp = 0.40)
                addCrystalBell(buf, sampleRate, startSec = 0.14, freq = 1046.50, durSec = 0.27, amp = 0.28)
                addCrystalBell(buf, sampleRate, startSec = 0.20, freq = 1567.98, durSec = 0.21, amp = 0.22)
            } else {
                addCrystalBell(buf, sampleRate, startSec = 0.06, freq = 1046.50, durSec = 0.15, amp = 0.18)
            }

            val samples = toPcm16(buf)
            playPcmStatic(sampleRate, samples, durationMs, AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        }
    }

    /**
     * Plays a single non-overlapping preview of the bundled Water-Harp & Droplet alarm chime.
     */
    suspend fun playAlarmChimePreview(context: Context) = withContext(Dispatchers.IO) {
        stopAlarmChimePreview()
        var playerRef: MediaPlayer? = null
        val playedRaw = runCatching {
            val afd = context.resources.openRawResourceFd(R.raw.hydrate_alarm_chime) ?: return@runCatching false
            val player = MediaPlayer()
            playerRef = player
            afd.use { descriptor ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                player.isLooping = false
                player.prepare()
            }
            previewMutex.withLock {
                activePreviewPlayer = player
            }
            player.start()
            val duration = player.duration.coerceIn(800, 4500)
            delay(duration.toLong())
            true
        }.getOrDefault(false)

        if (playerRef != null) {
            previewMutex.withLock {
                if (activePreviewPlayer === playerRef) {
                    activePreviewPlayer = null
                }
            }
            runCatching {
                if (playerRef?.isPlaying == true) playerRef?.stop()
                playerRef?.release()
            }
        }

        if (!playedRaw) {
            playAlarmPulse()
        }
    }

    fun stopAlarmChimePreview() {
        val current = activePreviewPlayer
        activePreviewPlayer = null
        if (current != null) {
            runCatching {
                if (current.isPlaying) current.stop()
                current.release()
            }
        }
    }

    /**
     * Synthesizes a multi-droplet crystal water-harp cascade used as an alarm fallback
     * if raw MediaPlayer playback is unavailable.
     */
    suspend fun playAlarmPulse() = withContext(Dispatchers.Default) {
        runCatching {
            val sampleRate = 22050
            val durationMs = 1500
            val numSamples = (sampleRate * durationMs) / 1000
            val buf = DoubleArray(numSamples)

            addWaterBubble(buf, sampleRate, startSec = 0.02, fStart = 480.0, fEnd = 1140.0, durSec = 0.08, amp = 0.44)
            addWaterBubble(buf, sampleRate, startSec = 0.13, fStart = 640.0, fEnd = 1480.0, durSec = 0.08, amp = 0.42)
            addCrystalBell(buf, sampleRate, startSec = 0.22, freq = 523.25, durSec = 0.55, amp = 0.34)
            addCrystalBell(buf, sampleRate, startSec = 0.38, freq = 659.25, durSec = 0.55, amp = 0.34)
            addCrystalBell(buf, sampleRate, startSec = 0.54, freq = 783.99, durSec = 0.65, amp = 0.36)
            addCrystalBell(buf, sampleRate, startSec = 0.72, freq = 1046.50, durSec = 0.75, amp = 0.38)

            val samples = toPcm16(buf)
            playPcmStatic(sampleRate, samples, durationMs, AudioAttributes.USAGE_ALARM)
        }
    }

    private fun addWaterBubble(
        buf: DoubleArray,
        sampleRate: Int,
        startSec: Double,
        fStart: Double,
        fEnd: Double,
        durSec: Double,
        amp: Double
    ) {
        val startIdx = (startSec * sampleRate).toInt()
        val n = (durSec * sampleRate).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until n) {
            val idx = startIdx + i
            if (idx >= buf.size) break
            val t = i.toDouble() / n.toDouble()
            val freq = fStart * (fEnd / fStart).pow(t.pow(0.85))
            phase += 2.0 * PI * freq / sampleRate
            val attack = (t / 0.08).coerceAtMost(1.0)
            val env = attack * exp(-4.0 * t) * (1.0 - t)
            buf[idx] += (sin(phase) + 0.18 * sin(2.0 * phase)) * env * amp
        }
    }

    private fun addCrystalBell(
        buf: DoubleArray,
        sampleRate: Int,
        startSec: Double,
        freq: Double,
        durSec: Double,
        amp: Double
    ) {
        val startIdx = (startSec * sampleRate).toInt()
        val n = (durSec * sampleRate).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val idx = startIdx + i
            if (idx >= buf.size) break
            val tSec = i.toDouble() / sampleRate
            val t = i.toDouble() / n.toDouble()
            val attack = (tSec / 0.012).coerceAtMost(1.0)
            val env = attack * exp(-3.5 * tSec) * (1.0 - t.pow(6.0))
            val signal = sin(2.0 * PI * freq * tSec) +
                0.30 * sin(2.0 * PI * freq * 2.0 * tSec) * exp(-5.0 * tSec)
            buf[idx] += signal * env * amp
        }
    }

    private fun toPcm16(buf: DoubleArray): ShortArray {
        val peak = buf.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0
        val scale = if (peak > 0.88) 0.88 / peak else 1.0
        return ShortArray(buf.size) { i ->
            (buf[i] * scale * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private suspend fun playPcmStatic(
        sampleRate: Int,
        samples: ShortArray,
        durationMs: Int,
        usage: Int
    ) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(samples, 0, samples.size)
                track.play()
                delay(durationMs.toLong() + 40L)
                track.stop()
            }
        } finally {
            runCatching { track.release() }
        }
    }
}
