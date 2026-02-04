package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.databinding.ItemCoverBinding

class CoverItemAdapter(
  private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<CoverItemAdapter.CoverViewHolder>() {

  private val items = mutableListOf<CoverItem>()

  fun submit(list: List<CoverItem>) {
    items.clear()
    items.addAll(list)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
    val binding = ItemCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return CoverViewHolder(binding, onClick)
  }

  override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
    holder.bind(items[position], position)
  }

  override fun getItemCount(): Int = items.size

  class CoverViewHolder(
    private val binding: ItemCoverBinding,
    private val onClick: (Int) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: CoverItem, position: Int) {
      binding.coverTitle.text = item.title
      if (item.subtitle.isNullOrBlank()) {
        binding.coverSubtitle.visibility = View.GONE
      } else {
        binding.coverSubtitle.visibility = View.VISIBLE
        binding.coverSubtitle.text = item.subtitle
      }
      binding.coverImage.load(item.coverUrl) {
        crossfade(true)
        placeholder(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
        error(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
      }
      binding.root.setOnClickListener { onClick(position) }
    }
  }
}
