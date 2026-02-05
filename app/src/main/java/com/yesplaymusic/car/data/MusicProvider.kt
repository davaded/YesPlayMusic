package com.yesplaymusic.car.data

interface MusicProvider {
  suspend fun search(keyword: String, limit: Int = 20): List<Track>
  suspend fun resolveStream(trackId: Long): StreamResource
  suspend fun getPlaylist(playlistId: Long): List<Track>
  suspend fun getPlaylistDetail(playlistId: Long): MediaDetail?
  suspend fun getAlbumDetail(albumId: Long): MediaDetail?
  suspend fun getLyrics(trackId: Long): List<LyricLine> = emptyList()
  suspend fun getSongDetail(trackId: Long): SongDetail? = null
  suspend fun getMvUrl(mvId: Long): String = ""
  suspend fun getRecommendPlaylists(limit: Int = 10): List<CoverItem> = emptyList()
  suspend fun getNewAlbums(limit: Int = 10): List<CoverItem> = emptyList()
  suspend fun getPersonalizedNewSongs(limit: Int = 8): List<CoverItem> = emptyList()

  // ========== 登录相关接口 ==========

  /** 获取二维码 key */
  suspend fun getQrKey(): String? = null

  /** 生成二维码图片 (返回 base64) */
  suspend fun createQrCode(key: String): String? = null

  /** 检查二维码扫码状态 */
  suspend fun checkQrStatus(key: String): QrCheckResult = QrCheckResult(QrStatus.EXPIRED)

  /** 获取登录状态 */
  suspend fun getLoginStatus(): LoginStatus = LoginStatus(false)

  /** 获取用户喜欢的歌单 */
  suspend fun getUserLikedPlaylist(userId: Long): CoverItem? = null

  /** 设置 cookie */
  fun setCookie(cookie: String?) {}

  // ========== 个性化推荐接口 (需登录) ==========

  /** 每日推荐歌单 */
  suspend fun getDailyRecommendPlaylists(): List<CoverItem> = emptyList()

  /** 每日推荐歌曲 */
  suspend fun getDailyRecommendSongs(): List<Track> = emptyList()

  /** 获取用户歌单列表 */
  suspend fun getUserPlaylists(userId: Long): List<CoverItem> = emptyList()

  // ========== 推荐/热门接口 ==========

  /** 热门歌单 */
  suspend fun getTopPlaylists(limit: Int = 20, order: String = "hot", cat: String = "全部"): List<CoverItem> = emptyList()

  /** 精品歌单 */
  suspend fun getHighqualityPlaylists(limit: Int = 20, cat: String = "全部"): List<CoverItem> = emptyList()

  /** 热门歌单分类标签 */
  suspend fun getPlaylistHotTags(): List<String> = emptyList()

  // ========== 搜索接口 ==========

  /** 搜索歌单 */
  suspend fun searchPlaylists(keyword: String, limit: Int = 20): List<CoverItem> = emptyList()
}
