package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class OngoingPostsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post_list, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewPostList)
        val emptyTextView: TextView = view.findViewById(R.id.textViewEmptyList)

        val postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(requireContext(), PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = postAdapter

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("posts")
                .whereArrayContains("participants", currentUser.uid)
                // --- 이 부분을 whereNotEqualTo에서 whereIn으로 수정했습니다 ---
                .whereIn("status", listOf("모집중", "모집완료"))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, _ ->
                    val posts = snapshots?.toObjects(Post::class.java)?.mapIndexed { index, post ->
                        post.apply { id = snapshots.documents[index].id }
                    } ?: emptyList()
                    postAdapter.updatePosts(posts)
                    emptyTextView.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                }
        }
        return view
    }
}
