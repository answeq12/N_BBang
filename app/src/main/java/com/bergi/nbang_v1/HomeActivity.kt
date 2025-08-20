package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var textViewUserLocation: TextView
    private lateinit var recyclerViewPosts: RecyclerView
    private lateinit var fabCreatePost: FloatingActionButton

    private val tag = "HomeActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = Firebase.auth

        // 사용자가 로그인 상태가 아니면 즉시 로그인 화면으로 보냄
        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }

        initViews()
        setupUI()
    }

    private fun initViews() {
        textViewUserLocation = findViewById(R.id.textViewUserLocation)
        recyclerViewPosts = findViewById(R.id.recyclerViewPosts)
        fabCreatePost = findViewById(R.id.fabCreatePost)
    }

    private fun setupUI() {
        val currentUser = auth.currentUser!! // 로그인 상태는 위에서 확인했으므로 !! 사용 가능

        // 1. 환영 메시지 설정
        val displayName = currentUser.displayName
        val welcomeMessage = if (!displayName.isNullOrEmpty()) {
            "환영합니다, $displayName 님!"
        } else {
            "환영합니다!"
        }
        textViewUserLocation.text = welcomeMessage

        // 2. 환영 메시지를 클릭하면 프로필 화면으로 이동
        textViewUserLocation.setOnClickListener {
            Log.d(tag, "Welcome message clicked. Navigating to ProfileActivity.")
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        // 3. RecyclerView 설정 (샘플 데이터 사용)
        recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        val samplePosts = listOf(
            Post("첫 번째 게시글", "작성자 A"),
            Post("두 번째 이야기", "사용자 B"),
            Post("세 번째 공지사항", "관리자")
        )
        recyclerViewPosts.adapter = PostAdapter(samplePosts)

        // 4. FAB 버튼 리스너 (기능은 추후 구현)
        fabCreatePost.setOnClickListener {
            Log.d(tag, "fabCreatePost clicked.")
            // TODO: 새 글 작성 화면으로 이동하는 로직 구현
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
