package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast // ✅ 추가: Toast import
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.util.Arrays

class UserProfileActivity : BaseActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private val TAG = "UserProfileActivity"
    private lateinit var buttonWriteReview: Button
    private val auth = Firebase.auth

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
            Toast.makeText(this, "사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        buttonWriteReview = findViewById(R.id.button_write_review)

        loadUserData()
        loadUserPosts()
        checkCompletedDealsAndEnableReviewButton()
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
                Toast.makeText(this, "사용자 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
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

    private fun checkCompletedDealsAndEnableReviewButton() {
        val currentUserId = auth.currentUser?.uid
        val profileUserId = userId

        if (currentUserId == null || profileUserId == null || currentUserId == profileUserId) {
            return
        }

        firestore.collection("chatRooms")
            .whereArrayContainsAny("participants", Arrays.asList(currentUserId, profileUserId))
            .whereEqualTo("isCompleted", true)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    buttonWriteReview.visibility = View.VISIBLE
                    buttonWriteReview.setOnClickListener {
                        // TODO: ReviewActivity로 이동하는 로직 구현
                        // val intent = Intent(this@UserProfileActivity, ReviewActivity::class.java)
                        // intent.putExtra("REVIEWED_USER_ID", profileUserId)
                        // startActivity(intent)
                        Toast.makeText(this@UserProfileActivity, "후기 작성 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    buttonWriteReview.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "거래 완료된 채팅방 조회 실패", e)
                Toast.makeText(this@UserProfileActivity, "후기 작성 가능 여부를 확인하는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                buttonWriteReview.visibility = View.GONE
            }
    }
}
