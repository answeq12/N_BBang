package com.bergi.nbang_v1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val chatFragment = ChatFragment()
    private val myFragment = MyFragment()
    private val profileFragment = ProfileFragment() // ProfileFragment 인스턴스 추가

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        replaceFragment(homeFragment, false) // 처음에는 애니메이션 없이 홈 화면 표시

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    replaceFragment(homeFragment, false)
                    true
                }
                R.id.navigation_chat -> {
                    replaceFragment(chatFragment, false)
                    true
                }
                R.id.navigation_my -> {
                    replaceFragment(myFragment, false)
                    true
                }
                else -> false
            }
        }
    }

    // --- 이 함수를 수정합니다 ---
    // 애니메이션 여부를 결정하는 'animated' 파라미터 추가
    private fun replaceFragment(fragment: Fragment, animated: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
        if (animated) {
            // 오른쪽에서 들어오고 왼쪽으로 나가는 애니메이션
            transaction.setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 애니메이션 없음
            transaction.setCustomAnimations(0, 0)
        }
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }

    // --- 이 함수를 새로 추가합니다 ---
    // MyFragment에서 호출할 프로필 화면 전환 함수
    fun navigateToProfileFragment() {
        val transaction = supportFragmentManager.beginTransaction()
        // 애니메이션과 함께 ProfileFragment로 교체
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        transaction.replace(R.id.fragment_container, profileFragment)
        // 뒤로가기 버튼을 눌렀을 때 MY 화면으로 돌아올 수 있도록 스택에 추가
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
