package com.yesplaymusic.car.ui

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import com.yesplaymusic.car.data.CookieStore
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.ActivityMainBinding
import com.yesplaymusic.car.playback.PlaybackService
import com.yesplaymusic.car.playback.PlaybackViewModel
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity(), PlaybackHost, DetailNavigator, MvNavigator, LoginFragment.LoginCallback {

  private lateinit var binding: ActivityMainBinding
  private lateinit var viewModel: PlaybackViewModel
  private lateinit var cookieStore: CookieStore

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
    cookieStore = CookieStore.getInstance(this)

    // 初始化 cookie (如果已登录)
    initializeCookie()

    val pagerAdapter = MainPagerAdapter(this)
    binding.viewPager.adapter = pagerAdapter
    binding.viewPager.offscreenPageLimit = 5
    TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
      tab.text = when (position) {
        0 -> getString(R.string.tab_home)
        1 -> getString(R.string.tab_recommend)
        2 -> getString(R.string.tab_search)
        3 -> getString(R.string.tab_player)
        4 -> getString(R.string.tab_queue)
        else -> ""
      }
    }.attach()

    binding.searchShortcut.setOnClickListener {
      binding.viewPager.currentItem = 2
    }

    binding.miniPlayerBar.setOnClickListener {
      binding.viewPager.currentItem = 3
    }
    binding.miniPlayButton.setOnClickListener { togglePlay() }
    binding.miniNextButton.setOnClickListener { skipNext() }
    binding.miniPrevButton.setOnClickListener { skipPrev() }

    viewModel.currentTrack.observe(this) { track ->
      updateMiniPlayer(track)
    }
    viewModel.isPlaying.observe(this) { isPlaying ->
      binding.miniPlayButton.setIconResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
    }
    viewModel.positionMs.observe(this) { updateMiniProgress() }
    viewModel.durationMs.observe(this) { updateMiniProgress() }

    supportFragmentManager.addOnBackStackChangedListener {
      updateDetailVisibility()
    }
    updateDetailVisibility()
  }

  private fun initializeCookie() {
    val savedCookie = cookieStore.getCookie()
    if (!savedCookie.isNullOrBlank()) {
      provider.setCookie(savedCookie)
    }
  }

  fun showLoginScreen() {
    binding.viewPager.visibility = View.GONE
    binding.tabLayout.visibility = View.GONE
    binding.topBar.visibility = View.GONE
    binding.miniPlayerBar.visibility = View.GONE

    supportFragmentManager.beginTransaction()
      .replace(binding.detailContainer.id, LoginFragment())
      .addToBackStack("login")
      .commit()
    binding.detailContainer.visibility = View.VISIBLE
  }

  private fun showMainContent() {
    binding.detailContainer.visibility = View.GONE
    binding.viewPager.visibility = View.VISIBLE
    binding.tabLayout.visibility = View.VISIBLE
    binding.topBar.visibility = View.VISIBLE
    binding.miniPlayerBar.visibility = View.VISIBLE
  }

  override fun onLoginSuccess() {
    android.util.Log.i("MainActivity", "登录成功回调 -> 关闭登录页并刷新首页")
    supportFragmentManager.popBackStack()
    showMainContent()
    // 通知首页刷新用户信息
    refreshHomeFragment()
  }

  private fun refreshHomeFragment() {
    // ViewPager2 + FragmentStateAdapter 使用 "f" + position 作为 tag
    val homeFragment = supportFragmentManager.findFragmentByTag("f0") as? HomeFragment
    homeFragment?.refreshUserInfo()
  }

  override fun onLoginSkipped() {
    supportFragmentManager.popBackStack()
    showMainContent()
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

  override fun openMediaDetail(type: MediaType, item: CoverItem) {
    val fragment = MediaDetailFragment.newInstance(type, item)
    supportFragmentManager.beginTransaction()
      .setReorderingAllowed(true)
      .replace(binding.detailContainer.id, fragment)
      .addToBackStack("media_detail")
      .commit()
    setDetailVisible(true)
  }

  override fun openMv(mvId: Long, title: String, artist: String) {
    if (mvId <= 0L) return
    val fragment = MvFragment.newInstance(mvId, title, artist)
    supportFragmentManager.beginTransaction()
      .setReorderingAllowed(true)
      .replace(binding.detailContainer.id, fragment)
      .addToBackStack("mv_detail")
      .commit()
    setDetailVisible(true)
  }

  private fun updatePlaybackState() {
    val player = controller ?: return
    val isPlaying = player.isPlaying
    viewModel.isPlaying.postValue(isPlaying)

    val status = when (player.playbackState) {
      Player.STATE_IDLE -> getString(R.string.status_idle)
      Player.STATE_BUFFERING -> getString(R.string.status_buffering)
      Player.STATE_READY -> if (isPlaying) getString(R.string.status_playing) else getString(R.string.status_paused)
      Player.STATE_ENDED -> getString(R.string.status_ended)
      else -> ""
    }
    viewModel.statusText.postValue(status)
  }

  private fun updateDetailVisibility() {
    setDetailVisible(supportFragmentManager.backStackEntryCount > 0)
  }

  private fun setDetailVisible(hasDetail: Boolean) {
    binding.detailContainer.visibility = if (hasDetail) View.VISIBLE else View.GONE
    binding.viewPager.visibility = if (hasDetail) View.GONE else View.VISIBLE
    binding.tabLayout.visibility = if (hasDetail) View.GONE else View.VISIBLE
    binding.topBar.visibility = if (hasDetail) View.GONE else View.VISIBLE
  }

  private fun updateMiniPlayer(track: Track?) {
    binding.miniPlayerBar.visibility = View.VISIBLE
    if (track == null) {
      binding.miniTitle.text = getString(R.string.not_playing)
      binding.miniSubtitle.text = ""
      binding.miniCover.setImageResource(R.drawable.ic_launcher_foreground)
      binding.miniPlayButton.isEnabled = false
      binding.miniNextButton.isEnabled = false
      binding.miniPrevButton.isEnabled = false
      binding.miniProgress.progress = 0
      return
    }
    binding.miniTitle.text = track.title
    binding.miniSubtitle.text = track.artist
    binding.miniPlayButton.isEnabled = true
    binding.miniNextButton.isEnabled = true
    binding.miniPrevButton.isEnabled = true
    binding.miniCover.load(track.coverUrl) {
      crossfade(true)
      placeholder(R.drawable.ic_launcher_foreground)
      error(R.drawable.ic_launcher_foreground)
    }
  }

  private fun updateMiniProgress() {
    val duration = viewModel.durationMs.value ?: 0L
    val position = viewModel.positionMs.value ?: 0L
    if (duration <= 0L) {
      binding.miniProgress.progress = 0
      return
    }
    val percent = ((position.coerceAtLeast(0L).toDouble() / duration.toDouble()) * 100.0)
      .toInt()
      .coerceIn(0, 100)
    binding.miniProgress.progress = percent
  }
}
