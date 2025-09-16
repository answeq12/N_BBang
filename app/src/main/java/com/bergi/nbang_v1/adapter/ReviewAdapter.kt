package com.bergi.nbang_v1.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Review

class ReviewAdapter(private val reviews: MutableList<Review>) :
    RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar_review_item)
        val commentTextView: TextView = itemView.findViewById(R.id.textView_review_comment_item)
        val reviewerNameTextView: TextView = itemView.findViewById(R.id.textView_reviewer_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]
        holder.ratingBar.rating = review.rating
        holder.commentTextView.text = review.comment
        // TODO: 후기 작성자 UID를 이용해 닉네임 가져와서 표시
    }

    override fun getItemCount(): Int {
        return reviews.size
    }

    fun updateReviews(newReviews: List<Review>) {
        reviews.clear()
        reviews.addAll(newReviews)
        notifyDataSetChanged()
    }
}