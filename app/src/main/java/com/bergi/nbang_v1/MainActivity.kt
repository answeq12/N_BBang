package com.bergi.nbang_v1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val chatFragment = ChatFragment()
    private val profileFragment = ProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // 앱 시작 시 첫 화면으로 HomeFragment를 설정
        replaceFragment(homeFragment)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    replaceFragment(homeFragment)
                    true
                }
                R.id.navigation_chat -> {
                    replaceFragment(chatFragment)
                    true
                }
                R.id.navigation_profile -> {
                    replaceFragment(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        // supportFragmentManager를 사용해 Fragment를 교체합니다.
        val transaction = supportFragmentManager.beginTransaction()
        // 애니메이션 효과를 없애기 위해 0, 0을 전달합니다.
        transaction.setCustomAnimations(0, 0)
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}
