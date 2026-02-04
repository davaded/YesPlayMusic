package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.databinding.ItemQuickTrackBinding

class QuickTrackAdapter(
  private val onClick: (CoverItem) -> Unit
) : RecyclerView.Adapter<QuickTrackAdapter.QuickViewHolder>() {

  private val items = mutableListOf<CoverItem>()

  fun submit(list: List<CoverItem>) {
    items.clear()
    items.addAll(list)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickViewHolder {
    val binding = ItemQuickTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return QuickViewHolder(binding, onClick)
  }

  override fun onBindViewHolder(holder: QuickViewHolder, position: Int) {
    holder.bind(items[position])
  }

  override fun getItemCount(): Int = items.size

  class QuickViewHolder(
    private val binding: ItemQuickTrackBinding,
    private val onClick: (CoverItem) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: CoverItem) {
      binding.quickTitle.text = item.title
      if (item.subtitle.isNullOrBlank()) {
        binding.quickSubtitle.visibility = android.view.View.GONE
      } else {
        binding.quickSubtitle.visibility = android.view.View.VISIBLE
        binding.quickSubtitle.text = item.subtitle
      }
      binding.quickCover.load(item.coverUrl) {
        crossfade(true)
        placeholder(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
        error(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
      }
      binding.root.setOnClickListener { onClick(item) }
    }
  }
}
