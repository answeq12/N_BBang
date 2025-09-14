package com.bergi.nbang_v1

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class UserProfilePagerAdapter(fragmentActivity: FragmentActivity, private val userId: String) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ProfilePostListFragment.newInstance(userId, "created")
            1 -> ProfilePostListFragment.newInstance(userId, "participated")
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
