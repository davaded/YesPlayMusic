package com.yesplaymusic.car.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiEnhancedProvider(
  private val baseUrl: String = DEFAULT_BASE_URL,
  private val client: OkHttpClient = OkHttpClient()
) : MusicProvider {

  private var cookie: String? = null

  override fun setCookie(cookie: String?) {
    // 解析 cookie，提取 MUSIC_U 和 MUSIC_R_T
    val parsed = parseCookie(cookie)
    android.util.Log.d("ApiProvider", "setCookie 原始: ${cookie?.take(100)}...")
    android.util.Log.d("ApiProvider", "setCookie 解析后: $parsed")
    this.cookie = parsed
  }

  private fun parseCookie(rawCookie: String?): String? {
    if (rawCookie.isNullOrBlank()) return null
    // 提取所有 key=value 对，忽略 Max-Age, Expires, Path 等元数据
    val cookieParts = mutableListOf<String>()
    val segments = rawCookie.split(";").map { it.trim() }
    for (segment in segments) {
      val key = segment.substringBefore("=").trim()
      // 只保留实际的 cookie 值，忽略元数据
      if (key.equals("MUSIC_U", ignoreCase = true) ||
          key.equals("MUSIC_R_T", ignoreCase = true) ||
          key.equals("__csrf", ignoreCase = true)) {
        cookieParts.add(segment)
      }
    }
    return if (cookieParts.isNotEmpty()) cookieParts.joinToString("; ") else rawCookie
  }

  private fun buildRequest(url: String): Request {
    val builder = Request.Builder().url(url)
    cookie?.let {
      android.util.Log.d("ApiProvider", "添加 Cookie header: $it")
      builder.addHeader("Cookie", it)
    } ?: android.util.Log.w("ApiProvider", "Cookie 为空，未添加 header")
    return builder.build()
  }

  override suspend fun search(keyword: String, limit: Int): List<Track> {
    return withContext(Dispatchers.IO) {
      val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString())
      val url = "$baseUrl/search?keywords=$encoded&limit=$limit"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val songs = root.obj("result")?.arr("songs") ?: return@withContext emptyList()
        songs.mapNotNull { item ->
          val song = item.asJsonObject
          val id = song.long("id")
          if (id == null) return@mapNotNull null
          Track(
            id = id,
            title = song.str("name") ?: "",
            artist = song.arr("ar")?.firstOrNull()?.asJsonObject?.str("name") ?: "",
            album = song.obj("al")?.str("name"),
            coverUrl = song.obj("al")?.str("picUrl"),
            durationMs = song.long("dt") ?: 0L,
            mvId = song.long("mv") ?: song.long("mvid") ?: 0L
          )
        }
      }
    }
  }

  override suspend fun resolveStream(trackId: Long): StreamResource {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/song/url?id=$trackId&br=320000"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext StreamResource(url = "")
        val body = resp.body?.string() ?: return@withContext StreamResource(url = "")
        val root = JsonParser.parseString(body).asJsonObject
        val first = root.arr("data")?.firstOrNull()?.asJsonObject
        val streamUrl = first?.str("url") ?: ""
        val expires = first?.long("expires")?.let { it * 1000L }
        StreamResource(url = streamUrl, expiresAtMs = expires)
      }
    }
  }

  override suspend fun getPlaylist(playlistId: Long): List<Track> {
    return getPlaylistDetail(playlistId)?.tracks.orEmpty()
  }

  override suspend fun getPlaylistDetail(playlistId: Long): MediaDetail? {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/playlist/detail?id=$playlistId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        val playlist = root.obj("playlist") ?: return@withContext null
        val tracks = playlist.arr("tracks")?.mapNotNull { item ->
          parseTrack(item.asJsonObject)
        } ?: emptyList()
        MediaDetail(
          id = playlistId,
          title = playlist.str("name") ?: "",
          subtitle = playlist.obj("creator")?.str("nickname"),
          coverUrl = playlist.str("coverImgUrl"),
          tracks = tracks
        )
      }
    }
  }

  override suspend fun getAlbumDetail(albumId: Long): MediaDetail? {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/album?id=$albumId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        val album = root.obj("album") ?: return@withContext null
        val tracks = root.arr("songs")?.mapNotNull { item ->
          parseTrack(item.asJsonObject, album)
        } ?: emptyList()
        val artist = album.obj("artist")?.str("name")
          ?: album.arr("artists")?.firstOrNull()?.asJsonObject?.str("name")
        MediaDetail(
          id = albumId,
          title = album.str("name") ?: "",
          subtitle = artist,
          coverUrl = album.str("picUrl"),
          tracks = tracks
        )
      }
    }
  }

  override suspend fun getLyrics(trackId: Long): List<LyricLine> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/lyric?id=$trackId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val lyric = root.obj("lrc")?.str("lyric")
          ?: root.obj("yrc")?.str("lyric")
          ?: root.obj("tlyric")?.str("lyric")
        LyricsParser.parse(lyric)
      }
    }
  }

  override suspend fun getSongDetail(trackId: Long): SongDetail? {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/song/detail?ids=$trackId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        val song = root.arr("songs")?.firstOrNull()?.asJsonObject ?: return@withContext null
        val albumObj = song.obj("al")
        val artistNames = song.arr("ar")
          ?.mapNotNull { it.asJsonObject?.str("name") }
          ?.joinToString(" / ")
          ?: ""
        SongDetail(
          id = song.long("id") ?: trackId,
          title = song.str("name") ?: "",
          artist = artistNames,
          album = albumObj?.str("name"),
          albumId = albumObj?.long("id"),
          durationMs = song.long("dt") ?: 0L,
          mvId = song.long("mv") ?: song.long("mvid") ?: 0L,
          publishTime = albumObj?.long("publishTime")
        )
      }
    }
  }

  override suspend fun getMvUrl(mvId: Long): String {
    if (mvId <= 0L) return ""
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/mv/url?id=$mvId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext ""
        val body = resp.body?.string() ?: return@withContext ""
        val root = JsonParser.parseString(body).asJsonObject
        root.obj("data")?.str("url") ?: ""
      }
    }
  }

  override suspend fun getRecommendPlaylists(limit: Int): List<CoverItem> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/personalized?limit=$limit"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val items = root.arr("result") ?: return@withContext emptyList()
        items.mapNotNull { item ->
          val obj = item.asJsonObject
          val id = obj.long("id") ?: return@mapNotNull null
          CoverItem(
            id = id,
            title = obj.str("name") ?: "",
            subtitle = obj.str("copywriter"),
            coverUrl = obj.str("picUrl")
          )
        }
      }
    }
  }

  override suspend fun getNewAlbums(limit: Int): List<CoverItem> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/top/album?limit=$limit"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val items = root.arr("albums") ?: return@withContext emptyList()
        items.mapNotNull { item ->
          val obj = item.asJsonObject
          val id = obj.long("id") ?: return@mapNotNull null
          val artist = obj.arr("artists")?.firstOrNull()?.asJsonObject?.str("name")
          CoverItem(
            id = id,
            title = obj.str("name") ?: "",
            subtitle = artist,
            coverUrl = obj.str("picUrl")
          )
        }
      }
    }
  }

  override suspend fun getPersonalizedNewSongs(limit: Int): List<CoverItem> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/personalized/newsong?limit=$limit"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val items = root.arr("result") ?: return@withContext emptyList()
        items.mapNotNull { item ->
          val obj = item.asJsonObject
          val songObj = obj.obj("song") ?: obj
          val id = songObj.long("id") ?: obj.long("id") ?: return@mapNotNull null
          val artist = songObj.arr("artists")?.firstOrNull()?.asJsonObject?.str("name")
            ?: songObj.arr("ar")?.firstOrNull()?.asJsonObject?.str("name")
          val albumObj = songObj.obj("album") ?: songObj.obj("al")
          CoverItem(
            id = id,
            title = obj.str("name") ?: songObj.str("name") ?: "",
            subtitle = artist,
            coverUrl = albumObj?.str("picUrl")
          )
        }
      }
    }
  }

  // ========== 登录相关接口实现 ==========

  override suspend fun getQrKey(): String? {
    return withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      val url = "$baseUrl/login/qr/key?timestamp=$timestamp"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        root.obj("data")?.str("unikey")
      }
    }
  }

  override suspend fun createQrCode(key: String): String? {
    return withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      val url = "$baseUrl/login/qr/create?key=$key&qrimg=true&timestamp=$timestamp"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        root.obj("data")?.str("qrimg")
      }
    }
  }

  override suspend fun checkQrStatus(key: String): QrCheckResult {
    return withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      val url = "$baseUrl/login/qr/check?key=$key&timestamp=$timestamp"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext QrCheckResult(QrStatus.EXPIRED)
        val body = resp.body?.string() ?: return@withContext QrCheckResult(QrStatus.EXPIRED)
        val root = JsonParser.parseString(body).asJsonObject
        val code = root.get("code")?.asInt ?: 800
        val status = QrStatus.fromCode(code)
        val cookie = root.str("cookie")
        val message = root.str("message")
        QrCheckResult(status, cookie, message)
      }
    }
  }

  override suspend fun getLoginStatus(): LoginStatus {
    return withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      // 使用 /user/account 接口获取用户信息
      val url = "$baseUrl/user/account?timestamp=$timestamp"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) {
          android.util.Log.e("ApiProvider", "getLoginStatus 请求失败: ${resp.code}")
          return@withContext LoginStatus(false)
        }
        val body = resp.body?.string() ?: return@withContext LoginStatus(false)
        android.util.Log.d("ApiProvider", "getLoginStatus 响应: ${body.take(500)}")
        val root = JsonParser.parseString(body).asJsonObject
        val profile = root.obj("profile")
        val account = root.obj("account")
        val userId = profile?.long("userId") ?: account?.long("id")
        if (userId == null || userId == 0L) {
          android.util.Log.w("ApiProvider", "未找到用户ID, profile=$profile, account=$account")
          return@withContext LoginStatus(false)
        }
        val user = UserInfo(
          id = userId,
          nickname = profile?.str("nickname") ?: "",
          avatarUrl = profile?.str("avatarUrl"),
          vipType = profile?.get("vipType")?.asInt ?: 0
        )
        android.util.Log.i("ApiProvider", "用户信息解析成功: $user")
        LoginStatus(true, user)
      }
    }
  }

  override suspend fun getUserLikedPlaylist(userId: Long): CoverItem? {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/user/playlist?uid=$userId&limit=1"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body?.string() ?: return@withContext null
        val root = JsonParser.parseString(body).asJsonObject
        val playlists = root.arr("playlist") ?: return@withContext null
        val first = playlists.firstOrNull()?.asJsonObject ?: return@withContext null
        val id = first.long("id") ?: return@withContext null
        CoverItem(
          id = id,
          title = first.str("name") ?: "我喜欢的音乐",
          subtitle = "${first.get("trackCount")?.asInt ?: 0}首歌",
          coverUrl = first.str("coverImgUrl")
        )
      }
    }
  }

  // ========== 个性化推荐接口实现 ==========

  override suspend fun getDailyRecommendPlaylists(): List<CoverItem> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/recommend/resource"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val items = root.arr("recommend") ?: return@withContext emptyList()
        items.mapNotNull { item ->
          val obj = item.asJsonObject
          val id = obj.long("id") ?: return@mapNotNull null
          CoverItem(
            id = id,
            title = obj.str("name") ?: "",
            subtitle = obj.str("copywriter"),
            coverUrl = obj.str("picUrl")
          )
        }
      }
    }
  }

  override suspend fun getDailyRecommendSongs(): List<Track> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/recommend/songs"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val data = root.obj("data") ?: return@withContext emptyList()
        val songs = data.arr("dailySongs") ?: return@withContext emptyList()
        songs.mapNotNull { item ->
          parseTrack(item.asJsonObject)
        }
      }
    }
  }

  override suspend fun getUserPlaylists(userId: Long): List<CoverItem> {
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/user/playlist?uid=$userId&limit=30"
      val response = client.newCall(buildRequest(url)).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val playlists = root.arr("playlist") ?: return@withContext emptyList()
        playlists.mapNotNull { item ->
          val obj = item.asJsonObject
          val id = obj.long("id") ?: return@mapNotNull null
          CoverItem(
            id = id,
            title = obj.str("name") ?: "",
            subtitle = "${obj.get("trackCount")?.asInt ?: 0}首歌",
            coverUrl = obj.str("coverImgUrl")
          )
        }
      }
    }
  }

  companion object {
    const val DEFAULT_BASE_URL = "https://daidaiyuplay.daidaiyu.me"
  }
}

private fun JsonObject.obj(name: String): JsonObject? =
  get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arr(name: String) =
  get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.str(name: String): String? =
  get(name)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.long(name: String): Long? =
  get(name)?.takeIf { it.isJsonPrimitive }?.asLong

private fun JsonElement?.firstOrNull(): JsonElement? =
  if (this != null && this.isJsonArray && this.asJsonArray.size() > 0) this.asJsonArray[0] else null

private fun parseTrack(track: JsonObject, fallbackAlbum: JsonObject? = null): Track? {
  val id = track.long("id") ?: return null
  val albumObj = track.obj("al") ?: track.obj("album") ?: fallbackAlbum
  val artist = track.arr("ar")?.firstOrNull()?.asJsonObject?.str("name")
    ?: track.obj("artist")?.str("name")
    ?: albumObj?.obj("artist")?.str("name")
    ?: ""
  return Track(
    id = id,
    title = track.str("name") ?: "",
    artist = artist,
    album = albumObj?.str("name"),
    coverUrl = albumObj?.str("picUrl"),
    durationMs = track.long("dt") ?: track.long("duration") ?: 0L,
    mvId = track.long("mv") ?: track.long("mvid") ?: 0L
  )
}
