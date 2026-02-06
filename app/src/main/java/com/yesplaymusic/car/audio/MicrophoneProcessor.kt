package com.yesplaymusic.car.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time microphone loopback for karaoke mode.
 *
 * Processing order:
 * 1) Anti-howling pre-process (high-pass + gate)
 * 2) AGC gain (stabilize loudness)
 * 3) User mic gain
 * 4) Reverb (optional, wet mix)
 * 5) Limiter and output clamp
 */
class MicrophoneProcessor {

  companion object {
    private const val TAG = "MicrophoneProcessor"
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private const val DEFAULT_MIC_GAIN = 2.2f
    private const val MIN_MIC_GAIN = 1.0f
    private const val MAX_MIC_GAIN = 6.0f

    private const val DEFAULT_AGC_ENABLED = true
    private const val DEFAULT_AGC_TARGET_LEVEL = 0.18f
    private const val MIN_AGC_TARGET_LEVEL = 0.08f
    private const val MAX_AGC_TARGET_LEVEL = 0.45f

    private const val DEFAULT_ANTI_HOWLING_ENABLED = true
    private const val DEFAULT_ANTI_HOWLING_STRENGTH = 0.55f
    private const val MIN_ANTI_HOWLING_STRENGTH = 0.0f
    private const val MAX_ANTI_HOWLING_STRENGTH = 1.0f

    private const val DEFAULT_REVERB_ENABLED = true
    private const val DEFAULT_REVERB_MIX = 0.28f
    private const val MIN_REVERB_MIX = 0.0f
    private const val MAX_REVERB_MIX = 0.65f
  }

  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null
  private var acousticEchoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null

  @Volatile
  private var isRunning = false
  private var processingThread: Thread? = null

  @Volatile
  private var micGain: Float = DEFAULT_MIC_GAIN
  @Volatile
  private var agcEnabled: Boolean = DEFAULT_AGC_ENABLED
  @Volatile
  private var agcTargetLevel: Float = DEFAULT_AGC_TARGET_LEVEL
  @Volatile
  private var antiHowlingEnabled: Boolean = DEFAULT_ANTI_HOWLING_ENABLED
  @Volatile
  private var antiHowlingStrength: Float = DEFAULT_ANTI_HOWLING_STRENGTH
  @Volatile
  private var reverbEnabled: Boolean = DEFAULT_REVERB_ENABLED
  @Volatile
  private var reverbMix: Float = DEFAULT_REVERB_MIX

  private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
    .takeIf { it > 0 }
    ?: run {
      val fallback = SAMPLE_RATE / 10
      Log.w(TAG, "Invalid min buffer size, fallback to: $fallback")
      fallback
    }

  fun start(): Boolean {
    if (isRunning) return true

    try {
      audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_IN,
        AUDIO_FORMAT,
        minBufferSize * 2
      )

      if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord init failed")
        release()
        return false
      }

      audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        SAMPLE_RATE,
        CHANNEL_OUT,
        AUDIO_FORMAT,
        minBufferSize * 2,
        AudioTrack.MODE_STREAM
      )

      if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
        Log.e(TAG, "AudioTrack init failed")
        release()
        return false
      }

      initPlatformEffects(audioRecord?.audioSessionId ?: 0)

      isRunning = true
      audioRecord?.startRecording()
      audioTrack?.play()

      processingThread = Thread {
        val inputBuffer = ShortArray(minBufferSize)
        val outputBuffer = ShortArray(minBufferSize)
        val reverbEngine = ReverbEngine(SAMPLE_RATE)
        var agcGain = 1.0f
        var previousInput = 0.0f
        var previousOutput = 0.0f

        while (isRunning) {
          val readCount = audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: 0
          if (readCount <= 0) continue

          if (agcEnabled) {
            val rms = computeRms(inputBuffer, readCount)
            val desiredGain = (agcTargetLevel / (rms + 1e-4f)).coerceIn(0.35f, 8.0f)
            val smoothing = if (desiredGain < agcGain) 0.35f else 0.08f
            agcGain += (desiredGain - agcGain) * smoothing
          } else {
            agcGain += (1.0f - agcGain) * 0.15f
          }

          val state = processBuffer(
            input = inputBuffer,
            output = outputBuffer,
            count = readCount,
            agcGain = agcGain,
            previousInput = previousInput,
            previousOutput = previousOutput,
            reverbEngine = reverbEngine
          )
          previousInput = state.previousInput
          previousOutput = state.previousOutput

          audioTrack?.write(outputBuffer, 0, readCount)
        }
      }.apply {
        priority = Thread.MAX_PRIORITY
        start()
      }

      Log.i(TAG, "Microphone processor started")
      return true
    } catch (e: SecurityException) {
      Log.e(TAG, "No record audio permission", e)
      release()
      return false
    } catch (e: Exception) {
      Log.e(TAG, "Start failed", e)
      release()
      return false
    }
  }

  fun stop() {
    isRunning = false
    processingThread?.interrupt()
    processingThread = null

    try {
      audioRecord?.stop()
      audioTrack?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "Stop failed", e)
    }

    Log.i(TAG, "Microphone processor stopped")
  }

  fun release() {
    stop()
    releasePlatformEffects()
    audioRecord?.release()
    audioTrack?.release()
    audioRecord = null
    audioTrack = null
    Log.i(TAG, "Microphone resources released")
  }

  fun isRunning(): Boolean = isRunning

  fun setMicGain(gain: Float) {
    micGain = gain.coerceIn(MIN_MIC_GAIN, MAX_MIC_GAIN)
  }

  fun getMicGain(): Float = micGain

  fun setAgcEnabled(enabled: Boolean) {
    agcEnabled = enabled
  }

  fun isAgcEnabled(): Boolean = agcEnabled

  fun setAgcTargetLevel(level: Float) {
    agcTargetLevel = level.coerceIn(MIN_AGC_TARGET_LEVEL, MAX_AGC_TARGET_LEVEL)
  }

  fun getAgcTargetLevel(): Float = agcTargetLevel

  fun setAntiHowlingEnabled(enabled: Boolean) {
    antiHowlingEnabled = enabled
    updatePlatformEffectState()
  }

  fun isAntiHowlingEnabled(): Boolean = antiHowlingEnabled

  fun setAntiHowlingStrength(strength: Float) {
    antiHowlingStrength = strength.coerceIn(MIN_ANTI_HOWLING_STRENGTH, MAX_ANTI_HOWLING_STRENGTH)
  }

  fun getAntiHowlingStrength(): Float = antiHowlingStrength

  fun setReverbEnabled(enabled: Boolean) {
    reverbEnabled = enabled
  }

  fun isReverbEnabled(): Boolean = reverbEnabled

  fun setReverbMix(mix: Float) {
    reverbMix = mix.coerceIn(MIN_REVERB_MIX, MAX_REVERB_MIX)
  }

  fun getReverbMix(): Float = reverbMix

  private data class FilterState(
    val previousInput: Float,
    val previousOutput: Float
  )

  private fun processBuffer(
    input: ShortArray,
    output: ShortArray,
    count: Int,
    agcGain: Float,
    previousInput: Float,
    previousOutput: Float,
    reverbEngine: ReverbEngine
  ): FilterState {
    val localMicGain = micGain
    val antiEnabled = antiHowlingEnabled
    val antiStrength = antiHowlingStrength
    val localReverbEnabled = reverbEnabled
    val localReverbMix = reverbMix
    val totalGain = agcGain * localMicGain

    val highPassAlpha = 0.985f - antiStrength * 0.02f
    val gateThreshold = 160.0f + antiStrength * 2200.0f
    val gateFloor = 1.0f - antiStrength * 0.92f
    val limiterThreshold = (0.95f - antiStrength * 0.22f) * Short.MAX_VALUE

    var prevIn = previousInput
    var prevOut = previousOutput
    for (i in 0 until count) {
      var sample = input[i].toFloat()

      if (antiEnabled) {
        val filtered = highPassAlpha * (prevOut + sample - prevIn)
        prevIn = sample
        prevOut = filtered
        sample = if (abs(filtered) < gateThreshold) filtered * gateFloor else filtered
      }

      sample *= totalGain

      if (localReverbEnabled) {
        sample = reverbEngine.process(sample, localReverbMix)
      }

      sample = softLimit(sample, limiterThreshold)

      output[i] = sample
        .toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    return FilterState(previousInput = prevIn, previousOutput = prevOut)
  }

  private fun softLimit(sample: Float, threshold: Float): Float {
    val absSample = abs(sample)
    if (absSample <= threshold) return sample
    val compressed = threshold + (absSample - threshold) / 4.0f
    return if (sample >= 0.0f) compressed else -compressed
  }

  private fun computeRms(input: ShortArray, count: Int): Float {
    if (count <= 0) return 0.0f
    var sumSquares = 0.0
    for (i in 0 until count) {
      val normalized = input[i] / 32768.0
      sumSquares += normalized * normalized
    }
    return sqrt(sumSquares / count).toFloat()
  }

  private class ReverbEngine(sampleRate: Int) {
    private val delaySizes = intArrayOf(
      (sampleRate * 0.031f).toInt().coerceAtLeast(64),
      (sampleRate * 0.043f).toInt().coerceAtLeast(64),
      (sampleRate * 0.057f).toInt().coerceAtLeast(64)
    )
    private val feedback = floatArrayOf(0.58f, 0.52f, 0.47f)
    private val buffers = Array(delaySizes.size) { i -> FloatArray(delaySizes[i]) }
    private val indices = IntArray(delaySizes.size)
    private val dampMemory = FloatArray(delaySizes.size)

    fun process(input: Float, wet: Float): Float {
      if (wet <= 0.001f) return input
      var acc = 0.0f

      for (i in delaySizes.indices) {
        val idx = indices[i]
        val delayed = buffers[i][idx]
        val damped = delayed * 0.65f + dampMemory[i] * 0.35f
        dampMemory[i] = damped
        buffers[i][idx] = input + damped * feedback[i]
        indices[i] = (idx + 1) % buffers[i].size
        acc += damped
      }

      val reverb = acc / delaySizes.size
      val wetMix = wet.coerceIn(0.0f, 0.65f)
      val dryMix = 1.0f - wetMix
      return input * dryMix + reverb * wetMix * 1.1f
    }
  }

  private fun initPlatformEffects(sessionId: Int) {
    releasePlatformEffects()
    if (sessionId == 0) return

    if (AcousticEchoCanceler.isAvailable()) {
      acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)
    }
    if (NoiseSuppressor.isAvailable()) {
      noiseSuppressor = NoiseSuppressor.create(sessionId)
    }
    updatePlatformEffectState()
  }

  private fun updatePlatformEffectState() {
    val enabled = antiHowlingEnabled
    runCatching { acousticEchoCanceler?.enabled = enabled }
    runCatching { noiseSuppressor?.enabled = enabled }
  }

  private fun releasePlatformEffects() {
    runCatching { acousticEchoCanceler?.release() }
    runCatching { noiseSuppressor?.release() }
    acousticEchoCanceler = null
    noiseSuppressor = null
  }
}
