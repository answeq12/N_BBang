package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var textViewWelcomeMessage: TextView
    private val welcomeDisplayTime: Long = 2000 // 환영 메시지 표시 시간 (2초)
    private val tag = "WelcomeActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // XML ID와 일치하도록 수정했습니다.
        textViewWelcomeMessage = findViewById(R.id.textViewWelcomeMessage)

        // Intent로부터 닉네임 받기
        val nickname = intent.getStringExtra("NICKNAME")
        var welcomeText = "환영합니다!" // 기본 메시지

        if (!nickname.isNullOrEmpty()) {
            welcomeText = "${nickname}님, 환영합니다!"
        } else {
            Log.w(tag, "onCreate: Nickname is null or empty.")
        }

        textViewWelcomeMessage.text = welcomeText

        // 설정된 시간 후에 MainActivity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, welcomeDisplayTime)
    }
}
