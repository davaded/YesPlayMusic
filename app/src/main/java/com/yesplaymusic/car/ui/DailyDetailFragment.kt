package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentMediaDetailBinding

class DailyDetailFragment : Fragment() {

  private var _binding: FragmentMediaDetailBinding? = null
  private val binding get() = _binding!!
  private val tracks = mutableListOf<Track>()
  private val allTracks = mutableListOf<Track>()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentMediaDetailBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val args = requireArguments()
    val title = args.getString(ARG_TITLE)
    val subtitle = args.getString(ARG_SUBTITLE)
    val trackIds = args.getLongArray(ARG_TRACK_IDS) ?: longArrayOf()
    val trackTitles = args.getStringArray(ARG_TRACK_TITLES) ?: arrayOf()
    val trackArtists = args.getStringArray(ARG_TRACK_ARTISTS) ?: arrayOf()
    val trackAlbums = args.getStringArray(ARG_TRACK_ALBUMS) ?: arrayOf()
    val trackCovers = args.getStringArray(ARG_TRACK_COVERS) ?: arrayOf()
    val trackDurations = args.getLongArray(ARG_TRACK_DURATIONS) ?: longArrayOf()

    // 重建 Track 列表
    for (i in trackIds.indices) {
      allTracks.add(Track(
        id = trackIds[i],
        title = trackTitles.getOrNull(i) ?: "",
        artist = trackArtists.getOrNull(i) ?: "",
        album = trackAlbums.getOrNull(i),
        coverUrl = trackCovers.getOrNull(i),
        durationMs = trackDurations.getOrNull(i) ?: 0L
      ))
    }

    binding.detailToolbar.title = getString(R.string.hero_daily_title)
    binding.detailToolbar.setNavigationOnClickListener {
      parentFragmentManager.popBackStack()
    }

    binding.backButton.setOnClickListener {
      parentFragmentManager.popBackStack()
    }

    binding.searchPillButton.setOnClickListener {
      binding.detailSearchCard.visibility =
        if (binding.detailSearchCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    bindHeader(title, subtitle)

    val adapter = TrackListAdapter { index ->
      if (tracks.isNotEmpty()) {
        (activity as? PlaybackHost)?.playQueue(tracks.toList(), index)
      }
    }

    binding.detailList.layoutManager = LinearLayoutManager(requireContext())
    binding.detailList.adapter = adapter
    if (binding.detailList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.list_item_spacing)
      binding.detailList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    binding.playAllButton.setOnClickListener {
      if (tracks.isNotEmpty()) {
        (activity as? PlaybackHost)?.playQueue(tracks.toList(), 0)
      }
    }

    binding.detailSearchInput.doAfterTextChanged {
      applyFilter(adapter)
    }

    // 直接显示歌曲列表
    applyFilter(adapter)
  }

  private fun bindHeader(title: String?, subtitle: String?) {
    binding.detailTitle.text = title.orEmpty()
    if (subtitle.isNullOrBlank()) {
      binding.detailSubtitle.visibility = View.GONE
    } else {
      binding.detailSubtitle.visibility = View.VISIBLE
      binding.detailSubtitle.text = subtitle
    }
    // 每日推荐使用默认图标
    binding.detailCover.setImageResource(R.drawable.ic_launcher_foreground)
  }

  private fun applyFilter(adapter: TrackListAdapter) {
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
    tracks.clear()
    tracks.addAll(filtered)
    adapter.submit(tracks)
    binding.playAllButton.isEnabled = tracks.isNotEmpty()
    binding.detailLoading.visibility = View.GONE
    if (tracks.isEmpty()) {
      binding.detailStatus.visibility = View.VISIBLE
      binding.detailStatus.text = getString(R.string.detail_empty)
      binding.detailList.visibility = View.GONE
    } else {
      binding.detailStatus.visibility = View.GONE
      binding.detailList.visibility = View.VISIBLE
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    private const val ARG_TITLE = "arg_title"
    private const val ARG_SUBTITLE = "arg_subtitle"
    private const val ARG_TRACK_IDS = "arg_track_ids"
    private const val ARG_TRACK_TITLES = "arg_track_titles"
    private const val ARG_TRACK_ARTISTS = "arg_track_artists"
    private const val ARG_TRACK_ALBUMS = "arg_track_albums"
    private const val ARG_TRACK_COVERS = "arg_track_covers"
    private const val ARG_TRACK_DURATIONS = "arg_track_durations"

    fun newInstance(tracks: List<Track>, item: CoverItem): DailyDetailFragment {
      return DailyDetailFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_TITLE, item.title)
          putString(ARG_SUBTITLE, item.subtitle)
          putLongArray(ARG_TRACK_IDS, tracks.map { it.id }.toLongArray())
          putStringArray(ARG_TRACK_TITLES, tracks.map { it.title }.toTypedArray())
          putStringArray(ARG_TRACK_ARTISTS, tracks.map { it.artist }.toTypedArray())
          putStringArray(ARG_TRACK_ALBUMS, tracks.map { it.album ?: "" }.toTypedArray())
          putStringArray(ARG_TRACK_COVERS, tracks.map { it.coverUrl ?: "" }.toTypedArray())
          putLongArray(ARG_TRACK_DURATIONS, tracks.map { it.durationMs }.toLongArray())
        }
      }
    }
  }
}
