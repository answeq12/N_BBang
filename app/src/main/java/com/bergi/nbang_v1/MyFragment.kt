package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
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

        // 받은 후기 버튼을 눌렀을 때의 동작을 추가합니다.
        // ReceivedReviewsActivity는 미리 만들어두어야 합니다.
        receivedReviewsCard.setOnClickListener {
            val intent = Intent(requireContext(), ReceivedReviewsActivity::class.java)
            // 현재 로그인한 사용자의 UID를 "USER_ID"라는 이름으로 Intent에 추가
            intent.putExtra("USER_ID", user?.uid)
            startActivity(intent)
        }
    }

    // 닉네임과 매너 점수 정보를 불러오는 함수
    private fun loadMyInfo() {
        val user = Firebase.auth.currentUser ?: return
        nicknameTextView.text = user.displayName ?: user.email

        FirebaseFirestore.getInstance().collection("reviews")
            .whereEqualTo("reviewedUserUid", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                val reviews = documents.toObjects<Review>()
                val averageRatingFiveScale = if (reviews.isNotEmpty()) {
                    reviews.sumOf { it.rating.toDouble() } / reviews.size
                } else {
                    2.5 // << 후기 없을 때 5점 만점 기준 2.5점으로 설정 (10점 만점 기준 5.0점이 됨)
                }

                // 0-5점 스케일의 평균을 0-10점 스케일로 변환
                val averageRatingTenScale = averageRatingFiveScale * 2.0

                // ProgressBar는 0-5점 평균을 기준으로 100%를 채우도록 설정
                val progressPercentage = (averageRatingFiveScale / 5.0 * 100).toInt() // 2.5점일 경우 50%

                mannerScoreTextView.text = "%.1f점".format(averageRatingTenScale) // 10점 만점 기준으로 텍스트 표시
                mannerScoreProgressBar.progress = progressPercentage
            }
    }
}