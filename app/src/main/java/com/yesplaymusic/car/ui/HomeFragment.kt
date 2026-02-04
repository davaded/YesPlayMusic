package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.lifecycle.lifecycleScope
import coil.load
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

  private var _binding: FragmentHomeBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()
  private val quickItems = mutableListOf<com.yesplaymusic.car.data.CoverItem>()
  private val libraryItems = mutableListOf<com.yesplaymusic.car.data.CoverItem>()
  private var heroItem: com.yesplaymusic.car.data.CoverItem? = null
  private var heroType: MediaType = MediaType.PLAYLIST

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val highlightAdapter = QuickTrackAdapter { item ->
      val track = Track(
        id = item.id,
        title = item.title,
        artist = item.subtitle ?: "",
        album = null,
        coverUrl = item.coverUrl,
        durationMs = 0L
      )
      (activity as? PlaybackHost)?.playSingle(track)
    }
    val libraryAdapter = LibraryCoverAdapter { item ->
      (activity as? DetailNavigator)?.openMediaDetail(MediaType.PLAYLIST, item)
    }

    binding.highlightList.layoutManager = GridLayoutManager(requireContext(), 3)
    binding.highlightList.adapter = highlightAdapter
    if (binding.highlightList.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.list_item_spacing)
      binding.highlightList.addItemDecoration(SpacingItemDecoration(vertical = spacing, horizontal = spacing))
    }

    binding.libraryGrid.layoutManager = GridLayoutManager(requireContext(), 5)
    binding.libraryGrid.adapter = libraryAdapter
    if (binding.libraryGrid.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.home_section_spacing_horizontal)
      binding.libraryGrid.addItemDecoration(SpacingItemDecoration(vertical = spacing, horizontal = spacing))
    }

    binding.heroCard.setOnClickListener { openHeroDetail() }
    binding.heroPlayButton.setOnClickListener { playHero() }

    lifecycleScope.launch {
      val recommendDeferred = async(Dispatchers.IO) { provider.getRecommendPlaylists(10) }
      val forYouDeferred = async(Dispatchers.IO) { provider.getPersonalizedNewSongs(8) }
      val albumDeferred = async(Dispatchers.IO) { provider.getNewAlbums(10) }

      val recommend = recommendDeferred.await()
      val forYou = forYouDeferred.await()
      val albums = albumDeferred.await()

      withContext(Dispatchers.Main) {
        quickItems.clear()
        quickItems.addAll(forYou)
        highlightAdapter.submit(quickItems)
        binding.highlightCard.visibility = if (quickItems.isEmpty()) View.GONE else View.VISIBLE

        libraryItems.clear()
        libraryItems.addAll(recommend)
        if (libraryItems.isEmpty()) {
          libraryItems.addAll(albums)
        }
        libraryAdapter.submit(libraryItems)

        if (recommend.isNotEmpty()) {
          heroType = MediaType.PLAYLIST
          heroItem = recommend.first()
        } else if (albums.isNotEmpty()) {
          heroType = MediaType.ALBUM
          heroItem = albums.first()
        } else {
          heroItem = null
        }
        bindHero()
      }
    }
  }

  private fun bindHero() {
    val item = heroItem
    if (item == null) {
      binding.heroCard.visibility = View.GONE
      return
    }
    binding.heroCard.visibility = View.VISIBLE
    binding.heroLabel.text = if (heroType == MediaType.PLAYLIST) {
      getString(R.string.hero_label_playlist)
    } else {
      getString(R.string.hero_label_album)
    }
    binding.heroTitle.text = getString(R.string.hero_favorite_title)
    binding.heroSubtitle.text = getString(R.string.hero_favorite_count)
    binding.heroCover.load(item.coverUrl) {
      crossfade(true)
      placeholder(R.drawable.ic_launcher_foreground)
      error(R.drawable.ic_launcher_foreground)
    }
  }

  private fun openHeroDetail() {
    val item = heroItem ?: return
    (activity as? DetailNavigator)?.openMediaDetail(heroType, item)
  }

  private fun playHero() {
    val item = heroItem ?: return
    lifecycleScope.launch {
      val detail = withContext(Dispatchers.IO) {
        when (heroType) {
          MediaType.PLAYLIST -> provider.getPlaylistDetail(item.id)
          MediaType.ALBUM -> provider.getAlbumDetail(item.id)
        }
      }
      val tracks = detail?.tracks ?: emptyList()
      if (tracks.isNotEmpty()) {
        (activity as? PlaybackHost)?.playQueue(tracks, 0)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
