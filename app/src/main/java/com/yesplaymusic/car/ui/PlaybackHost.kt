package com.yesplaymusic.car.ui

import com.yesplaymusic.car.data.Track

interface PlaybackHost {
  fun playQueue(tracks: List<Track>, index: Int)
  fun playSingle(track: Track)
  fun togglePlay()
  fun skipNext()
  fun skipPrev()
  fun seekTo(positionMs: Long)
}
