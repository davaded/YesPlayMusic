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
      if (isActive) {
        // 当前行: 白色 + 大字 + 粗体
        binding.lyricLine.setTextColor(context.getColor(R.color.textPrimary))
        binding.lyricLine.textSize = 24f
        binding.lyricLine.setTypeface(null, Typeface.BOLD)
        binding.lyricLine.alpha = 1f
      } else {
        // 其他行: 半透明白色 + 正常字体
        binding.lyricLine.setTextColor(0x66FFFFFF.toInt())
        binding.lyricLine.textSize = 20f
        binding.lyricLine.setTypeface(null, Typeface.NORMAL)
        binding.lyricLine.alpha = 0.6f
      }
    }
  }
}
