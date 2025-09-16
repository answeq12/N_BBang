package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Post
import com.bumptech.glide.Glide
import java.util.Date

// [수정] 후기 작성 버튼 클릭 콜백 추가
class PostAdapter(
    private val posts: MutableList<Post>,
    private val onItemClick: (Post) -> Unit,
    private val onReviewClick: (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // [수정] item_post.xml의 ID와 일치하도록 뷰 변수 업데이트
        private val category: TextView = itemView.findViewById(R.id.textViewPostCategory)
        private val status: TextView = itemView.findViewById(R.id.textViewPostStatus)
        private val title: TextView = itemView.findViewById(R.id.textViewPostTitle)
        private val people: TextView = itemView.findViewById(R.id.textViewPostPeople)
        private val meetingPlace: TextView = itemView.findViewById(R.id.textViewPostMeetingPlace)
        private val timestamp: TextView = itemView.findViewById(R.id.textViewPostTimestamp)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
        private val creatorNickname: TextView = itemView.findViewById(R.id.textViewCreatorNickname)
        // [추가] 후기 작성 버튼
        private val writeReviewButton: Button = itemView.findViewById(R.id.buttonWriteReview)

        fun bind(post: Post) {
            category.text = post.category
            status.text = post.status
            title.text = post.title
            people.text = "${post.currentPeople} / ${post.totalPeople}명"
            meetingPlace.text = post.meetingPlaceName
            timestamp.text = formatTimestamp(post.timestamp)
            creatorNickname.text = post.creatorName

            if (post.photoUrls.isNotEmpty()) {
                Glide.with(itemView.context).load(post.photoUrls[0]).centerCrop().into(thumbnailImageView)
                thumbnailImageView.visibility = View.VISIBLE
            } else {
                Glide.with(itemView.context).load(R.drawable.n_1_logo).centerCrop().into(thumbnailImageView)
                thumbnailImageView.visibility = View.VISIBLE
            }

            // [추가] 게시글 상태에 따라 '후기 작성' 버튼 보이기/숨기기
            if (post.status == "거래완료") {
                writeReviewButton.visibility = View.VISIBLE
            } else {
                writeReviewButton.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(post) }
            writeReviewButton.setOnClickListener { onReviewClick(post) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int {
        return posts.size
    }

    fun updatePosts(newPosts: List<Post>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return ""
        val diff = Date().time - timestamp.toDate().time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}일 전"
            hours > 0 -> "${hours}시간 전"
            minutes > 0 -> "${minutes}분 전"
            else -> "방금 전"
        }
    }
}