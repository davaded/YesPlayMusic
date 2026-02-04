package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.yesplaymusic.car.databinding.FragmentQueueBinding
import com.yesplaymusic.car.playback.PlaybackViewModel

class QueueFragment : Fragment() {

  private var _binding: FragmentQueueBinding? = null
  private val binding get() = _binding!!
  private val viewModel: PlaybackViewModel by activityViewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentQueueBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val adapter = QueueAdapter { index ->
      (activity as? PlaybackHost)?.playQueue(viewModel.queue.value ?: emptyList(), index)
    }

    binding.queueList.layoutManager = LinearLayoutManager(requireContext())
    binding.queueList.adapter = adapter
    if (binding.queueList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(com.yesplaymusic.car.R.dimen.list_item_spacing)
      binding.queueList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    viewModel.queue.observe(viewLifecycleOwner) { queue ->
      adapter.submit(queue, viewModel.queueIndex.value ?: -1)
      binding.queueEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
    }

    viewModel.queueIndex.observe(viewLifecycleOwner) { index ->
      adapter.submit(viewModel.queue.value ?: emptyList(), index)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
