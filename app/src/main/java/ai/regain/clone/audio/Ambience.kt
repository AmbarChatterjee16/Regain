package ai.regain.clone.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small procedural ambience engine. Everything is synthesised on the fly so the APK
 * stays tiny and there is no licensing baggage. Sounds are intentionally subtle.
 */
object Ambience {
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile private var running = false
    @Volatile private var kind: String = "none"
    @Volatile private var volume: Float = 0.5f

    val isRunning: Boolean get() = running

    fun start(kind: String, volume: Float) {
        if (kind == "none" || kind.isBlank()) {
            stop()
            return
        }
        this.kind = kind
        this.volume = volume.coerceIn(0f, 1f)
        if (running) return

        runCatching {
            val sampleRate = 44100
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, sampleRate / 8 * 4)
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            track = t
            running = true
            t.play()
            worker = Thread { renderLoop(t, sampleRate) }.also {
                it.priority = Thread.NORM_PRIORITY
                it.isDaemon = true
                it.start()
            }
        }.onFailure { stop() }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    fun stop() {
        running = false
        val t = track
        track = null
        runCatching { worker?.join(250) }
        worker = null
        runCatching { t?.pause() }
        runCatching { t?.flush() }
        runCatching { t?.stop() }
        runCatching { t?.release() }
    }

    private fun renderLoop(t: AudioTrack, sampleRate: Int) {
        val frames = 1024
        val buffer = ShortArray(frames * 2)
        var phase = 0.0
        var brownL = 0.0
        var brownR = 0.0
        var lp = 0.0
        var droplet = 0.0
        val twoPi = 2.0 * PI

        while (running) {
            val amp = volume
            for (i in 0 until frames) {
                val time = (phase + i) / sampleRate
                var l = 0.0
                var r = 0.0
                when (kind) {
                    "white" -> {
                        l = (Random.nextDouble() * 2 - 1) * 0.10
                        r = (Random.nextDouble() * 2 - 1) * 0.10
                    }
                    "deep" -> {
                        brownL = (brownL + (Random.nextDouble() * 2 - 1) * 0.04).coerceIn(-1.0, 1.0) * 0.995
                        brownR = (brownR + (Random.nextDouble() * 2 - 1) * 0.04).coerceIn(-1.0, 1.0) * 0.995
                        l = brownL * 0.55
                        r = brownR * 0.55
                    }
                    "rain" -> {
                        val n = Random.nextDouble() * 2 - 1
                        lp += (n - lp) * 0.18
                        if (Random.nextDouble() < 0.00035) droplet = 0.5
                        droplet *= 0.9994
                        l = lp * 0.12 + sin(twoPi * 900 * time) * droplet * 0.05
                        r = lp * 0.12 + sin(twoPi * 1150 * time) * droplet * 0.045
                    }
                    "lofi" -> {
                        val chord = sin(twoPi * 220 * time) * 0.05 +
                            sin(twoPi * 261.63 * time) * 0.045 +
                            sin(twoPi * 329.63 * time) * 0.04
                        val tremolo = 0.65 + 0.35 * sin(twoPi * 0.12 * time)
                        brownL = (brownL + (Random.nextDouble() * 2 - 1) * 0.03).coerceIn(-1.0, 1.0) * 0.995
                        brownR = (brownR + (Random.nextDouble() * 2 - 1) * 0.03).coerceIn(-1.0, 1.0) * 0.995
                        l = chord * tremolo + brownL * 0.22
                        r = chord * tremolo * 0.92 + brownR * 0.22
                    }
                    "space" -> {
                        val swell = 0.55 + 0.45 * sin(twoPi * 0.045 * time)
                        l = sin(twoPi * 110 * time) * 0.10 * swell +
                            sin(twoPi * 164.81 * time) * 0.05 * (1.05 - swell)
                        r = sin(twoPi * 110.4 * time) * 0.10 * swell +
                            sin(twoPi * 220 * time) * 0.04 * (1.05 - swell)
                    }
                    "binaural" -> {
                        l = sin(twoPi * 200 * time) * 0.10
                        r = sin(twoPi * 210 * time) * 0.10
                    }
                    else -> {
                        l = 0.0
                        r = 0.0
                    }
                }
                buffer[i * 2] = (l * amp * 32767 * 0.9).toInt().coerceIn(-32768, 32767).toShort()
                buffer[i * 2 + 1] = (r * amp * 32767 * 0.9).toInt().coerceIn(-32768, 32767).toShort()
            }
            phase += frames.toDouble()
            runCatching { t.write(buffer, 0, buffer.size) }
            if (!running) break
        }
    }

    data class Sound(val id: String, val label: String, val subtitle: String)

    val sounds = listOf(
        Sound("none", "None", "Silence"),
        Sound("lofi", "Lofi", "Warm lo-fi chords"),
        Sound("deep", "Deep Focus", "Brown noise wash"),
        Sound("rain", "Rain", "Soft rain droplets"),
        Sound("white", "White Noise", "Mask the room"),
        Sound("space", "Space", "Slow drifting drones"),
        Sound("binaural", "Binaural", "200/210 Hz focus pair · headphones")
    )
}
