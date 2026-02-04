package com.yesplaymusic.car.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yesplaymusic.car.data.Track
import com.yesplaymusic.car.databinding.ItemTrackBinding

class SearchResultAdapter(
  private val onPlay: (Track) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.TrackViewHolder>() {

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
    holder.bind(items[position])
  }

  override fun getItemCount(): Int = items.size

  class TrackViewHolder(
    private val binding: ItemTrackBinding,
    private val onPlay: (Track) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(track: Track) {
      binding.trackTitle.text = track.title
      val subtitle = if (track.album.isNullOrBlank()) track.artist else "${track.artist} · ${track.album}"
      binding.trackSubtitle.text = subtitle
      binding.cover.load(track.coverUrl) {
        crossfade(true)
        placeholder(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
        error(com.yesplaymusic.car.R.drawable.ic_launcher_foreground)
      }
      binding.playItemButton.setOnClickListener { onPlay(track) }
      binding.root.setOnClickListener { onPlay(track) }
    }
  }
}
