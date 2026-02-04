package com.yesplaymusic.car.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.LyricLine
import com.yesplaymusic.car.databinding.ItemLyricLineBinding

class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.LyricViewHolder>() {

  private val items = mutableListOf<LyricLine>()
  private var activeIndex = -1

  fun submit(lines: List<LyricLine>) {
    items.clear()
    items.addAll(lines)
    activeIndex = if (items.isEmpty()) -1 else 0
    notifyDataSetChanged()
  }

  fun setActiveIndex(index: Int) {
    if (index == activeIndex) return
    val previous = activeIndex
    activeIndex = index
    if (previous in items.indices) notifyItemChanged(previous)
    if (activeIndex in items.indices) notifyItemChanged(activeIndex)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
    val binding = ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return LyricViewHolder(binding)
  }

  override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
    holder.bind(items[position], position == activeIndex)
  }

  override fun getItemCount(): Int = items.size

  class LyricViewHolder(
    private val binding: ItemLyricLineBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(line: LyricLine, isActive: Boolean) {
      binding.lyricLine.text = line.text
      val context = binding.root.context
      val color = if (isActive) {
        context.getColor(R.color.primary)
      } else {
        context.getColor(R.color.textTertiary)
      }
      binding.lyricLine.setTextColor(color)
      binding.lyricLine.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
    }
  }
}
