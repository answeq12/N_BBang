package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log // Log import 추가
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bergi.nbang_v1.data.Review
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase

class MyFragment : Fragment() {
    private lateinit var nicknameTextView: TextView
    private lateinit var profileCard: MaterialCardView
    private lateinit var myActivitiesCard: MaterialCardView
    private lateinit var certifyLocationButton: Button
    private lateinit var keywordAlarmCard: MaterialCardView
    private lateinit var mannerScoreTextView: TextView
    private lateinit var mannerScoreProgressBar: ProgressBar
    private lateinit var receivedReviewsCard: MaterialCardView

    // ... (onCreateView, onViewCreated, onResume, initViews, setupClickListeners 생략) ...
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadMyInfo()
    }

    private fun initViews(view: View) {
        nicknameTextView = view.findViewById(R.id.textViewMyNickname)
        profileCard = view.findViewById(R.id.cardViewProfile)
        myActivitiesCard = view.findViewById(R.id.cardMyActivities)
        certifyLocationButton = view.findViewById(R.id.buttonCertifyLocation)
        keywordAlarmCard = view.findViewById(R.id.cardKeywordAlarm)
        mannerScoreTextView = view.findViewById(R.id.textViewMannerScore)
        mannerScoreProgressBar = view.findViewById(R.id.progressBarManner)
        receivedReviewsCard = view.findViewById(R.id.cardReceivedReviews)
    }

    private fun setupClickListeners() {
        val user = Firebase.auth.currentUser

        profileCard.setOnClickListener {
            (activity as? MainActivity)?.navigateToProfileFragment()
        }

        myActivitiesCard.setOnClickListener {
            val intent = Intent(requireContext(), MyActivitiesActivity::class.java)
            intent.putExtra("USER_ID", user?.uid)
            startActivity(intent)
        }
        keywordAlarmCard.setOnClickListener {
            startActivity(Intent(requireContext(), KeywordSettingsActivity::class.java))
        }

        certifyLocationButton.setOnClickListener {
            startActivity(Intent(requireContext(), VerifyLocationActivity::class.java))
        }

        receivedReviewsCard.setOnClickListener {
            val intent = Intent(requireContext(), ReceivedReviewsActivity::class.java)
            intent.putExtra("USER_ID", user?.uid)
            startActivity(intent)
        }
    }


    private fun loadMyInfo() {
        val user = Firebase.auth.currentUser
        if (user == null) {
            Log.e("MyFragment", "User is null, cannot load info.")
            return
        }
        nicknameTextView.text = user.displayName ?: user.email
        Log.d("MyFragment", "Loading info for user: ${user.uid}")

        FirebaseFirestore.getInstance().collection("reviews")
            .whereEqualTo("reviewedUserUid", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("MyFragment", "Fetched ${documents?.size() ?: 0} review documents for manner score.")
                val reviews = documents.toObjects<Review>()
                Log.d("MyFragment", "Converted to ${reviews.size} Review objects.")

                // --- 여기부터 매너점수 계산 로직 수정 ---
                val initialDefaultScore = 2.5 // 5점 만점 기준 초기 점수
                val sumOfActualRatings = reviews.sumOf { it.rating.toDouble() }
                val numberOfActualReviews = reviews.size

                Log.d("MyFragment", "Initial default score (5-scale): $initialDefaultScore")
                Log.d("MyFragment", "Sum of actual ratings: $sumOfActualRatings, Number of actual reviews: $numberOfActualReviews")

                // 전체 합계: 실제 리뷰 점수 합계 + 초기 기본 점수
                val totalSumForAverage = sumOfActualRatings + initialDefaultScore
                // 전체 개수: 실제 리뷰 개수 + 초기 기본 점수 1개
                val totalCountForAverage = numberOfActualReviews + 1

                Log.d("MyFragment", "Total sum for average calculation: $totalSumForAverage")
                Log.d("MyFragment", "Total count for average calculation: $totalCountForAverage")

                val averageRatingFiveScale = totalSumForAverage / totalCountForAverage
                // --- 여기까지 매너점수 계산 로직 수정 ---

                Log.d("MyFragment", "Final averageRatingFiveScale: $averageRatingFiveScale")

                val averageRatingTenScale = averageRatingFiveScale * 2.0
                Log.d("MyFragment", "Final averageRatingTenScale: $averageRatingTenScale")

                val progressPercentage = (averageRatingFiveScale / 5.0 * 100).toInt()
                Log.d("MyFragment", "Final progressPercentage: $progressPercentage")

                mannerScoreTextView.text = "%.1f점".format(averageRatingTenScale)
                mannerScoreProgressBar.progress = progressPercentage
                Log.d("MyFragment", "Displayed manner score: ${mannerScoreTextView.text}")
            }
            .addOnFailureListener { e ->
                Log.e("MyFragment", "Error fetching reviews for manner score: ", e)
            }
    }

}
