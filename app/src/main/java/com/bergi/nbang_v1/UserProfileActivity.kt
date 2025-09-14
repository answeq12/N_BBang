package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.adapter.ReviewAdapter
import com.bergi.nbang_v1.data.Post
import com.bergi.nbang_v1.data.Review
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

    private lateinit var recyclerViewUserReviews: RecyclerView
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var recyclerViewUserPosts: RecyclerView
    private lateinit var postAdapter: PostAdapter

    private lateinit var tabReceivedReviews: TextView
    private lateinit var tabWrittenPosts: TextView

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

        tabReceivedReviews = findViewById(R.id.tabReceivedReviews)
        tabWrittenPosts = findViewById(R.id.tabWrittenPosts)

        recyclerViewUserReviews = findViewById(R.id.recyclerViewUserReviews)
        recyclerViewUserPosts = findViewById(R.id.recyclerViewUserPosts)

        setupRecyclerViews()

        loadUserData()
        loadUserReviews()
        loadUserPosts()
        checkCompletedDealsAndEnableReviewButton()

        // ✅ 탭 클릭 리스너 설정
        tabReceivedReviews.setOnClickListener {
            showReceivedReviewsTab()
        }

        tabWrittenPosts.setOnClickListener {
            showWrittenPostsTab()
        }

        // 초기 화면은 '작성한 N빵' 탭으로 설정
        showWrittenPostsTab()
    }

    private fun setupRecyclerViews() {
        reviewAdapter = ReviewAdapter(mutableListOf())
        recyclerViewUserReviews.layoutManager = LinearLayoutManager(this)
        recyclerViewUserReviews.adapter = reviewAdapter

        postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerViewUserPosts.layoutManager = LinearLayoutManager(this)
        recyclerViewUserPosts.adapter = postAdapter
    }

    private fun showReceivedReviewsTab() {
        // ✅ '받은 후기' 탭을 활성화
        tabReceivedReviews.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tabReceivedReviews.background = ContextCompat.getDrawable(this, R.drawable.tab_selected_background)

        tabWrittenPosts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tabWrittenPosts.background = ContextCompat.getDrawable(this, R.drawable.tab_unselected_background)

        recyclerViewUserReviews.visibility = View.VISIBLE
        recyclerViewUserPosts.visibility = View.GONE
    }

    private fun showWrittenPostsTab() {
        // ✅ '작성한 N빵' 탭을 활성화
        tabReceivedReviews.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tabReceivedReviews.background = ContextCompat.getDrawable(this, R.drawable.tab_unselected_background)

        tabWrittenPosts.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tabWrittenPosts.background = ContextCompat.getDrawable(this, R.drawable.tab_selected_background)

        recyclerViewUserReviews.visibility = View.GONE
        recyclerViewUserPosts.visibility = View.VISIBLE
    }

    private fun loadUserReviews() {
        if (userId == null) return

        firestore.collection("reviews")
            .whereEqualTo("reviewedUserUid", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                val reviews = snapshots.toObjects(Review::class.java).toMutableList()
                reviewAdapter.updateReviews(reviews)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting user reviews", e)
            }
    }

    private fun loadUserPosts() {
        if (userId == null) return

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

    private fun loadUserData() {
        val nicknameTextView: TextView = findViewById(R.id.textViewUserNickname)
        val mannerProgressBar: ProgressBar = findViewById(R.id.progressBarManner)
        val mannerScoreTextView: TextView = findViewById(R.id.textViewMannerScore)

        firestore.collection("users").document(userId!!).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "알 수 없는 사용자"
                    val mannerScore = document.getDouble("mannerScore") ?: 0.0

                    nicknameTextView.text = nickname
                    supportActionBar?.title = "$nickname 님의 프로필"
                    mannerProgressBar.progress = (mannerScore * 10).toInt()
                    mannerScoreTextView.text = "%.1f점".format(mannerScore)
                } else {
                    Log.w(TAG, "User document not found.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting user document", e)
                Toast.makeText(this, "사용자 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
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
                        val intent = Intent(this@UserProfileActivity, ReviewActivity::class.java)
                        intent.putExtra("REVIEWED_USER_ID", profileUserId)
                        startActivity(intent)
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
