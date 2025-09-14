package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Post
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp // Timestamp import
import java.util.Date

// RecyclerView.Adapter를 상속받아 Post 데이터를 처리하는 어댑터입니다.
class PostAdapter(
    private val posts: MutableList<Post>,
    private val onItemClick: (Post) -> Unit // 아이템 클릭 시 실행될 람다 함수
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    // ViewHolder는 item_post.xml 레이아웃 내부의 뷰들을 보관하는 객체입니다.
    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val category: TextView = itemView.findViewById(R.id.textViewPostCategory)
        private val status: TextView = itemView.findViewById(R.id.textViewPostStatus)
        private val title: TextView = itemView.findViewById(R.id.textViewPostTitle)
        private val people: TextView = itemView.findViewById(R.id.textViewPostPeople)
        private val meetingPlace: TextView = itemView.findViewById(R.id.textViewPostMeetingPlace)
        private val timestamp: TextView = itemView.findViewById(R.id.textViewPostTimestamp)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.imageViewThumbnail)

        // bind 함수는 Post 객체 하나를 받아 뷰에 데이터를 채워넣는 역할을 합니다.
        fun bind(post: Post) {
            category.text = post.category
            status.text = post.status
            title.text = post.title
            people.text = "${post.currentPeople} / ${post.totalPeople}명"
            meetingPlace.text = post.meetingPlaceName
            timestamp.text = formatTimestamp(post.timestamp)

            // 사진이 있을 경우에만 썸네일 표시
            if (post.photoUrls.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(post.photoUrls[0])
                    .centerCrop()
                    .into(thumbnailImageView)
                thumbnailImageView.visibility = View.VISIBLE
            } else {
                Glide.with(itemView.context)
                    .load(R.drawable.n_1_logo) // 기본 이미지 로드
                    .centerCrop()
                    .into(thumbnailImageView)
                thumbnailImageView.visibility = View.VISIBLE
            }

            // 아이템 뷰가 클릭되었을 때, 생성자에서 받은 onItemClick 함수를 실행합니다.
            itemView.setOnClickListener {
                onItemClick(post)
            }
        }
    }

    // ViewHolder가 처음 생성될 때 호출됩니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    // 생성된 ViewHolder에 데이터를 바인딩(연결)할 때 호출됩니다.
    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    // RecyclerView가 표시할 전체 아이템의 개수를 반환합니다.
    override fun getItemCount(): Int {
        return posts.size
    }

    // Firestore에서 새로운 데이터를 불러왔을 때, RecyclerView를 갱신하는 함수입니다.
    fun updatePosts(newPosts: List<Post>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    // Firebase의 Timestamp를 "n분 전", "n시간 전"과 같은 상대 시간으로 변환하는 함수입니다.
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