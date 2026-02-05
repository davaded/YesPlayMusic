package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.lifecycle.lifecycleScope
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

    // 歌曲网格适配器 (3列)
    val songGridAdapter = QuickTrackAdapter { item ->
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

    // 专辑/歌单网格适配器 (5列)
    val libraryAdapter = LibraryCoverAdapter { item ->
      (activity as? DetailNavigator)?.openMediaDetail(MediaType.PLAYLIST, item)
    }

    // 设置歌曲网格 RecyclerView (3列)
    binding.songGrid.layoutManager = GridLayoutManager(requireContext(), 3)
    binding.songGrid.adapter = songGridAdapter
    if (binding.songGrid.itemDecorationCount == 0) {
      val spacingH = resources.getDimensionPixelSize(R.dimen.song_grid_spacing_h)
      val spacingV = resources.getDimensionPixelSize(R.dimen.song_grid_spacing_v)
      binding.songGrid.addItemDecoration(SpacingItemDecoration(vertical = spacingV, horizontal = spacingH))
    }

    // 设置专辑网格 RecyclerView (5列)
    binding.libraryGrid.layoutManager = GridLayoutManager(requireContext(), 5)
    binding.libraryGrid.adapter = libraryAdapter
    if (binding.libraryGrid.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.home_section_spacing_horizontal)
      binding.libraryGrid.addItemDecoration(SpacingItemDecoration(vertical = spacing, horizontal = spacing))
    }

    // Hero 卡片点击事件
    binding.heroCard.setOnClickListener { openHeroDetail() }
    binding.heroPlayButton.setOnClickListener { playHero() }

    // 加载数据
    lifecycleScope.launch {
      val recommendDeferred = async(Dispatchers.IO) { provider.getRecommendPlaylists(10) }
      val forYouDeferred = async(Dispatchers.IO) { provider.getPersonalizedNewSongs(12) }
      val albumDeferred = async(Dispatchers.IO) { provider.getNewAlbums(10) }

      val recommend = recommendDeferred.await()
      val forYou = forYouDeferred.await()
      val albums = albumDeferred.await()

      withContext(Dispatchers.Main) {
        // 更新歌曲网格 (显示12首)
        quickItems.clear()
        quickItems.addAll(forYou.take(12))
        songGridAdapter.submit(quickItems)

        // 更新专辑/歌单网格
        libraryItems.clear()
        libraryItems.addAll(recommend)
        if (libraryItems.isEmpty()) {
          libraryItems.addAll(albums)
        }
        libraryAdapter.submit(libraryItems)

        // 设置 Hero 卡片数据
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
    // Hero 卡片使用固定文字，不再动态绑定封面
    binding.heroTitle.text = getString(R.string.hero_favorite_title)
    binding.heroSubtitle.text = getString(R.string.hero_favorite_count)
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
