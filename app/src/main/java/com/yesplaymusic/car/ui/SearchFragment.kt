package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType
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
  private val trackResults = mutableListOf<Track>()
  private val playlistResults = mutableListOf<CoverItem>()

  private var searchType = SearchType.SONGS
  private lateinit var trackAdapter: SearchResultAdapter
  private lateinit var playlistAdapter: LibraryCoverAdapter

  enum class SearchType { SONGS, PLAYLISTS }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentSearchBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 歌曲搜索结果适配器
    trackAdapter = SearchResultAdapter { track ->
      val index = trackResults.indexOfFirst { it.id == track.id }
      if (index >= 0) {
        (activity as? PlaybackHost)?.playQueue(trackResults.toList(), index)
      } else {
        (activity as? PlaybackHost)?.playSingle(track)
      }
    }

    // 歌单搜索结果适配器
    playlistAdapter = LibraryCoverAdapter { item ->
      (activity as? DetailNavigator)?.openMediaDetail(MediaType.PLAYLIST, item)
    }

    // 默认使用歌曲列表布局
    binding.resultsList.layoutManager = LinearLayoutManager(requireContext())
    binding.resultsList.adapter = trackAdapter
    if (binding.resultsList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.list_item_spacing)
      binding.resultsList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    // 类型切换按钮
    binding.typeSongs.setOnClickListener { switchType(SearchType.SONGS) }
    binding.typePlaylists.setOnClickListener { switchType(SearchType.PLAYLISTS) }
    updateTypeButtons()

    binding.searchButton.setOnClickListener { performSearch() }
    binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        performSearch()
        true
      } else {
        false
      }
    }
  }

  private fun switchType(type: SearchType) {
    if (searchType == type) return
    searchType = type
    updateTypeButtons()

    // 切换适配器和布局
    when (type) {
      SearchType.SONGS -> {
        binding.resultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.resultsList.adapter = trackAdapter
      }
      SearchType.PLAYLISTS -> {
        binding.resultsList.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.resultsList.adapter = playlistAdapter
      }
    }

    // 如果有搜索关键词，重新搜索
    val keyword = binding.searchInput.text?.toString()?.trim().orEmpty()
    if (keyword.isNotBlank()) {
      performSearch()
    }
  }

  private fun updateTypeButtons() {
    when (searchType) {
      SearchType.SONGS -> {
        binding.typeSongs.setBackgroundResource(R.drawable.bg_tag_selected)
        binding.typeSongs.setTextColor(resources.getColor(R.color.textPrimary, null))
        binding.typePlaylists.setBackgroundResource(R.drawable.bg_tag_normal)
        binding.typePlaylists.setTextColor(0x99FFFFFF.toInt())
      }
      SearchType.PLAYLISTS -> {
        binding.typePlaylists.setBackgroundResource(R.drawable.bg_tag_selected)
        binding.typePlaylists.setTextColor(resources.getColor(R.color.textPrimary, null))
        binding.typeSongs.setBackgroundResource(R.drawable.bg_tag_normal)
        binding.typeSongs.setTextColor(0x99FFFFFF.toInt())
      }
    }
  }

  private fun performSearch() {
    val keyword = binding.searchInput.text?.toString()?.trim().orEmpty()
    if (keyword.isBlank()) return
    binding.searchStatus.visibility = View.VISIBLE
    binding.searchStatus.text = getString(R.string.searching)

    lifecycleScope.launch {
      when (searchType) {
        SearchType.SONGS -> {
          val list = withContext(Dispatchers.IO) { provider.search(keyword, limit = 20) }
          trackResults.clear()
          trackResults.addAll(list)
          trackAdapter.submit(list)
          updateSearchStatus(list.isEmpty())
        }
        SearchType.PLAYLISTS -> {
          val list = withContext(Dispatchers.IO) { provider.searchPlaylists(keyword, limit = 20) }
          playlistResults.clear()
          playlistResults.addAll(list)
          playlistAdapter.submit(list)
          updateSearchStatus(list.isEmpty())
        }
      }
    }
  }

  private fun updateSearchStatus(isEmpty: Boolean) {
    if (isEmpty) {
      binding.searchStatus.visibility = View.VISIBLE
      binding.searchStatus.text = getString(R.string.no_results)
    } else {
      binding.searchStatus.visibility = View.GONE
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
