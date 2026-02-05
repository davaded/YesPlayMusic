package com.yesplaymusic.car.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

  override fun getItemCount(): Int = 5

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> HomeFragment()
      1 -> RecommendFragment()
      2 -> SearchFragment()
      3 -> PlayerFragment()
      4 -> QueueFragment()
      else -> HomeFragment()
    }
  }
}
