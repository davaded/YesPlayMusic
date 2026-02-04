package com.yesplaymusic.car.data

interface MusicProvider {
  suspend fun search(keyword: String, limit: Int = 20): List<Track>
  suspend fun resolveStream(trackId: Long): StreamResource
  suspend fun getPlaylist(playlistId: Long): List<Track>
  suspend fun getRecommendPlaylists(limit: Int = 10): List<CoverItem> = emptyList()
  suspend fun getNewAlbums(limit: Int = 10): List<CoverItem> = emptyList()
  suspend fun getPersonalizedNewSongs(limit: Int = 8): List<CoverItem> = emptyList()
}
