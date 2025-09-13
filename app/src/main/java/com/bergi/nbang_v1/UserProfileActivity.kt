package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post // ✅ Post 데이터 클래스 임포트
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

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
                    val nickname = document.getString("nickname") ?: "알 수 없는 사용자"
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