package com.yesplaymusic.car.ui

import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType
import com.yesplaymusic.car.data.Track

interface DetailNavigator {
  fun openMediaDetail(type: MediaType, item: CoverItem)
  fun openDailyDetail(tracks: List<Track>, item: CoverItem)
}
