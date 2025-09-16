package com.bergi.nbang_v1

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import com.bergi.nbang_v1.data.Review
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.ktx.Firebase

class ReviewActivity : AppCompatActivity() {

    private lateinit var reviewedUserUid: String
    private lateinit var postId: String
    private lateinit var textViewTargetUser: TextView
    private lateinit var ratingBarReview: RatingBar
    private lateinit var editTextReviewComment: EditText
    private lateinit var buttonSubmitReview: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        textViewTargetUser = findViewById(R.id.textView_target_user)
        ratingBarReview = findViewById(R.id.ratingBar_review)
        editTextReviewComment = findViewById(R.id.editText_review_comment)
        buttonSubmitReview = findViewById(R.id.button_submit_review)

        reviewedUserUid = intent.getStringExtra("REVIEWED_USER_ID") ?: run {
            finishWithMessage("후기 대상을 찾을 수 없습니다."); return
        }
        postId = intent.getStringExtra("POST_ID") ?: run {
            finishWithMessage("게시글 정보를 찾을 수 없습니다."); return
        }

        loadTargetUserInfo()
        buttonSubmitReview.setOnClickListener { submitReview() }
    }

    private fun loadTargetUserInfo() {
        firestore.collection("users").document(reviewedUserUid).get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "사용자"
                textViewTargetUser.text = "${nickname}님에게 후기 남기기"
            }
            .addOnFailureListener {
                finishWithMessage("후기 대상을 불러오는 데 실패했습니다.")
            }
    }

    private fun submitReview() {
        val reviewerUid = auth.currentUser?.uid ?: return
        val rating = ratingBarReview.rating
        val comment = editTextReviewComment.text.toString().trim()

        if (rating == 0f || comment.isEmpty()) {
            Toast.makeText(this, "별점과 후기 내용을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val reviewData = hashMapOf(
            "reviewerUid" to reviewerUid,
            "reviewedUserUid" to reviewedUserUid,
            "postId" to postId,
            "rating" to rating,
            "comment" to comment,
            "reviewerNickname" to (auth.currentUser?.displayName ?: "익명"),
            "createdAt" to Timestamp.now()
        )

        firestore.collection("reviews").add(reviewData)
            .addOnSuccessListener {
                updatePostReviewStatus()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "후기 제출에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePostReviewStatus() {
        val reviewerUid = auth.currentUser?.uid ?: return
        firestore.collection("posts").document(postId)
            .update("reviewsWritten.$reviewerUid", FieldValue.arrayUnion(reviewedUserUid))
            .addOnSuccessListener {
                Toast.makeText(this, "후기가 성공적으로 제출되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "후기 상태 기록에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}