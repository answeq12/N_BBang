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
import androidx.viewpager2.widget.ViewPager2
import com.bergi.nbang_v1.data.Review
import com.google.android.gms.tasks.Tasks
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase

class UserProfileActivity : BaseActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private val auth = Firebase.auth
    private val TAG = "UserProfileActivity"

    private lateinit var toolbar: Toolbar
    private lateinit var nicknameTextView: TextView
    private lateinit var mannerScoreTextView: TextView
    private lateinit var mannerScoreProgressBar: ProgressBar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var buttonWriteReview: Button

    // [수정] 숨김 처리된 UI 요소들은 변수 선언에서 제거
    // private lateinit var createdPostsCountTextView: TextView
    // private lateinit var participatedPostsCountTextView: TextView
    // private lateinit var receivedReviewsCountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        firestore = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("USER_ID")

        if (userId == null) {
            Toast.makeText(this, "사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        initViews()
        setupToolbar()
        loadAndDisplayStats()
        setupViewPager()
        checkIfReviewCanBeWritten()

        buttonWriteReview.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java).putExtra("REVIEWED_USER_ID", userId))
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        nicknameTextView = findViewById(R.id.textViewUserNickname)
        mannerScoreTextView = findViewById(R.id.textViewMannerScore)
        mannerScoreProgressBar = findViewById(R.id.progressBarMannerScore)
        tabLayout = findViewById(R.id.tabLayoutPosts)
        viewPager = findViewById(R.id.viewPagerPosts)
        buttonWriteReview = findViewById(R.id.button_write_review)

        // [수정] 숨김 처리된 UI 요소들은 초기화 코드에서 제거
        // createdPostsCountTextView = findViewById(R.id.textViewCreatedPostsCount)
        // participatedPostsCountTextView = findViewById(R.id.textViewParticipatedPostsCount)
        // receivedReviewsCountTextView = findViewById(R.id.textViewReviewsCount)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadAndDisplayStats() {
        val userDocRef = firestore.collection("users").document(userId!!)
        val reviewsTask = firestore.collection("reviews").whereEqualTo("reviewedUserUid", userId).get()

        // [수정] 사용자 정보와 후기 정보만 불러오도록 변경
        Tasks.whenAllSuccess<Any>(userDocRef.get(), reviewsTask).addOnSuccessListener { results ->
            (results[0] as? DocumentSnapshot)?.let { userDoc ->
                val nickname = userDoc.getString("nickname") ?: "알 수 없는 사용자"
                nicknameTextView.text = nickname
                supportActionBar?.title = "$nickname 님의 프로필"
            }

            (results[1] as? com.google.firebase.firestore.QuerySnapshot)?.let {
                val reviews = it.toObjects<Review>()
                val averageRating = if (reviews.isNotEmpty()) reviews.sumOf { r -> r.rating.toDouble() } / reviews.size else 5.0
                val progressPercentage = (averageRating / 10.0 * 100).toInt()

                mannerScoreTextView.text = "%.1f점".format(averageRating)
                mannerScoreProgressBar.progress = progressPercentage
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "프로필 정보 로딩 실패", e)
        }
    }

    private fun checkIfReviewCanBeWritten() {
        val myUid = auth.currentUser?.uid
        val otherUserUid = userId
        if (myUid == null || otherUserUid == null || myUid == otherUserUid) {
            buttonWriteReview.visibility = View.GONE; return
        }

        firestore.collection("chatRooms")
            .whereEqualTo("isDealFullyCompleted", true)
            .whereArrayContains("participants", myUid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val canWriteReview = querySnapshot.documents.any { doc ->
                    (doc.get("participants") as? List<*>)?.contains(otherUserUid) == true
                }
                buttonWriteReview.visibility = if (canWriteReview) View.VISIBLE else View.GONE
            }
    }

    private fun setupViewPager() {
        // [수정] 탭이 2개이므로 UserProfilePagerAdapter도 2개의 Fragment를 처리하도록 수정해야 합니다.
        viewPager.adapter = UserProfilePagerAdapter(this, userId!!)

        // [수정] 탭 제목을 '작성한 글', '받은 후기' 2개로 변경
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "작성한 글"
                1 -> "받은 후기"
                else -> null
            }
        }.attach()
    }
}