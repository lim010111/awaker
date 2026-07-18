package com.awaker.exit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

/**
 * 환기 사운드 v0 — 핑크 노이즈 합성 재생 (이슈 06). CONTEXT.md 번들 풀의
 * 백색/핑크 노이즈 항목을 에셋 없이 런타임 합성으로 충당한다 (전체 5~10개
 * 풀·사용자 MP3·수면 모드는 v1 범위로 이연). Paul Kellet economy 필터.
 */
class NoisePlayer {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::playLoop, "awaker-noise").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun playLoop() {
        val sampleRate = 44_100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuf * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track.play()

        val rnd = Random(System.nanoTime())
        val buffer = ShortArray(2_048)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        while (running) {
            for (i in buffer.indices) {
                val white = rnd.nextDouble() * 2 - 1
                b0 = 0.99765 * b0 + white * 0.0990460
                b1 = 0.96300 * b1 + white * 0.2965164
                b2 = 0.57000 * b2 + white * 1.0526913
                val pink = (b0 + b1 + b2 + white * 0.1848) * 0.06
                buffer[i] = (pink.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(buffer, 0, buffer.size)
        }
        runCatching {
            track.stop()
            track.release()
        }
    }
}
