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

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var textViewWelcome: TextView
    private lateinit var buttonLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth

        textViewWelcome = findViewById(R.id.textViewWelcome)
        buttonLogout = findViewById(R.id.buttonLogout)

        buttonLogout.setOnClickListener {
            signOut()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 사용자가 로그인되어 있음
            val welcomeMessage = if (!currentUser.displayName.isNullOrEmpty()) {
                "환영합니다, ${currentUser.displayName}님!"
            } else if (!currentUser.email.isNullOrEmpty()) {
                "로그인됨: ${currentUser.email}"
            } else {
                "환영합니다!" // displayName과 email 모두 없는 경우 (이론상 드묾)
            }
            textViewWelcome.text = welcomeMessage
        } else {
            // 사용자가 로그인되어 있지 않음 -> LoginActivity로 이동
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        }
    }

    private fun signOut() {
        auth.signOut()
        Toast.makeText(this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // MainActivity를 종료하여 뒤로가기 시 다시 보이지 않도록 함
    }
}
