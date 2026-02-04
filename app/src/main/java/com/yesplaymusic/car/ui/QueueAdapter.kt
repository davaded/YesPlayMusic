package com.yesplaymusic.car.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.ItemTrackBinding

class QueueAdapter(
  private val onPlay: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

  private val items = mutableListOf<Track>()
  private var currentIndex: Int = -1

  fun submit(tracks: List<Track>, index: Int) {
    items.clear()
    items.addAll(tracks)
    currentIndex = index
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
    val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return QueueViewHolder(binding, onPlay)
  }

  override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
    holder.bind(items[position], position == currentIndex, position)
  }

  override fun getItemCount(): Int = items.size

  class QueueViewHolder(
    private val binding: ItemTrackBinding,
    private val onPlay: (Int) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(track: Track, isCurrent: Boolean, index: Int) {
      binding.trackTitle.text = track.title
      binding.trackSubtitle.text = if (track.album.isNullOrBlank()) track.artist else "${track.artist} · ${track.album}"
      binding.cover.load(track.coverUrl) {
        crossfade(true)
        placeholder(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
        error(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
      }
      val color = if (isCurrent) {
        binding.root.context.getColor(com.yesplaymusic.car.R.color.primary)
      } else {
        binding.root.context.getColor(com.yesplaymusic.car.R.color.textPrimary)
      }
      binding.trackTitle.setTextColor(color)
      binding.trackTitle.setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
      binding.playItemButton.text = if (isCurrent) {
        binding.root.context.getString(com.yesplaymusic.car.R.string.playing)
      } else {
        binding.root.context.getString(com.yesplaymusic.car.R.string.play)
      }
      binding.playItemButton.setOnClickListener { onPlay(index) }
      binding.root.setOnClickListener { onPlay(index) }
    }
  }
}
