package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.ItemTrackBinding

class TrackListAdapter(
  private val onPlay: (Int) -> Unit
) : RecyclerView.Adapter<TrackListAdapter.TrackViewHolder>() {

  private val items = mutableListOf<Track>()

  fun submit(tracks: List<Track>) {
    items.clear()
    items.addAll(tracks)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
    val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return TrackViewHolder(binding, onPlay)
  }

  override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
    holder.bind(items[position], position)
  }

  override fun getItemCount(): Int = items.size

  class TrackViewHolder(
    private val binding: ItemTrackBinding,
    private val onPlay: (Int) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(track: Track, index: Int) {
      binding.trackTitle.text = track.title
      binding.trackSubtitle.text = if (track.album.isNullOrBlank()) {
        track.artist
      } else {
        "${track.artist} - ${track.album}"
      }
      binding.cover.load(track.coverUrl) {
        crossfade(true)
        placeholder(R.drawable.ic_launcher_foreground)
        error(R.drawable.ic_launcher_foreground)
      }
      binding.playItemButton.text = binding.root.context.getString(R.string.play)
      binding.playItemButton.setOnClickListener { onPlay(index) }
      binding.root.setOnClickListener { onPlay(index) }
    }
  }
}
