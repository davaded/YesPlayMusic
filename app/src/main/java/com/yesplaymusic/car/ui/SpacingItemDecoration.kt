package com.yesplaymusic.car.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration(
  private val horizontal: Int = 0,
  private val vertical: Int = 0
) : RecyclerView.ItemDecoration() {
  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    outRect.left = horizontal
    outRect.right = horizontal
    outRect.top = vertical
    outRect.bottom = vertical
  }
}
