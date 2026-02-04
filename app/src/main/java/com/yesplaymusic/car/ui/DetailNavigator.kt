package com.yesplaymusic.car.ui

import com.yesplaymusic.car.data.CoverItem
import com.yesplaymusic.car.data.MediaType

interface DetailNavigator {
  fun openMediaDetail(type: MediaType, item: CoverItem)
}
