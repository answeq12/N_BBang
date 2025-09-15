package com.bergi.nbang_v1

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class UserProfilePagerAdapter(fragmentActivity: FragmentActivity, private val userId: String) : FragmentStateAdapter(fragmentActivity) {

    // 탭 개수는 2개로 유지
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ProfilePostListFragment.newInstance(userId, "created")
            // [수정] 1번 탭에서는 ReceivedReviewsFragment를 보여주도록 변경
            1 -> ReceivedReviewsFragment.newInstance(userId)
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}