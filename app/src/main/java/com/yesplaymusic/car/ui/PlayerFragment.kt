package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.slider.Slider
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.LyricLine
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.SongDetail
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentPlayerBinding
import com.yesplaymusic.car.playback.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerFragment : Fragment() {

  private var _binding: FragmentPlayerBinding? = null
  private val binding get() = _binding!!
  private val viewModel: PlaybackViewModel by activityViewModels()
  private val provider = ProviderRegistry.get()
  private val lyricsAdapter = LyricsAdapter()
  private var lyrics: List<LyricLine> = emptyList()
  private var lyricsJob: Job? = null
  private var detailJob: Job? = null
  private var lastLyricsIndex = -1
  private var currentDetail: SongDetail? = null
  private var currentMvId: Long = 0L

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentPlayerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.playButton.setOnClickListener { (activity as? PlaybackHost)?.togglePlay() }
    binding.prevButton.setOnClickListener { (activity as? PlaybackHost)?.skipPrev() }
    binding.nextButton.setOnClickListener { (activity as? PlaybackHost)?.skipNext() }
    binding.karaokeButton.setOnClickListener { (activity as? KaraokeNavigator)?.openKaraoke() }

    binding.seekBar.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
      if (fromUser) {
        (activity as? PlaybackHost)?.seekTo(value.toLong())
      }
    }

    binding.lyricsList.layoutManager = LinearLayoutManager(requireContext())
    binding.lyricsList.adapter = lyricsAdapter

    binding.mvButton.setOnClickListener {
      val track = viewModel.currentTrack.value ?: return@setOnClickListener
      if (currentMvId > 0L) {
        (activity as? MvNavigator)?.openMv(currentMvId, track.title, track.artist)
      }
    }

    viewModel.currentTrack.observe(viewLifecycleOwner) { track ->
      binding.title.text = track?.title ?: getString(R.string.not_playing)
      binding.subtitle.text = track?.artist ?: ""
      binding.albumArt.load(track?.coverUrl) {
        crossfade(true)
        placeholder(R.drawable.ic_launcher_foreground)
        error(R.drawable.ic_launcher_foreground)
      }
      updateDetail(track)
      updateLyrics(track)
      updateMvButton(track?.mvId ?: 0L)
    }

    viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
      binding.playButton.setIconResource(
        if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
      )
    }

    viewModel.durationMs.observe(viewLifecycleOwner) { duration ->
      val max = duration.coerceAtLeast(1L).toFloat()
      binding.seekBar.valueTo = max
      binding.totalTime.text = formatDuration(duration)
    }

    viewModel.positionMs.observe(viewLifecycleOwner) { position ->
      if (!binding.seekBar.isPressed) {
        binding.seekBar.value = position.toFloat().coerceAtLeast(0f).coerceAtMost(binding.seekBar.valueTo)
      }
      binding.currentTime.text = formatDuration(position)
      updateLyricsPosition(position)
    }

    viewModel.statusText.observe(viewLifecycleOwner) { status ->
      binding.status.text = status
    }
  }

  private fun updateDetail(track: Track?) {
    detailJob?.cancel()
    if (track == null) {
      currentDetail = null
      binding.songInfo.text = ""
      return
    }
    binding.songInfo.text = buildInfoText(track, null)
    detailJob = viewLifecycleOwner.lifecycleScope.launch {
      val detail = withContext(Dispatchers.IO) { provider.getSongDetail(track.id) }
      if (!isAdded) return@launch
      currentDetail = detail
      binding.songInfo.text = buildInfoText(track, detail)
      updateMvButton(detail?.mvId ?: track.mvId)
    }
  }

  private fun updateLyrics(track: Track?) {
    lyricsJob?.cancel()
    lyrics = emptyList()
    lyricsAdapter.submit(listOf(LyricLine(0L, getString(R.string.lyrics_empty))))
    lastLyricsIndex = -1
    if (track == null) return
    lyricsJob = viewLifecycleOwner.lifecycleScope.launch {
      val lines = withContext(Dispatchers.IO) { provider.getLyrics(track.id) }
      if (!isAdded) return@launch
      lyrics = if (lines.isEmpty()) {
        listOf(LyricLine(0L, getString(R.string.lyrics_empty)))
      } else {
        lines
      }
      lyricsAdapter.submit(lyrics)
      binding.lyricsList.scrollToPosition(0)
      lastLyricsIndex = -1
    }
  }

  private fun updateLyricsPosition(positionMs: Long) {
    if (lyrics.isEmpty()) return
    val index = findLyricIndex(lyrics, positionMs)
    if (index != lastLyricsIndex) {
      lastLyricsIndex = index
      lyricsAdapter.setActiveIndex(index)
      if (index >= 0) {
        binding.lyricsList.smoothScrollToPosition(index)
      }
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

  private fun buildInfoText(track: Track, detail: SongDetail?): String {
    val album = detail?.album ?: track.album.orEmpty()
    val artist = detail?.artist ?: track.artist
    val duration = detail?.durationMs ?: track.durationMs
    val durationText = if (duration > 0) formatDuration(duration) else "--:--"
    return buildString {
      append(getString(R.string.info_artist_label)).append(artist)
      if (album.isNotBlank()) {
        append('\n')
        append(getString(R.string.info_album_label)).append(album)
      }
      append('\n')
      append(getString(R.string.info_duration_label)).append(durationText)
      append('\n')
      append(getString(R.string.info_id_label)).append(track.id)
    }
  }

  private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
  }

  private fun updateMvButton(mvId: Long) {
    currentMvId = mvId
    binding.mvButton.visibility = if (mvId > 0L) View.VISIBLE else View.GONE
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
