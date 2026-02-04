package com.yesplaymusic.car.ui

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.tabs.TabLayoutMediator
import com.google.common.util.concurrent.ListenableFuture
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.ActivityMainBinding
import com.yesplaymusic.car.playback.PlaybackService
import com.yesplaymusic.car.playback.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), PlaybackHost {

  private lateinit var binding: ActivityMainBinding
  private lateinit var viewModel: PlaybackViewModel

  private var controller: MediaController? = null
  private var controllerFuture: ListenableFuture<MediaController>? = null

  private val provider = ProviderRegistry.get()
  private val queue = mutableListOf<Track>()
  private var queueIndex: Int = -1

  private val handler = Handler(Looper.getMainLooper())
  private val progressRunnable = object : Runnable {
    override fun run() {
      val player = controller ?: return
      viewModel.positionMs.postValue(player.currentPosition.coerceAtLeast(0))
      viewModel.durationMs.postValue(player.duration.coerceAtLeast(0))
      handler.postDelayed(this, 500)
    }
  }

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      updatePlaybackState()
      if (playbackState == Player.STATE_ENDED) {
        skipNext()
      }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      viewModel.isPlaying.postValue(isPlaying)
      updatePlaybackState()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
      updatePlaybackState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      updatePlaybackState()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    viewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]

    val pagerAdapter = MainPagerAdapter(this)
    binding.viewPager.adapter = pagerAdapter
    binding.viewPager.offscreenPageLimit = 4
    TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
      tab.text = when (position) {
        0 -> getString(R.string.tab_home)
        1 -> getString(R.string.tab_search)
        2 -> getString(R.string.tab_player)
        3 -> getString(R.string.tab_queue)
        else -> ""
      }
    }.attach()
  }

  override fun onStart() {
    super.onStart()
    connectController()
  }

  override fun onStop() {
    super.onStop()
    disconnectController()
  }

  private fun connectController() {
    val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
    controllerFuture = MediaController.Builder(this, token).buildAsync()
    controllerFuture?.addListener(
      {
        controller = controllerFuture?.get()
        controller?.addListener(playerListener)
        updatePlaybackState()
        handler.post(progressRunnable)
      },
      ContextCompat.getMainExecutor(this)
    )
  }

  private fun disconnectController() {
    handler.removeCallbacks(progressRunnable)
    controller?.removeListener(playerListener)
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    controller = null
  }

  override fun playQueue(tracks: List<Track>, index: Int) {
    if (tracks.isEmpty() || index !in tracks.indices) return
    queue.clear()
    queue.addAll(tracks)
    queueIndex = index
    viewModel.queue.postValue(queue.toList())
    viewModel.queueIndex.postValue(queueIndex)
    playTrackAt(index)
  }

  override fun playSingle(track: Track) {
    playQueue(listOf(track), 0)
  }

  private fun playTrackAt(index: Int) {
    val player = controller ?: return
    if (index !in queue.indices) return
    val track = queue[index]
    queueIndex = index
    viewModel.queueIndex.postValue(queueIndex)
    viewModel.currentTrack.postValue(track)

    lifecycleScope.launch {
      viewModel.statusText.postValue(getString(R.string.resolving))
      val stream = withContext(Dispatchers.IO) { provider.resolveStream(track.id) }
      if (stream.url.isBlank()) {
        viewModel.statusText.postValue(getString(R.string.unplayable))
        return@launch
      }
      val mediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(stream.url)
        .setMediaMetadata(
          MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.coverUrl?.let { android.net.Uri.parse(it) })
            .build()
        )
        .build()
      player.setMediaItem(mediaItem)
      player.prepare()
      player.play()
    }
  }

  override fun togglePlay() {
    val player = controller ?: return
    if (player.isPlaying) player.pause() else player.play()
  }

  override fun skipNext() {
    if (queueIndex + 1 < queue.size) {
      playTrackAt(queueIndex + 1)
    }
  }

  override fun skipPrev() {
    if (queueIndex - 1 >= 0) {
      playTrackAt(queueIndex - 1)
    }
  }

  override fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs)
  }

  private fun updatePlaybackState() {
    val player = controller ?: return
    val isPlaying = player.isPlaying
    viewModel.isPlaying.postValue(isPlaying)

    val status = when (player.playbackState) {
      Player.STATE_IDLE -> "就绪"
      Player.STATE_BUFFERING -> "缓冲中…"
      Player.STATE_READY -> if (isPlaying) "播放中" else "已暂停"
      Player.STATE_ENDED -> "播放结束"
      else -> ""
    }
    viewModel.statusText.postValue(status)
  }
}
