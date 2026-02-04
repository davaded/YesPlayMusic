package com.yesplaymusic.car.data

data class SongDetail(
  val id: Long,
  val title: String,
  val artist: String,
  val album: String? = null,
  val albumId: Long? = null,
  val durationMs: Long = 0L,
  val mvId: Long = 0L,
  val publishTime: Long? = null
)
