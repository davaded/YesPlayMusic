package com.yesplaymusic.car.playback

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yesplaymusic.car.data.Track

class PlaybackViewModel : ViewModel() {
  val currentTrack = MutableLiveData<Track?>(null)
  val isPlaying = MutableLiveData(false)
  val positionMs = MutableLiveData(0L)
  val durationMs = MutableLiveData(0L)
  val statusText = MutableLiveData("")
  val queue = MutableLiveData<List<Track>>(emptyList())
  val queueIndex = MutableLiveData(-1)
}
