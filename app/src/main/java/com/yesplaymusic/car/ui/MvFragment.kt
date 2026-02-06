package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.databinding.FragmentMvBinding
import com.yesplaymusic.car.playback.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MvFragment : Fragment() {

  private var _binding: FragmentMvBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()
  private val viewModel: PlaybackViewModel by activityViewModels()

  private var player: ExoPlayer? = null
  private var pendingMvUrl: String? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentMvBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val mvId = arguments?.getLong(ARG_MV_ID) ?: 0L
    val title = arguments?.getString(ARG_TITLE).orEmpty()
    val artist = arguments?.getString(ARG_ARTIST).orEmpty()

    binding.mvToolbar.title = if (artist.isBlank()) title else "$title · $artist"
    binding.mvToolbar.setNavigationOnClickListener {
      parentFragmentManager.popBackStack()
    }

    if (mvId <= 0L) {
      binding.mvStatus.text = getString(R.string.mv_unavailable)
      return
    }

    if (viewModel.isPlaying.value == true) {
      (activity as? PlaybackHost)?.togglePlay()
    }

    viewLifecycleOwner.lifecycleScope.launch {
      binding.mvStatus.visibility = View.VISIBLE
      binding.mvStatus.text = getString(R.string.detail_loading)
      val url = try {
        withContext(Dispatchers.IO) { provider.getMvUrl(mvId) }
      } catch (e: Exception) {
        android.util.Log.e("MvFragment", "获取 MV 地址失败: mvId=$mvId", e)
        ""
      }
      if (!isAdded) return@launch
      if (url.isBlank()) {
        binding.mvStatus.text = getString(R.string.mv_unavailable)
        return@launch
      }
      pendingMvUrl = url
      startPlaybackIfReady()
    }
  }

  override fun onStart() {
    super.onStart()
    player = ExoPlayer.Builder(requireContext()).build()
    binding.mvPlayerView.player = player
    startPlaybackIfReady()
  }

  private fun startPlaybackIfReady() {
    val url = pendingMvUrl ?: return
    val currentPlayer = player ?: return
    val mediaItem = MediaItem.fromUri(url)
    currentPlayer.setMediaItem(mediaItem)
    currentPlayer.prepare()
    currentPlayer.playWhenReady = true
    pendingMvUrl = null
    binding.mvStatus.visibility = View.GONE
  }

  override fun onStop() {
    super.onStop()
    binding.mvPlayerView.player = null
    player?.release()
    player = null
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    private const val ARG_MV_ID = "arg_mv_id"
    private const val ARG_TITLE = "arg_title"
    private const val ARG_ARTIST = "arg_artist"

    fun newInstance(mvId: Long, title: String, artist: String): MvFragment {
      return MvFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_MV_ID, mvId)
          putString(ARG_TITLE, title)
          putString(ARG_ARTIST, artist)
        }
      }
    }
  }
}
