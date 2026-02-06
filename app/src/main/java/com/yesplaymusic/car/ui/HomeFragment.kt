package com.yesplaymusic.car.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.yesplaymusic.car.data.CookieStore
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

  companion object {
    private const val TAG = "HomeFragment"
  }

  private var _binding: FragmentHomeBinding? = null
  private val binding get() = _binding!!

  /** 供外部调用刷新用户信息和数据 */
  fun refreshUserInfo() {
    if (_binding != null) {
      updateUserInfo()
      loadData()
    }
  }
  private val provider = ProviderRegistry.get()
  private lateinit var cookieStore: CookieStore
  private val quickItems = mutableListOf<CoverItem>()
  private val libraryItems = mutableListOf<CoverItem>()
  private var heroItem: CoverItem? = null
  private var heroType: MediaType = MediaType.PLAYLIST
  private var dailyRecommendTracks: List<Track> = emptyList() // 保存每日推荐歌曲用于播放

  private lateinit var songGridAdapter: QuickTrackAdapter
  private lateinit var libraryAdapter: LibraryCoverAdapter

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    cookieStore = CookieStore.getInstance(requireContext())

    // 头像点击 -> 打开登录页面
    binding.userAvatar.setOnClickListener {
      Log.d(TAG, "头像点击 -> 打开登录页面")
      (activity as? MainActivity)?.showLoginScreen()
    }

    // 更新用户信息显示
    updateUserInfo()

    // 歌曲网格适配器 (3列)
    songGridAdapter = QuickTrackAdapter { item ->
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
    libraryAdapter = LibraryCoverAdapter { item ->
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
    loadData()
  }

  private fun loadData() {
    val userInfo = cookieStore.getCachedUserInfo()
    val isLoggedIn = userInfo != null

    // 显示 loading
    binding.loadingIndicator.visibility = View.VISIBLE

    lifecycleScope.launch {
      if (isLoggedIn && userInfo != null) {
        // 已登录：使用个性化推荐
        val dailySongsDeferred = async(Dispatchers.IO) { provider.getDailyRecommendSongs() }
        val userPlaylistsDeferred = async(Dispatchers.IO) { provider.getUserPlaylists(userInfo.id) }

        val dailySongs = dailySongsDeferred.await()
        val userPlaylists = userPlaylistsDeferred.await()

        withContext(Dispatchers.Main) {
          // 保存每日推荐歌曲用于 Hero 播放
          dailyRecommendTracks = dailySongs

          // 更新 Hero 卡片为"每日推荐"
          bindHero(isDaily = true, trackCount = dailySongs.size)

          // 更新歌曲网格为每日推荐
          quickItems.clear()
          dailySongs.take(12).forEach { track ->
            quickItems.add(CoverItem(
              id = track.id,
              title = track.title,
              subtitle = track.artist,
              coverUrl = track.coverUrl
            ))
          }
          songGridAdapter.submit(quickItems)

          // 更新专辑/歌单网格为用户歌单
          libraryItems.clear()
          // 跳过第一个（我喜欢的音乐），显示其他歌单
          libraryItems.addAll(userPlaylists.drop(1).take(10))
          libraryAdapter.submit(libraryItems)

          // 隐藏 loading
          binding.loadingIndicator.visibility = View.GONE
        }
      } else {
        // 未登录：使用公开推荐
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
          dailyRecommendTracks = emptyList()
          if (recommend.isNotEmpty()) {
            heroType = MediaType.PLAYLIST
            heroItem = recommend.first()
          } else if (albums.isNotEmpty()) {
            heroType = MediaType.ALBUM
            heroItem = albums.first()
          } else {
            heroItem = null
          }
          bindHero(isDaily = false, trackCount = 0)

          // 隐藏 loading
          binding.loadingIndicator.visibility = View.GONE
        }
      }
    }
  }

  private fun bindHero(isDaily: Boolean = false, trackCount: Int = 0) {
    binding.heroCard.visibility = View.VISIBLE
    if (isDaily) {
      // 每日推荐
      binding.heroTitle.text = getString(R.string.hero_daily_title)
      binding.heroSubtitle.text = getString(R.string.hero_daily_count, trackCount)
    } else {
      // 未登录时显示推荐歌单
      val item = heroItem
      if (item == null) {
        binding.heroCard.visibility = View.GONE
        return
      }
      binding.heroTitle.text = item.title
      binding.heroSubtitle.text = item.subtitle ?: ""
    }
  }

  private fun openHeroDetail() {
    // 每日推荐跳转到详情页
    if (dailyRecommendTracks.isNotEmpty()) {
      val dailyItem = CoverItem(
        id = 0L, // 每日推荐没有固定ID
        title = getString(R.string.hero_daily_title),
        subtitle = getString(R.string.hero_daily_count, dailyRecommendTracks.size),
        coverUrl = null
      )
      (activity as? DetailNavigator)?.openDailyDetail(dailyRecommendTracks, dailyItem)
      return
    }
    val item = heroItem ?: return
    (activity as? DetailNavigator)?.openMediaDetail(heroType, item)
  }

  private fun playHero() {
    // 如果有每日推荐歌曲，直接播放
    if (dailyRecommendTracks.isNotEmpty()) {
      (activity as? PlaybackHost)?.playQueue(dailyRecommendTracks, 0)
      return
    }
    // 否则获取歌单/专辑详情播放
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

  override fun onResume() {
    super.onResume()
    // 每次回到首页时更新用户信息（登录后返回）
    updateUserInfo()
  }

  private fun updateUserInfo() {
    val userInfo = cookieStore.getCachedUserInfo()
    if (userInfo != null) {
      // 已登录，显示用户头像和昵称
      binding.userTitle.text = "${userInfo.nickname}的音乐库"
      userInfo.avatarUrl?.let { url ->
        binding.userAvatarImage.load(url) {
          crossfade(true)
          transformations(CircleCropTransformation())
          placeholder(R.drawable.ic_launcher_foreground)
          error(R.drawable.ic_launcher_foreground)
        }
      }
    } else {
      // 未登录，显示默认
      binding.userTitle.text = getString(R.string.home_user_title)
      binding.userAvatarImage.setImageResource(R.drawable.ic_launcher_foreground)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
