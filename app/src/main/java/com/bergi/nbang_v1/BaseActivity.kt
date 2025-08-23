package com.bergi.nbang_v1

import androidx.appcompat.app.AppCompatActivity

// 뒤로가기 애니메이션을 공통으로 처리하기 위한 부모 액티비티
abstract class BaseActivity : AppCompatActivity() {

    override fun finish() {
        super.finish()
        // 이 액티비티가 끝날 때, 슬라이드 아웃 애니메이션을 적용합니다.
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
