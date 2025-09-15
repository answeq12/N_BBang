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
        if (userId == null) return

        FirebaseFirestore.getInstance().collection("reviews")
            .whereEqualTo("reviewedUserUid", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null) {
                    val reviews = documents.toObjects<Review>()
                    // [수정] 3. 데이터 로딩이 끝나면 어댑터의 updateReviews 함수를 호출합니다.
                    reviewAdapter.updateReviews(reviews)
                }
            }
            .addOnFailureListener { exception ->
                Log.w("ReceivedReviewsFragment", "Error getting documents: ", exception)
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