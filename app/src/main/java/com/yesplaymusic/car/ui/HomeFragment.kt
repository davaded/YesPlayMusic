package com.yesplaymusic.car.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.HomeSection
import com.yesplaymusic.car.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

  private var _binding: FragmentHomeBinding? = null
  private val binding get() = _binding!!
  private val provider = ProviderRegistry.get()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val adapter = HomeSectionAdapter { _, _ -> }
    binding.homeList.layoutManager = LinearLayoutManager(requireContext())
    binding.homeList.adapter = adapter

    if (binding.homeList.itemDecorationCount == 0) {
      val spacing = (12 * resources.displayMetrics.density).toInt()
      binding.homeList.addItemDecoration(SpacingItemDecoration(vertical = spacing))
    }

    lifecycleScope.launch {
      val recommendDeferred = async(Dispatchers.IO) { provider.getRecommendPlaylists(10) }
      val forYouDeferred = async(Dispatchers.IO) { provider.getPersonalizedNewSongs(8) }
      val albumDeferred = async(Dispatchers.IO) { provider.getNewAlbums(10) }

      val recommend = recommendDeferred.await()
      val forYou = forYouDeferred.await()
      val albums = albumDeferred.await()

      val sections = mutableListOf<HomeSection>()
      if (recommend.isNotEmpty()) {
        sections.add(HomeSection(getString(R.string.section_recommend), recommend))
      }
      if (forYou.isNotEmpty()) {
        sections.add(HomeSection(getString(R.string.section_for_you), forYou))
      }
      if (albums.isNotEmpty()) {
        sections.add(HomeSection(getString(R.string.section_new_albums), albums))
      }

      withContext(Dispatchers.Main) {
        adapter.submit(sections)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
