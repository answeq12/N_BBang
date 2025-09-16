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
        mannerScoreProgressBar = view.findViewById(R.id.progressBarMannerScore)
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
            startActivity(Intent(requireContext(), ReceivedReviewsActivity::class.java))
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
                val averageRating = if (reviews.isNotEmpty()) {
                    reviews.sumOf { it.rating.toDouble() } / reviews.size
                } else {
                    5.0 // 후기 없으면 5.0점
                }

                val progressPercentage = (averageRating / 10.0 * 100).toInt()

                mannerScoreTextView.text = "%.1f점".format(averageRating)
                mannerScoreProgressBar.progress = progressPercentage
            }
    }
}