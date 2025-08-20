package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 파일 이름은 PostAdapter.kt 입니다.
class PostAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    // ViewHolder 클래스가 item_post.xml의 뷰들을 참조하도록 수정합니다.
    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // android.R.id.text1 대신 item_post.xml에 있는 실제 ID를 사용합니다.
        val titleTextView: TextView = itemView.findViewById(R.id.textViewPostTitle)
        val authorTextView: TextView = itemView.findViewById(R.id.textViewPostAuthor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        // android.R.layout.simple_list_item_2 대신 우리가 만든 item_post.xml을 사용하도록 수정합니다.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.titleTextView.text = post.title
        holder.authorTextView.text = post.author
    }

    override fun getItemCount(): Int {
        return posts.size
    }
}
