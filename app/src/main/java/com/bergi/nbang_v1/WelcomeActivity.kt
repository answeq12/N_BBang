package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
// Firebase 관련 import는 LoginActivity에서 닉네임을 받아오므로 직접적인 인증 처리가 필요 없어 삭제 가능
// import com.google.firebase.auth.FirebaseAuth
// import com.google.firebase.auth.ktx.auth
// import com.google.firebase.ktx.Firebase

class WelcomeActivity : AppCompatActivity() {

    // private lateinit var auth: FirebaseAuth // LoginActivity에서 닉네임 전달받으므로 삭제
    private lateinit var textViewWelcomeMessage: TextView // ID 변경

    private val welcomeDisplayTime: Long = 1000
    private val tag = "WelcomeActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: Activity started.")
        setContentView(R.layout.activity_welcome)
        Log.d(tag, "onCreate: setContentView(R.layout.activity_welcome) called.")

        // auth = Firebase.auth // 삭제
        // Log.d(tag, "onCreate: Firebase auth initialized.")
        textViewWelcomeMessage = findViewById(R.id.textViewWelcomeMessage) // ID 변경 반영
        Log.d(tag, "onCreate: textViewWelcomeMessage initialized.")

        // Intent로부터 닉네임 받기
        val nickname = intent.getStringExtra("NICKNAME")
        var welcomeText = "환영합니다!" // 기본 메시지

        if (!nickname.isNullOrEmpty()) {
            welcomeText = "환영합니다, ${nickname}님!"
        } else {
            // 닉네임이 없는 경우, 또는 이전처럼 Firebase에서 가져오거나 기본 메시지 사용
            // 여기서는 LoginActivity에서 전달된 닉네임이 없을 경우를 대비한 기본값을 설정합니다.
            // 만약 항상 닉네임이 전달된다고 가정하면 이 else 블록은 필요 없을 수 있습니다.
            Log.w(tag, "onCreate: Nickname is null or empty. Displaying default welcome message.")
            // 또는 이전처럼 currentUser에서 정보를 가져오는 로직을 여기에 추가할 수 있습니다.
            // 예: val currentUser = Firebase.auth.currentUser ... 등
        }

        textViewWelcomeMessage.text = welcomeText
        Log.d(tag, "onCreate: Welcome text set to: $welcomeText")

        Log.d(tag, "onCreate: Setting up Handler to start HomeActivity in $welcomeDisplayTime ms.")
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(tag, "postDelayed_Runnable: Attempting to start HomeActivity.")
            val intent = Intent(this, HomeActivity::class.java)
            Log.d(tag, "postDelayed_Runnable: Intent created for HomeActivity.")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Log.d(tag, "postDelayed_Runnable: startActivity(HomeActivity) called.")
            finish()
            Log.d(tag, "postDelayed_Runnable: WelcomeActivity finished.")
        }, welcomeDisplayTime)
    }
}
