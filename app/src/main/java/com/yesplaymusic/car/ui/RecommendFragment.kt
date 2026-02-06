package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.databinding.FragmentRecommendBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecommendFragment : Fragment() {

  private var _binding: FragmentRecommendBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()

  private val highqualityItems = mutableListOf<CoverItem>()
  private val hotItems = mutableListOf<CoverItem>()
  private var currentTag = "全部"

  private lateinit var highqualityAdapter: LibraryCoverAdapter
  private lateinit var hotAdapter: LibraryCoverAdapter

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentRecommendBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 精品歌单适配器
    highqualityAdapter = LibraryCoverAdapter { item ->
      (activity as? DetailNavigator)?.openMediaDetail(MediaType.PLAYLIST, item)
    }

    // 热门歌单适配器
    hotAdapter = LibraryCoverAdapter { item ->
      (activity as? DetailNavigator)?.openMediaDetail(MediaType.PLAYLIST, item)
    }

    // 设置精品歌单 RecyclerView (5列)
    binding.highqualityGrid.layoutManager = GridLayoutManager(requireContext(), 5)
    binding.highqualityGrid.adapter = highqualityAdapter
    if (binding.highqualityGrid.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.home_section_spacing_horizontal)
      binding.highqualityGrid.addItemDecoration(SpacingItemDecoration(vertical = spacing, horizontal = spacing))
    }

    // 设置热门歌单 RecyclerView (5列)
    binding.hotGrid.layoutManager = GridLayoutManager(requireContext(), 5)
    binding.hotGrid.adapter = hotAdapter
    if (binding.hotGrid.itemDecorationCount == 0) {
      val spacing = resources.getDimensionPixelSize(R.dimen.home_section_spacing_horizontal)
      binding.hotGrid.addItemDecoration(SpacingItemDecoration(vertical = spacing, horizontal = spacing))
    }

    // 加载数据
    loadTags()
    loadData()
  }

  private fun loadTags() {
    viewLifecycleOwner.lifecycleScope.launch {
      val tags = withContext(Dispatchers.IO) { provider.getPlaylistHotTags() }
      withContext(Dispatchers.Main) {
        binding.tagsContainer.removeAllViews()
        // 添加"全部"标签
        addTagView("全部", currentTag == "全部")
        // 添加热门标签
        tags.take(10).forEach { tag ->
          addTagView(tag, currentTag == tag)
        }
      }
    }
  }

  private fun addTagView(tag: String, isSelected: Boolean) {
    val textView = TextView(requireContext()).apply {
      text = tag
      textSize = 14f
      setTextColor(if (isSelected) resources.getColor(R.color.textPrimary, null) else 0x99FFFFFF.toInt())
      setBackgroundResource(if (isSelected) R.drawable.bg_tag_selected else R.drawable.bg_tag_normal)
      setPadding(
        resources.getDimensionPixelSize(R.dimen.tag_padding_h),
        resources.getDimensionPixelSize(R.dimen.tag_padding_v),
        resources.getDimensionPixelSize(R.dimen.tag_padding_h),
        resources.getDimensionPixelSize(R.dimen.tag_padding_v)
      )
      val params = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      params.marginEnd = resources.getDimensionPixelSize(R.dimen.tag_margin)
      layoutParams = params
      setOnClickListener {
        if (currentTag != tag) {
          currentTag = tag
          loadTags()
          loadData()
        }
      }
    }
    binding.tagsContainer.addView(textView)
  }

  private fun loadData() {
    binding.loadingIndicator.visibility = View.VISIBLE

    viewLifecycleOwner.lifecycleScope.launch {
      val highqualityDeferred = async(Dispatchers.IO) { provider.getHighqualityPlaylists(10, currentTag) }
      val hotDeferred = async(Dispatchers.IO) { provider.getTopPlaylists(20, "hot", currentTag) }

      val highquality = highqualityDeferred.await()
      val hot = hotDeferred.await()

      withContext(Dispatchers.Main) {
        binding.loadingIndicator.visibility = View.GONE

        // 更新精品歌单
        highqualityItems.clear()
        highqualityItems.addAll(highquality)
        highqualityAdapter.submit(highqualityItems)

        // 更新热门歌单
        hotItems.clear()
        hotItems.addAll(hot)
        hotAdapter.submit(hotItems)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
