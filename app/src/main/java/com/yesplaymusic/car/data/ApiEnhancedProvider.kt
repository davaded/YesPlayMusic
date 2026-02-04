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
      val response = client.newCall(Request.Builder().url(url).build()).execute()
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
