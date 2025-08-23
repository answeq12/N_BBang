package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class UserProfileActivity : BaseActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private val TAG = "UserProfileActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        firestore = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("USER_ID")

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (userId == null) {
            finish()
            return
        }

        loadUserData()
        loadUserPosts()
    }

    private fun loadUserData() {
        val nicknameTextView: TextView = findViewById(R.id.textViewUserNickname)
        val mannerProgressBar: ProgressBar = findViewById(R.id.progressBarManner)

        firestore.collection("users").document(userId!!).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // --- User.kt 없이 직접 데이터를 가져옵니다 ---
                    val nickname = document.getString("nickname") ?: "알 수 없는 사용자"
                    // Firestore는 숫자를 Long으로 저장하므로, Long으로 받고 Int로 변환합니다.
                    val mannerScore = document.getLong("mannerScore")?.toInt() ?: 36

                    nicknameTextView.text = nickname
                    supportActionBar?.title = "$nickname 님의 프로필"
                    mannerProgressBar.progress = mannerScore
                } else {
                    Log.w(TAG, "User document not found.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting user document", e)
            }
    }

    private fun loadUserPosts() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewUserPosts)
        val postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = postAdapter

        firestore.collection("posts")
            .whereEqualTo("creatorUid", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snapshots ->
                val posts = snapshots.toObjects(Post::class.java).mapIndexed { index, post ->
                    post.apply { id = snapshots.documents[index].id }
                }
                postAdapter.updatePosts(posts)
            }
    }
}
