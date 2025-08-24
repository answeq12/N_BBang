package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.util.Date

class PostDetailActivity : BaseActivity() { // BaseActivity 상속으로 애니메이션 복구

    private lateinit var firestore: FirebaseFirestore
    private var postId: String? = null
    private var currentPost: Post? = null

    // UI 요소
    private lateinit var categoryTextView: TextView
    private lateinit var timestampTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var contentTextView: TextView
    private lateinit var peopleTextView: TextView
    private lateinit var placeTextView: TextView
    private lateinit var joinButton: Button
    private lateinit var deleteButton: Button
    private lateinit var creatorNicknameTextView: TextView // 변수 이름 통일
    private lateinit var creatorProfileCard: MaterialCardView // 프로필 카드 변수 추가
    private lateinit var photoViewPager: ViewPager2
    private lateinit var photoCountTextView: TextView

    private val TAG = "PostDetailActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        firestore = FirebaseFirestore.getInstance()
        postId = intent.getStringExtra("POST_ID")

        // UI 요소 초기화
        categoryTextView = findViewById(R.id.textViewDetailCategory)
        timestampTextView = findViewById(R.id.textViewDetailTimestamp)
        titleTextView = findViewById(R.id.textViewDetailTitle)
        contentTextView = findViewById(R.id.textViewDetailContent)
        peopleTextView = findViewById(R.id.textViewDetailPeople)
        placeTextView = findViewById(R.id.textViewDetailPlace)
        joinButton = findViewById(R.id.buttonJoin)
        deleteButton = findViewById(R.id.buttonDelete)
        creatorNicknameTextView = findViewById(R.id.textViewCreatorNickname)
        creatorProfileCard = findViewById(R.id.cardViewCreatorProfile) // 프로필 카드 초기화 추가
        photoViewPager = findViewById(R.id.photoViewPager)
        photoCountTextView = findViewById(R.id.photoCountTextView)

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPostDetails()

        joinButton.setOnClickListener {
            Toast.makeText(this, "N빵 참여 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadPostDetails() {
        firestore.collection("posts").document(postId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentPost = document.toObject(Post::class.java)
                    if (currentPost != null) {
                        updateUI(currentPost!!)
                    } else {
                        handleLoadError()
                    }
                } else {
                    handleLoadError()
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting document: ", exception)
                handleLoadError()
            }
    }

    private fun updateUI(post: Post) {
        titleTextView.text = post.title
        categoryTextView.text = post.category
        contentTextView.text = post.content
        peopleTextView.text = "${post.currentPeople} / ${post.totalPeople}명"
        placeTextView.text = post.meetingPlace
        timestampTextView.text = formatTimestamp(post.timestamp)

        // 1. Firestore에서 작성자 닉네임을 불러오는 함수 호출
        loadCreatorInfo(post.creatorUid)

        // 2. 프로필 카드에 클릭 리스너 설정
        creatorProfileCard.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USER_ID", post.creatorUid)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }


        if (post.photoUrls.isNotEmpty()) {
            photoViewPager.adapter = PhotoAdapter(post.photoUrls)
            photoViewPager.visibility = View.VISIBLE
            photoCountTextView.visibility = View.VISIBLE

            photoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    photoCountTextView.text = "${position + 1} / ${post.photoUrls.size}"
                }
            })
            photoCountTextView.text = "1 / ${post.photoUrls.size}"
        } else {
            photoViewPager.visibility = View.GONE
            photoCountTextView.visibility = View.GONE
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && currentUser.uid == post.creatorUid) {
            deleteButton.visibility = View.VISIBLE
        } else {
            deleteButton.visibility = View.GONE
        }
    }

    // --- 이 함수를 다시 추가했습니다 ---
    private fun loadCreatorInfo(creatorId: String) {
        firestore.collection("users").document(creatorId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "알 수 없음"
                    creatorNicknameTextView.text = nickname
                } else {
                    creatorNicknameTextView.text = "알 수 없음"
                }
            }
            .addOnFailureListener {
                creatorNicknameTextView.text = "정보 로딩 실패"
            }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                deletePost()
            }
            .setNegativeButton("취소", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deletePost() {
        if (postId != null) {
            firestore.collection("posts").document(postId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "삭제에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun handleLoadError() {
        Toast.makeText(this, "게시글 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return ""
        val diff = Date().time - timestamp.toDate().time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}일 전"
            hours > 0 -> "${hours}시간 전"
            minutes > 0 -> "${minutes}분 전"
            else -> "방금 전"
        }
    }
}
