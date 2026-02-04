package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yesplaymusic.car.data.HomeSection
import com.yesplaymusic.car.databinding.ItemHomeSectionBinding

class HomeSectionAdapter(
  private val onItemClick: (Int, Int) -> Unit
) : RecyclerView.Adapter<HomeSectionAdapter.SectionViewHolder>() {

  private val sections = mutableListOf<HomeSection>()

  fun submit(items: List<HomeSection>) {
    sections.clear()
    sections.addAll(items)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
    val binding = ItemHomeSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return SectionViewHolder(binding, onItemClick)
  }

  override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
    holder.bind(sections[position], position)
  }

  override fun getItemCount(): Int = sections.size

  class SectionViewHolder(
    private val binding: ItemHomeSectionBinding,
    private val onItemClick: (Int, Int) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(section: HomeSection, sectionIndex: Int) {
      binding.sectionTitle.text = section.title
      val adapter = CoverItemAdapter { itemIndex ->
        onItemClick(sectionIndex, itemIndex)
      }
      binding.sectionList.layoutManager = LinearLayoutManager(
        binding.root.context,
        LinearLayoutManager.HORIZONTAL,
        false
      )
      binding.sectionList.adapter = adapter
      binding.sectionList.isNestedScrollingEnabled = false
      if (binding.sectionList.itemDecorationCount == 0) {
        val spacing = binding.root.resources.getDimensionPixelSize(com.yesplaymusic.car.R.dimen.home_section_spacing_horizontal)
        binding.sectionList.addItemDecoration(SpacingItemDecoration(horizontal = spacing))
      }
      adapter.submit(section.items)
    }
  }
}
