package com.yesplaymusic.car.data

data class CoverItem(
  val id: Long,
  val title: String,
  val subtitle: String? = null,
  val coverUrl: String? = null
)
