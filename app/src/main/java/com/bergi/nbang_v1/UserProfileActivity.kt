package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.tasks.Tasks
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class UserProfileActivity : BaseActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private val auth = Firebase.auth
    private val TAG = "UserProfileActivity"

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var nicknameTextView: TextView
    private lateinit var createdPostsCountTextView: TextView
    private lateinit var participatedPostsCountTextView: TextView
    private lateinit var likesCountTextView: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        firestore = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("USER_ID")

        if (userId == null) {
            Toast.makeText(this, "사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        loadAndDisplayStats()
        setupViewPager()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        nicknameTextView = findViewById(R.id.textViewUserNickname)
        createdPostsCountTextView = findViewById(R.id.textViewCreatedPostsCount)
        participatedPostsCountTextView = findViewById(R.id.textViewParticipatedPostsCount)
        likesCountTextView = findViewById(R.id.textViewLikesCount)
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

        val userTask = userDocRef.get()
        val createdPostsTask = firestore.collection("posts").whereEqualTo("creatorUid", userId).get()
        val participatedPostsTask = firestore.collection("posts").whereArrayContains("participants", userId!!).get()

        Tasks.whenAllSuccess<Any>(userTask, createdPostsTask, participatedPostsTask).addOnSuccessListener { results ->
            // User Data
            val userDocument = results[0] as com.google.firebase.firestore.DocumentSnapshot
            if (userDocument.exists()) {
                val nickname = userDocument.getString("nickname") ?: "알 수 없는 사용자"
                val mannerScore = userDocument.getDouble("mannerScore") ?: 36.5
                nicknameTextView.text = nickname
                supportActionBar?.title = "$nickname 님의 프로필"
                likesCountTextView.text = "%.1f".format(mannerScore)
            }

            // Created Posts Count
            val createdPostsSnapshot = results[1] as com.google.firebase.firestore.QuerySnapshot
            val createdCount = createdPostsSnapshot.size()
            createdPostsCountTextView.text = createdCount.toString()

            // Participated Posts Count
            val participatedPostsSnapshot = results[2] as com.google.firebase.firestore.QuerySnapshot
            val participatedCount = participatedPostsSnapshot.documents.count { it.getString("creatorUid") != userId }
            participatedPostsCountTextView.text = participatedCount.toString()

        }.addOnFailureListener { e ->
            Log.e(TAG, "Error loading profile stats", e)
            Toast.makeText(this, "프로필 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewPager() {
        val adapter = UserProfilePagerAdapter(this, userId!!)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "작성한 글"
                1 -> "참여한 글"
                else -> null
            }
        }.attach()
    }
}
