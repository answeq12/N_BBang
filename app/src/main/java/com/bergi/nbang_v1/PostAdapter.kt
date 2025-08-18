package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PostAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    // ViewHolder class to hold the views for each item
    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(android.R.id.text1) // Example ID
        val authorTextView: TextView = itemView.findViewById(android.R.id.text2) // Example ID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        // Inflate the layout for each item
        // NOTE: You'''ll need to create a layout file for the post item, e.g., R.layout.item_post
        // For now, using a generic Android layout for simplicity, but this needs to be customized.
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
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
