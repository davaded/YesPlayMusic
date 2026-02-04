package com.yesplaymusic.car.data

enum class HomeSectionType {
  PLAYLISTS,
  NEW_SONGS,
  ALBUMS
}

data class HomeSection(
  val title: String,
  val type: HomeSectionType,
  val items: List<CoverItem>
)
