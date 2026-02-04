package com.yesplaymusic.car.data

object ProviderRegistry {
  @Volatile
  private var provider: MusicProvider = ApiEnhancedProvider()

  fun get(): MusicProvider = provider

  fun set(customProvider: MusicProvider) {
    provider = customProvider
  }
}
