package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ReviewAdapter
import com.bergi.nbang_v1.data.Review
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase

class ReceivedReviewsActivity : BaseActivity() {

    private lateinit var reviewsRecyclerView: RecyclerView
    private lateinit var reviewAdapter: ReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_received_reviews)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        reviewsRecyclerView = findViewById(R.id.recyclerViewReceivedReviews)
        reviewAdapter = ReviewAdapter(mutableListOf())
        reviewsRecyclerView.layoutManager = LinearLayoutManager(this)
        reviewsRecyclerView.adapter = reviewAdapter

        fetchMyReviews()
    }

    private fun fetchMyReviews() {
        val myUid = Firebase.auth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("reviews")
            .whereEqualTo("reviewedUserUid", myUid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val reviews = documents.toObjects<Review>()
                reviewAdapter.updateReviews(reviews)
            }
            .addOnFailureListener {
                Log.e("ReceivedReviewsActivity", "후기 로딩 실패", it)
            }
    }
}