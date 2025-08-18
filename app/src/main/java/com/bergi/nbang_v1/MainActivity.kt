package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity_DEBUG" // 로그 태그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: MainActivity displayed (fallback).")

        // 기본 레이아웃 설정 (LinearLayout)
        val linearLayout = LinearLayout(this)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.gravity = Gravity.CENTER
        linearLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        linearLayout.setPadding(40, 40, 40, 40) // 패딩 추가

        // 메시지 텍스트 뷰
        val textView = TextView(this)
        textView.text = "여기는 MainActivity 입니다. HomeActivity가 보여야 합니다. (수정 시도 2)"
        textView.textSize = 20f
        textView.gravity = Gravity.CENTER
        textView.setPadding(0,0,0,40) // 버튼과의 간격
        linearLayout.addView(textView)

        // 로그아웃 버튼
        val logoutButton = Button(this)
        logoutButton.text = "임시 로그아웃"
        logoutButton.setOnClickListener {
            Log.d(tag, "Logout button clicked.")
            Firebase.auth.signOut()
            Log.d(tag, "Firebase signOut() called.")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Log.d(tag, "Navigating to LoginActivity.")
            finish()
        }
        linearLayout.addView(logoutButton)

        setContentView(linearLayout) // 설정된 LinearLayout을 화면에 표시
    }
}
