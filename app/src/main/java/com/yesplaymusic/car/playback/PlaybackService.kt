package com.yesplaymusic.car.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yesplaymusic.car.ui.MainActivity

@UnstableApi
class PlaybackService : MediaSessionService() {

  private lateinit var player: ExoPlayer
  private lateinit var session: MediaSession

  override fun onCreate() {
    super.onCreate()

    player = ExoPlayer.Builder(this).build().apply {
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
      setAudioAttributes(audioAttributes, true)
      setHandleAudioBecomingNoisy(true)
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    session = MediaSession.Builder(this, player)
      .setSessionActivity(pendingIntent)
      .build()

    setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

  override fun onDestroy() {
    session.release()
    player.release()
    super.onDestroy()
  }
}
