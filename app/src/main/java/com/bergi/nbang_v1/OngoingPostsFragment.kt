package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post // ✅ Post 데이터 클래스 임포트
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class OngoingPostsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post_list, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewPostList)
        val emptyTextView: TextView = view.findViewById(R.id.textViewEmptyList)

        // [수정] PostAdapter 생성자를 새 형식에 맞게 수정합니다.
        val postAdapter = PostAdapter(
            mutableListOf(),
            onItemClick = { post ->
                val intent = Intent(requireContext(), PostDetailActivity::class.java)
                intent.putExtra("POST_ID", post.id)
                startActivity(intent)
            },
            onReviewClick = { post ->
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = postAdapter

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("posts")
                .whereArrayContains("participants", currentUser.uid)
                .whereIn("status", listOf("모집중", "모집완료"))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("OngoingPostsFragment", "Error fetching ongoing posts: ", error)
                        emptyTextView.visibility = View.VISIBLE
                        postAdapter.updatePosts(emptyList())
                        return@addSnapshotListener
                    }

                    val posts = snapshots?.toObjects(Post::class.java)?.mapIndexed { index, post ->
                        post.apply { id = snapshots.documents[index].id }
                    } ?: emptyList()

                    postAdapter.updatePosts(posts)
                    emptyTextView.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                }
        } else {
            emptyTextView.visibility = View.VISIBLE
        }
        return view
    }
}