package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ReviewAdapter
import com.bergi.nbang_v1.data.Review
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects

class ReceivedReviewsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var reviewAdapter: ReviewAdapter
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString(ARG_USER_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_received_reviews, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewUserReviews)

        // [수정] 1. 어댑터를 먼저 빈 리스트로 생성하고 RecyclerView에 연결합니다.
        reviewAdapter = ReviewAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = reviewAdapter

        // 2. 데이터를 불러옵니다.
        fetchReviews()

        return view
    }

    private fun fetchReviews() {
        // (1) userId 값 확인
        if (userId == null) {
            Log.e("ReceivedReviewsFragment", "userId is NULL. Cannot fetch reviews.") // 오류 수준으로 변경, 더 눈에 띄도록
            return
        }
        Log.d("ReceivedReviewsFragment", "Attempting to fetch reviews for userId: $userId")

        FirebaseFirestore.getInstance().collection("reviews")
            .whereEqualTo("reviewedUserUid", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                // (2) 성공 리스너 진입 및 가져온 문서 개수 확인
                Log.d("ReceivedReviewsFragment", "addOnSuccessListener called.")
                if (documents != null && !documents.isEmpty) {
                    Log.d("ReceivedReviewsFragment", "Successfully fetched ${documents.size()} documents.")
                    try { // (3) toObjects 변환 과정에서의 예외 발생 가능성 확인
                        val reviews = documents.toObjects<Review>()
                        Log.d("ReceivedReviewsFragment", "Converted to ${reviews.size} Review objects.")
                        reviewAdapter.updateReviews(reviews)
                    } catch (e: Exception) {
                        Log.e("ReceivedReviewsFragment", "Error converting documents to Review objects: ", e)
                    }
                } else {
                    // (4) 문서를 가져왔지만 비어있거나 documents 객체 자체가 null인 경우
                    Log.w("ReceivedReviewsFragment", "No documents found for userId: $userId, or documents object was null/empty. Document count: ${documents?.size() ?: "null"}")
                    reviewAdapter.updateReviews(emptyList()) // UI 상 빈 상태를 명확히 표시
                }
            }
            .addOnFailureListener { exception ->
                // (5) 실패 리스너 진입 및 예외 상세 내용 확인 (가장 중요할 수 있음)
                Log.e("ReceivedReviewsFragment", "Error getting documents from Firestore: ", exception)
            }
    }


    companion object {
        private const val ARG_USER_ID = "user_id"

        @JvmStatic
        fun newInstance(userId: String) =
            ReceivedReviewsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                }
            }
    }
}