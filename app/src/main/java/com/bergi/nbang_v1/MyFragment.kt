// ▼▼▼ [수정] 아래 MyFragment.kt 코드를 통째로 복사해서 사용하시면 됩니다. ▼▼▼
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
    // ▼▼▼ 모든 UI 변수를 클래스 레벨에서 선언해야 합니다. ▼▼▼
    private lateinit var nicknameTextView: TextView
    private lateinit var profileCard: MaterialCardView
    private lateinit var myActivitiesCard: MaterialCardView
    private lateinit var certifyLocationButton: Button
    private lateinit var keywordAlarmCard: MaterialCardView
    private lateinit var mannerScoreTextView: TextView
    private lateinit var mannerScoreProgressBar: ProgressBar
    private lateinit var receivedReviewsCard: MaterialCardView
    // ▲▲▲ 여기까지 ▲▲▲

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI 요소 초기화
        initViews(view)
        // 클릭 리스너 설정
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 내 정보를 새로고침합니다.
        loadMyInfo()
    }

    // UI 요소를 ID와 연결하는 함수
    private fun initViews(view: View) {
        nicknameTextView = view.findViewById(R.id.textViewMyNickname)
        profileCard = view.findViewById(R.id.cardViewProfile)
        myActivitiesCard = view.findViewById(R.id.cardMyActivities)
        certifyLocationButton = view.findViewById(R.id.buttonCertifyLocation)
        keywordAlarmCard = view.findViewById(R.id.cardKeywordAlarm)
        mannerScoreTextView = view.findViewById(R.id.textViewMannerScore)
        mannerScoreProgressBar = view.findViewById(R.id.progressBarMannerScore)
        // 받은 후기 카드뷰를 연결합니다.
        receivedReviewsCard = view.findViewById(R.id.cardReceivedReviews)
    }

    // 클릭 리스너를 설정하는 함수
    private fun setupClickListeners() {
        val user = Firebase.auth.currentUser

        profileCard.setOnClickListener {
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("USER_ID", user?.uid)
            startActivity(intent)
        }

        myActivitiesCard.setOnClickListener {
            startActivity(Intent(requireContext(), MyActivitiesActivity::class.java))
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