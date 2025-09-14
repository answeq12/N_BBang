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

        reviewedUserUid = intent.getStringExtra("REVIEWED_USER_ID")
            ?: run {
                Toast.makeText(this, "후기 대상을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        firestore.collection("users").document(reviewedUserUid).get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "사용자"
                textViewTargetUser.text = "${nickname}님에게 후기 남기기"
            }
            .addOnFailureListener { e ->
                Log.w("ReviewActivity", "후기 대상 사용자 정보 로드 실패", e)
                Toast.makeText(this, "후기 대상을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }

        buttonSubmitReview.setOnClickListener {
            submitReview()
        }
    }

    private fun submitReview() {
        val reviewerUid = auth.currentUser?.uid
        val rating = ratingBarReview.rating * 2
        val comment = editTextReviewComment.text.toString().trim()

        if (reviewerUid == null) {
            Toast.makeText(this, "로그인 상태를 확인해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rating == 0f) {
            Toast.makeText(this, "별점을 남겨주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (comment.isEmpty()) {
            Toast.makeText(this, "후기 내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val review = Review(
            reviewerUid = reviewerUid,
            reviewedUserUid = reviewedUserUid,
            rating = rating,
            comment = comment,
            timestamp = Timestamp.now()
        )

        firestore.collection("reviews")
            .add(review)
            .addOnSuccessListener {
                Toast.makeText(this, "후기가 성공적으로 제출되었습니다.", Toast.LENGTH_SHORT).show()
                updateMannerScore(rating)
                finish()
            }
            .addOnFailureListener { e ->
                Log.w("ReviewActivity", "후기 제출 실패", e)
                Toast.makeText(this, "후기 제출에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateMannerScore(newRating: Float) {
        val userRef = firestore.collection("users").document(reviewedUserUid)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentScore = snapshot.getDouble("mannerScore") ?: 0.0
            val reviewCount = snapshot.getLong("reviewCount") ?: 0L

            val newReviewCount = reviewCount + 1
            val totalScore = currentScore * reviewCount + newRating
            val newMannerScore = totalScore / newReviewCount

            val formattedScore = "%.1f".format(newMannerScore).toFloat()

            transaction.update(userRef, "mannerScore", formattedScore)
            transaction.update(userRef, "reviewCount", newReviewCount)
        }.addOnSuccessListener {
            Log.d("ReviewActivity", "매너 점수 업데이트 성공")
        }.addOnFailureListener { e ->
            Log.w("ReviewActivity", "매너 점수 업데이트 실패", e)
        }
    }
}