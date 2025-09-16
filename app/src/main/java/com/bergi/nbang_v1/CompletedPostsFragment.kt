package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class CompletedPostsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post_list, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewPostList)
        val emptyTextView: TextView = view.findViewById(R.id.textViewEmptyList)

        // [수정] PostAdapter 생성 시 onReviewClick 람다 함수 추가
        val postAdapter = PostAdapter(
            mutableListOf(),
            onItemClick = { post ->
                // 기존 아이템 클릭 시 상세 화면으로 이동
                val intent = Intent(requireContext(), PostDetailActivity::class.java)
                intent.putExtra("POST_ID", post.id)
                startActivity(intent)
            },
            onReviewClick = { post ->
                val intent = Intent(requireContext(), SelectRevieweeActivity::class.java)
                // 참여자 UID 목록을 ArrayList 형태로 변환하여 전달
                intent.putStringArrayListExtra("PARTICIPANT_UIDS", ArrayList(post.participants))
                intent.putExtra("CREATOR_UID", post.creatorUid)
                startActivity(intent)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = postAdapter

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("posts")
                .whereArrayContains("participants", currentUser.uid)
                .whereEqualTo("status", "거래완료")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("CompletedPostsFragment", "Error fetching completed posts: ", error)
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