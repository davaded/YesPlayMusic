package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.databinding.ItemLibraryCoverBinding

class LibraryCoverAdapter(
  private val onClick: (CoverItem) -> Unit
) : RecyclerView.Adapter<LibraryCoverAdapter.CoverViewHolder>() {

  private val items = mutableListOf<CoverItem>()

  fun submit(list: List<CoverItem>) {
    items.clear()
    items.addAll(list)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
    val binding = ItemLibraryCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return CoverViewHolder(binding, onClick)
  }

  override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
    holder.bind(items[position])
  }

  override fun getItemCount(): Int = items.size

  class CoverViewHolder(
    private val binding: ItemLibraryCoverBinding,
    private val onClick: (CoverItem) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: CoverItem) {
      binding.libraryTitle.text = item.title
      if (item.subtitle.isNullOrBlank()) {
        binding.librarySubtitle.visibility = View.GONE
      } else {
        binding.librarySubtitle.visibility = View.VISIBLE
        binding.librarySubtitle.text = item.subtitle
      }
      binding.libraryCover.load(item.coverUrl) {
        crossfade(true)
        placeholder(R.drawable.ic_launcher_foreground)
        error(R.drawable.ic_launcher_foreground)
      }
      binding.root.setOnClickListener { onClick(item) }
    }
  }
}
