package com.yesplaymusic.car.data

data class Track(
  val id: Long,
  val title: String,
  val artist: String,
  val album: String? = null,
  val coverUrl: String? = null,
  val durationMs: Long = 0L,
  val mvId: Long = 0L
)
