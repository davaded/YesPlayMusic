package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import coil.load
import com.google.android.material.slider.Slider
import com.yesplaymusic.car.R
import com.yesplaymusic.car.databinding.FragmentPlayerBinding
import com.yesplaymusic.car.playback.PlaybackViewModel

class PlayerFragment : Fragment() {

  private var _binding: FragmentPlayerBinding? = null
  private val binding get() = _binding!!
  private val viewModel: PlaybackViewModel by activityViewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentPlayerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.playButton.setOnClickListener { (activity as? PlaybackHost)?.togglePlay() }
    binding.prevButton.setOnClickListener { (activity as? PlaybackHost)?.skipPrev() }
    binding.nextButton.setOnClickListener { (activity as? PlaybackHost)?.skipNext() }

    binding.seekBar.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
      if (fromUser) {
        (activity as? PlaybackHost)?.seekTo(value.toLong())
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
    }

    viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
      binding.playButton.text = if (isPlaying) getString(R.string.pause) else getString(R.string.play)
    }

    viewModel.durationMs.observe(viewLifecycleOwner) { duration ->
      val max = duration.coerceAtLeast(1L).toFloat()
      binding.seekBar.valueTo = max
    }

    viewModel.positionMs.observe(viewLifecycleOwner) { position ->
      if (!binding.seekBar.isPressed) {
        binding.seekBar.value = position.toFloat().coerceAtLeast(0f).coerceAtMost(binding.seekBar.valueTo)
      }
    }

    viewModel.statusText.observe(viewLifecycleOwner) { status ->
      binding.status.text = status
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
