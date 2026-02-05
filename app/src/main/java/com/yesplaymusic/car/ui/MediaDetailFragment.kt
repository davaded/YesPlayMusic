package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaDetail
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentMediaDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaDetailFragment : Fragment() {

  private var _binding: FragmentMediaDetailBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()
  private val tracks = mutableListOf<Track>()
  private val allTracks = mutableListOf<Track>()
  private var sortMode = SortMode.DEFAULT

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentMediaDetailBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val args = requireArguments()
    val type = args.getString(ARG_TYPE)?.let { MediaType.valueOf(it) } ?: return
    val id = args.getLong(ARG_ID)
    val title = args.getString(ARG_TITLE)
    val subtitle = args.getString(ARG_SUBTITLE)
    val coverUrl = args.getString(ARG_COVER)

    binding.detailToolbar.title = when (type) {
      MediaType.PLAYLIST -> getString(R.string.detail_playlist)
      MediaType.ALBUM -> getString(R.string.detail_album)
    }
    binding.detailToolbar.setNavigationOnClickListener {
      parentFragmentManager.popBackStack()
    }

    // 返回按钮点击事件
    binding.backButton.setOnClickListener {
      parentFragmentManager.popBackStack()
    }

    // 搜索按钮点击事件
    binding.searchPillButton.setOnClickListener {
      binding.detailSearchCard.visibility =
        if (binding.detailSearchCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    bindHeader(title, subtitle, coverUrl)

    val adapter = TrackListAdapter { index ->
      if (tracks.isNotEmpty()) {
        (activity as? PlaybackHost)?.playQueue(tracks.toList(), index)
      }
    }

    binding.detailList.layoutManager = LinearLayoutManager(requireContext())
    binding.detailList.adapter = adapter
    if (binding.detailList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(com.yesplaymusic.car.R.dimen.list_item_spacing)
      binding.detailList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    binding.playAllButton.setOnClickListener {
      if (tracks.isNotEmpty()) {
        (activity as? PlaybackHost)?.playQueue(tracks.toList(), 0)
      }
    }

    binding.detailSearchInput.doAfterTextChanged {
      applyFilterAndSort(adapter)
    }

    binding.detailSortButton.setOnClickListener {
      showSortMenu()
    }

    loadDetail(type, id, adapter)
  }

  private fun bindHeader(title: String?, subtitle: String?, coverUrl: String?) {
    binding.detailTitle.text = title.orEmpty()
    if (subtitle.isNullOrBlank()) {
      binding.detailSubtitle.visibility = View.GONE
    } else {
      binding.detailSubtitle.visibility = View.VISIBLE
      binding.detailSubtitle.text = subtitle
    }
    binding.detailCover.load(coverUrl) {
      crossfade(true)
      placeholder(R.drawable.ic_launcher_foreground)
      error(R.drawable.ic_launcher_foreground)
    }
  }

  private fun loadDetail(type: MediaType, id: Long, adapter: TrackListAdapter) {
    binding.detailStatus.visibility = View.VISIBLE
    binding.detailStatus.text = getString(R.string.detail_loading)
    binding.detailList.visibility = View.GONE
    binding.playAllButton.isEnabled = false

    lifecycleScope.launch {
      val detail = withContext(Dispatchers.IO) {
        when (type) {
          MediaType.PLAYLIST -> provider.getPlaylistDetail(id)
          MediaType.ALBUM -> provider.getAlbumDetail(id)
        }
      }
      if (!isAdded) return@launch
      if (detail == null) {
        showError()
        return@launch
      }
      applyDetail(detail, adapter)
    }
  }

  private fun applyDetail(detail: MediaDetail, adapter: TrackListAdapter) {
    bindHeader(detail.title, detail.subtitle, detail.coverUrl)
    allTracks.clear()
    allTracks.addAll(detail.tracks)
    applyFilterAndSort(adapter)
    if (tracks.isEmpty()) {
      binding.detailStatus.visibility = View.VISIBLE
      binding.detailStatus.text = getString(R.string.detail_empty)
      binding.detailList.visibility = View.GONE
      binding.playAllButton.isEnabled = false
    } else {
      binding.detailStatus.visibility = View.GONE
      binding.detailList.visibility = View.VISIBLE
      binding.playAllButton.isEnabled = true
    }
  }

  private fun showError() {
    binding.detailStatus.visibility = View.VISIBLE
    binding.detailStatus.text = getString(R.string.detail_failed)
    binding.detailList.visibility = View.GONE
    binding.playAllButton.isEnabled = false
  }

  private fun applyFilterAndSort(adapter: TrackListAdapter) {
    val keyword = binding.detailSearchInput.text?.toString()?.trim().orEmpty()
    val filtered = if (keyword.isBlank()) {
      allTracks.toList()
    } else {
      allTracks.filter { track ->
        track.title.contains(keyword, ignoreCase = true)
          || track.artist.contains(keyword, ignoreCase = true)
          || (track.album?.contains(keyword, ignoreCase = true) ?: false)
      }
    }
    val sorted = when (sortMode) {
      SortMode.DEFAULT -> filtered
      SortMode.TITLE -> filtered.sortedBy { it.title }
      SortMode.ARTIST -> filtered.sortedBy { it.artist }
      SortMode.ALBUM -> filtered.sortedBy { it.album ?: "" }
      SortMode.DURATION -> filtered.sortedBy { it.durationMs }
    }
    tracks.clear()
    tracks.addAll(sorted)
    adapter.submit(tracks)
    binding.playAllButton.isEnabled = tracks.isNotEmpty()
    if (tracks.isEmpty()) {
      binding.detailStatus.visibility = View.VISIBLE
      binding.detailStatus.text = getString(R.string.detail_empty)
      binding.detailList.visibility = View.GONE
    } else {
      binding.detailStatus.visibility = View.GONE
      binding.detailList.visibility = View.VISIBLE
    }
  }

  private fun showSortMenu() {
    val menu = PopupMenu(requireContext(), binding.detailSortButton)
    menu.menu.add(0, SortMode.DEFAULT.id, 0, getString(R.string.sort_default))
    menu.menu.add(0, SortMode.TITLE.id, 1, getString(R.string.sort_title))
    menu.menu.add(0, SortMode.ARTIST.id, 2, getString(R.string.sort_artist))
    menu.menu.add(0, SortMode.ALBUM.id, 3, getString(R.string.sort_album))
    menu.menu.add(0, SortMode.DURATION.id, 4, getString(R.string.sort_duration))
    menu.setOnMenuItemClickListener { item ->
      sortMode = SortMode.fromId(item.itemId)
      binding.detailSortButton.text = item.title
      applyFilterAndSort(binding.detailList.adapter as TrackListAdapter)
      true
    }
    menu.show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    private const val ARG_TYPE = "arg_type"
    private const val ARG_ID = "arg_id"
    private const val ARG_TITLE = "arg_title"
    private const val ARG_SUBTITLE = "arg_subtitle"
    private const val ARG_COVER = "arg_cover"

    fun newInstance(type: MediaType, item: CoverItem): MediaDetailFragment {
      return MediaDetailFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_TYPE, type.name)
          putLong(ARG_ID, item.id)
          putString(ARG_TITLE, item.title)
          putString(ARG_SUBTITLE, item.subtitle)
          putString(ARG_COVER, item.coverUrl)
        }
      }
    }
  }

  private enum class SortMode(val id: Int) {
    DEFAULT(1),
    TITLE(2),
    ARTIST(3),
    ALBUM(4),
    DURATION(5);

    companion object {
      fun fromId(id: Int): SortMode = values().firstOrNull { it.id == id } ?: DEFAULT
    }
  }
}
