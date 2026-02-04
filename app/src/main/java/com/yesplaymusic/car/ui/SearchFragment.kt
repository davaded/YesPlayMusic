package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentSearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

  private var _binding: FragmentSearchBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()
  private val results = mutableListOf<Track>()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentSearchBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val adapter = SearchResultAdapter { track ->
      val index = results.indexOfFirst { it.id == track.id }
      if (index >= 0) {
        (activity as? PlaybackHost)?.playQueue(results.toList(), index)
      } else {
        (activity as? PlaybackHost)?.playSingle(track)
      }
    }

    binding.resultsList.layoutManager = LinearLayoutManager(requireContext())
    binding.resultsList.adapter = adapter
    if (binding.resultsList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.list_item_spacing)
      binding.resultsList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    binding.searchButton.setOnClickListener { performSearch(adapter) }
    binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        performSearch(adapter)
        true
      } else {
        false
      }
    }
  }

  private fun performSearch(adapter: SearchResultAdapter) {
    val keyword = binding.searchInput.text?.toString()?.trim().orEmpty()
    if (keyword.isBlank()) return
    binding.searchStatus.visibility = View.VISIBLE
    binding.searchStatus.text = getString(R.string.searching)
    lifecycleScope.launch {
      val list = withContext(Dispatchers.IO) { provider.search(keyword, limit = 20) }
      results.clear()
      results.addAll(list)
      adapter.submit(list)
      if (list.isEmpty()) {
        binding.searchStatus.visibility = View.VISIBLE
        binding.searchStatus.text = getString(R.string.no_results)
      } else {
        binding.searchStatus.visibility = View.GONE
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
