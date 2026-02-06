package com.yesplaymusic.car.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.yesplaymusic.car.R
import com.yesplaymusic.car.audio.MicrophoneProcessor
import com.yesplaymusic.car.data.LyricLine
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.databinding.FragmentKaraokeBinding
import com.yesplaymusic.car.playback.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KaraokeFragment : Fragment() {

  private var _binding: FragmentKaraokeBinding? = null
  private val binding get() = _binding!!

  private val viewModel: PlaybackViewModel by activityViewModels()
  private val provider = ProviderRegistry.get()

  private var lyrics: List<LyricLine> = emptyList()
  private var lastLyricIndex = -1
  private var lyricsJob: Job? = null

  // 麦克风处理器
  private var micProcessor: MicrophoneProcessor? = null

  // 权限请求
  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      startMicrophone()
    } else {
      Toast.makeText(requireContext(), getString(R.string.mic_permission_denied), Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentKaraokeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 退出按钮
    binding.exitButton.setOnClickListener {
      parentFragmentManager.popBackStack()
    }

    // 请求麦克风权限并启动
    checkAndRequestMicPermission()

    // 播放控制
    binding.playButton.setOnClickListener {
      (activity as? PlaybackHost)?.togglePlay()
    }
    binding.prevButton.setOnClickListener {
      (activity as? PlaybackHost)?.skipPrev()
    }
    binding.nextButton.setOnClickListener {
      (activity as? PlaybackHost)?.skipNext()
    }

    // 进度条拖动
    binding.seekBar.addOnChangeListener { _, value, fromUser ->
      if (fromUser) {
        (activity as? PlaybackHost)?.seekTo(value.toLong())
      }
    }

    // 观察当前歌曲
    viewModel.currentTrack.observe(viewLifecycleOwner) { track ->
      loadBlurBackground(track?.coverUrl)
      loadLyrics(track?.id ?: 0L)
    }

    // 观察播放位置
    viewModel.positionMs.observe(viewLifecycleOwner) { position ->
      updateLyricDisplay(position)
      // 更新进度条
      if (!binding.seekBar.isPressed) {
        binding.seekBar.value = position.toFloat()
          .coerceAtLeast(0f)
          .coerceAtMost(binding.seekBar.valueTo)
      }
      // 更新时间显示
      updateTimeDisplay(position, viewModel.durationMs.value ?: 0L)
    }

    // 观察播放时长
    viewModel.durationMs.observe(viewLifecycleOwner) { duration ->
      val max = duration.coerceAtLeast(1L).toFloat()
      binding.seekBar.valueTo = max
    }

    // 观察播放状态
    viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
      binding.playButton.setIconResource(
        if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
      )
    }
  }

  private fun loadBlurBackground(url: String?) {
    binding.blurBackground.load(url) {
      crossfade(true)
      placeholder(R.drawable.ic_launcher_foreground)
      error(R.drawable.ic_launcher_foreground)
    }
  }

  private fun loadLyrics(trackId: Long) {
    lyricsJob?.cancel()
    lyrics = emptyList()
    lastLyricIndex = -1
    binding.lyricCurrent.text = "♪"
    binding.lyricPrev.text = ""
    binding.lyricNext.text = ""

    if (trackId <= 0L) return

    lyricsJob = viewLifecycleOwner.lifecycleScope.launch {
      val lines = withContext(Dispatchers.IO) { provider.getLyrics(trackId) }
      if (!isAdded) return@launch
      lyrics = lines
      if (lyrics.isEmpty()) {
        binding.lyricCurrent.text = getString(R.string.lyrics_empty)
      }
    }
  }

  private fun updateLyricDisplay(positionMs: Long) {
    if (lyrics.isEmpty()) return

    val index = findLyricIndex(lyrics, positionMs)
    if (index != lastLyricIndex) {
      lastLyricIndex = index

      val prev = lyrics.getOrNull(index - 1)?.text ?: ""
      val current = lyrics.getOrNull(index)?.text ?: "♪"
      val next = lyrics.getOrNull(index + 1)?.text ?: ""

      // 带淡入淡出动画更新歌词
      binding.lyricCurrent.animate()
        .alpha(0f)
        .setDuration(150)
        .withEndAction {
          binding.lyricCurrent.text = current
          binding.lyricCurrent.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
        }
        .start()

      binding.lyricPrev.text = prev
      binding.lyricNext.text = next
    }
  }

  private fun findLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
      val mid = (low + high) / 2
      val time = lines[mid].timeMs
      if (time <= positionMs) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return result
  }

  private fun updateTimeDisplay(positionMs: Long, durationMs: Long) {
    val current = formatDuration(positionMs)
    val total = formatDuration(durationMs)
    binding.timeDisplay.text = "$current / $total"
  }

  private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    lyricsJob?.cancel()
    // 释放麦克风资源
    micProcessor?.release()
    micProcessor = null
    _binding = null
  }

  private fun checkAndRequestMicPermission() {
    when {
      ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED -> {
        startMicrophone()
      }
      else -> {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    }
  }

  private fun startMicrophone() {
    if (micProcessor == null) {
      micProcessor = MicrophoneProcessor()
    }
    val success = micProcessor?.start() ?: false
    if (success) {
      Toast.makeText(requireContext(), getString(R.string.mic_enabled), Toast.LENGTH_SHORT).show()
    }
  }
}
