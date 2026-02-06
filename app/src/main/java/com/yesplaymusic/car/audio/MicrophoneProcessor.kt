package com.yesplaymusic.car.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

/**
 * 实时音频处理器 - 麦克风采集并实时播放
 * 实现 KTV 效果：用户唱歌 → 麦克风采集 → 实时从扬声器播放
 */
class MicrophoneProcessor {

  companion object {
    private const val TAG = "MicrophoneProcessor"
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
  }

  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null
  private var isRunning = false
  private var processingThread: Thread? = null

  private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
    .takeIf { it > 0 }
    ?: run {
      // 部分设备会返回错误码，回退到 100ms 缓冲避免崩溃
      val fallback = SAMPLE_RATE / 10
      Log.w(TAG, "getMinBufferSize 返回非法值，使用回退缓冲: $fallback")
      fallback
    }

  /**
   * 开始实时音频处理
   */
  fun start(): Boolean {
    if (isRunning) return true

    try {
      // 创建录音器
      audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_IN,
        AUDIO_FORMAT,
        minBufferSize * 2
      )

      if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord 初始化失败")
        release()
        return false
      }

      // 创建播放器
      audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        SAMPLE_RATE,
        CHANNEL_OUT,
        AUDIO_FORMAT,
        minBufferSize * 2,
        AudioTrack.MODE_STREAM
      )

      if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
        Log.e(TAG, "AudioTrack 初始化失败")
        release()
        return false
      }

      isRunning = true
      audioRecord?.startRecording()
      audioTrack?.play()

      // 启动处理线程
      processingThread = Thread {
        val buffer = ShortArray(minBufferSize)
        while (isRunning) {
          val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
          if (readCount > 0) {
            audioTrack?.write(buffer, 0, readCount)
          }
        }
      }.apply {
        priority = Thread.MAX_PRIORITY
        start()
      }

      Log.i(TAG, "麦克风实时处理已启动")
      return true

    } catch (e: SecurityException) {
      Log.e(TAG, "没有录音权限", e)
      release()
      return false
    } catch (e: Exception) {
      Log.e(TAG, "启动失败", e)
      release()
      return false
    }
  }

  /**
   * 停止实时音频处理
   */
  fun stop() {
    isRunning = false
    processingThread?.interrupt()
    processingThread = null

    try {
      audioRecord?.stop()
      audioTrack?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "停止时出错", e)
    }

    Log.i(TAG, "麦克风实时处理已停止")
  }

  /**
   * 释放资源
   */
  fun release() {
    stop()
    audioRecord?.release()
    audioTrack?.release()
    audioRecord = null
    audioTrack = null
    Log.i(TAG, "资源已释放")
  }

  /**
   * 是否正在运行
   */
  fun isRunning(): Boolean = isRunning
}
