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
            durationMs = song.long("dt") ?: 0L
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
    return withContext(Dispatchers.IO) {
      val url = "$baseUrl/playlist/detail?id=$playlistId"
      val response = client.newCall(Request.Builder().url(url).build()).execute()
      response.use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val body = resp.body?.string() ?: return@withContext emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val tracks = root.obj("playlist")?.arr("tracks") ?: return@withContext emptyList()
        tracks.mapNotNull { item ->
          val track = item.asJsonObject
          val id = track.long("id")
          if (id == null) return@mapNotNull null
          Track(
            id = id,
            title = track.str("name") ?: "",
            artist = track.arr("ar")?.firstOrNull()?.asJsonObject?.str("name") ?: "",
            album = track.obj("al")?.str("name"),
            coverUrl = track.obj("al")?.str("picUrl"),
            durationMs = track.long("dt") ?: 0L
          )
        }
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
    const val DEFAULT_BASE_URL = "https://api-enhanced-sable.vercel.app"
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
