package com.yesplaymusic.car.data

data class MediaDetail(
  val id: Long,
  val title: String,
  val subtitle: String? = null,
  val coverUrl: String? = null,
  val tracks: List<Track> = emptyList()
)
