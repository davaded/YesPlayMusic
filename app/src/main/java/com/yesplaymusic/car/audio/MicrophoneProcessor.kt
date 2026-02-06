package com.yesplaymusic.car.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

/**
 * Real-time microphone loopback for karaoke mode.
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
  }

  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null
  @Volatile
  private var isRunning = false
  private var processingThread: Thread? = null
  @Volatile
  private var micGain: Float = DEFAULT_MIC_GAIN

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

      isRunning = true
      audioRecord?.startRecording()
      audioTrack?.play()

      processingThread = Thread {
        val inputBuffer = ShortArray(minBufferSize)
        val outputBuffer = ShortArray(minBufferSize)
        while (isRunning) {
          val readCount = audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: 0
          if (readCount > 0) {
            applyGain(inputBuffer, outputBuffer, readCount, micGain)
            audioTrack?.write(outputBuffer, 0, readCount)
          }
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

  private fun applyGain(input: ShortArray, output: ShortArray, count: Int, gain: Float) {
    for (i in 0 until count) {
      val amplified = (input[i] * gain).toInt()
      output[i] = amplified
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }
  }
}
