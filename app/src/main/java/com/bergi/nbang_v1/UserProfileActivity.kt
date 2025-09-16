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
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        nicknameTextView = findViewById(R.id.textViewUserNickname)
        mannerScoreTextView = findViewById(R.id.textViewMannerScore)
        mannerScoreProgressBar = findViewById(R.id.progressBarMannerScore)
        tabLayout = findViewById(R.id.tabLayoutPosts)
        viewPager = findViewById(R.id.viewPagerPosts)

    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadAndDisplayStats() {
        val userDocRef = firestore.collection("users").document(userId!!)
        val reviewsTask = firestore.collection("reviews").whereEqualTo("reviewedUserUid", userId).get()

        Tasks.whenAllSuccess<Any>(userDocRef.get(), reviewsTask).addOnSuccessListener { results ->
            (results[0] as? DocumentSnapshot)?.let { userDoc ->
                val nickname = userDoc.getString("nickname") ?: "알 수 없는 사용자"
                nicknameTextView.text = nickname
                supportActionBar?.title = "$nickname 님의 프로필"
            }

            (results[1] as? com.google.firebase.firestore.QuerySnapshot)?.let { querySnapshot ->
                val reviews = querySnapshot.toObjects<Review>()
                Log.d(TAG, "Fetched ${reviews.size} review documents for user: $userId")

                // --- 여기부터 매너점수 계산 로직 수정 (MyFragment와 동일하게) ---
                val initialDefaultScoreFiveScale = 2.5 // 5점 만점 기준 초기 점수
                val sumOfActualRatingsFiveScale = reviews.sumOf { it.rating.toDouble() } // review.rating이 5점 만점 기준이라고 가정
                val numberOfActualReviews = reviews.size

                Log.d(TAG, "Initial default score (5-scale): $initialDefaultScoreFiveScale for user: $userId")
                Log.d(TAG, "Sum of actual ratings (5-scale): $sumOfActualRatingsFiveScale, Number of actual reviews: $numberOfActualReviews for user: $userId")

                // 전체 합계 (5점 만점 기준): 실제 리뷰 점수 합계 + 초기 기본 점수
                val totalSumForAverageFiveScale = sumOfActualRatingsFiveScale + initialDefaultScoreFiveScale
                // 전체 개수: 실제 리뷰 개수 + 초기 기본 점수 1개
                val totalCountForAverage = numberOfActualReviews + 1 

                Log.d(TAG, "Total sum for average calculation (5-scale): $totalSumForAverageFiveScale for user: $userId")
                Log.d(TAG, "Total count for average calculation: $totalCountForAverage for user: $userId")

                val averageRatingFiveScale = totalSumForAverageFiveScale / totalCountForAverage
                Log.d(TAG, "Final averageRatingFiveScale: $averageRatingFiveScale for user: $userId")

                val averageRatingTenScale = averageRatingFiveScale * 2.0 // 10점 만점으로 변환
                // --- 여기까지 매너점수 계산 로직 수정 ---

                Log.d(TAG, "Final averageRatingTenScale: $averageRatingTenScale for user: $userId")

                val progressPercentage = (averageRatingFiveScale / 5.0 * 100).toInt() // ProgressBar는 5점 만점 기준으로 계산
                Log.d(TAG, "Final progressPercentage: $progressPercentage for user: $userId")

                mannerScoreTextView.text = "%.1f점".format(averageRatingTenScale)
                mannerScoreProgressBar.progress = progressPercentage
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "프로필 정보 로딩 실패", e)
            // 오류 발생 시 UI 처리 (예: mannerScoreTextView.text = "오류")
        }
    }

    private fun setupViewPager() {
        viewPager.adapter = UserProfilePagerAdapter(this, userId!!)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "작성한 글"
                1 -> "받은 후기"
                else -> null
            }
        }.attach()
    }
}
