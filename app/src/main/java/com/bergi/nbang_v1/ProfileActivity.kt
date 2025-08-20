package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = Firebase.auth

        // UI 요소 초기화
        val nicknameTextView = findViewById<TextView>(R.id.textViewProfileNickname)
        val emailTextView = findViewById<TextView>(R.id.textViewProfileEmail)
        val phoneTextView = findViewById<TextView>(R.id.textViewProfilePhone)
        val editProfileButton = findViewById<Button>(R.id.buttonEditProfile)
        val logoutButton = findViewById<Button>(R.id.buttonLogout)

        // 현재 로그인된 사용자 정보 가져오기
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // 혹시 사용자가 로그아웃 상태이면 로그인 화면으로 보냄
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 화면에 사용자 정보 표시
        nicknameTextView.text = currentUser.displayName ?: "닉네임 정보 없음"
        emailTextView.text = currentUser.email ?: "이메일 정보 없음"
        phoneTextView.text = currentUser.phoneNumber ?: "휴대폰 정보 없음"

        // 정보 수정 버튼 리스너 (지금은 Toast 메시지만 표시)
        editProfileButton.setOnClickListener {
            Toast.makeText(this, "정보 수정 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        // 로그아웃 버튼 리스너
        logoutButton.setOnClickListener {
            // Firebase에서 로그아웃
            auth.signOut()

            // 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            // 이전 화면 기록을 모두 지워서 뒤로가기 시 다시 돌아오지 않도록 함
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
