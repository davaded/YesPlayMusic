package com.yesplaymusic.car.data

data class StreamResource(
  val url: String,
  val headers: Map<String, String> = emptyMap(),
  val expiresAtMs: Long? = null
)
