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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter

    private lateinit var userLocationTextView: TextView
    private val TAG = "HomeFragment_DEBUG"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    private fun loadUserLocation() {
        val user = Firebase.auth.currentUser
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val location = document.getString("location")
                        if (location.isNullOrEmpty()) {
                            userLocationTextView.text = "프로필 탭에서 동네를 인증해주세요."
                        } else {
                            userLocationTextView.text = location
                        }
                    }
                }
        } else {
            userLocationTextView.text = "로그인이 필요합니다."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        val recyclerViewPosts = view.findViewById<RecyclerView>(R.id.recyclerViewPosts)
        val fabCreatePost = view.findViewById<FloatingActionButton>(R.id.fabCreatePost)
        userLocationTextView = view.findViewById<TextView>(R.id.textViewUserLocation) // 초기화

        setupRecyclerView(recyclerViewPosts)
        loadPosts()
        loadUserLocation() // 함수 호출 추가!

        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(requireContext(), PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = postAdapter
    }

    private fun loadPosts() {
        firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Error listening for posts documents", e)
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                val posts = mutableListOf<Post>()
                for (doc in snapshots.documents) {
                    try {
                        val post = doc.toObject(Post::class.java)
                        if (post != null) {
                            post.id = doc.id
                            posts.add(post)
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error converting document", ex)
                    }
                }
                postAdapter.updatePosts(posts)
            }
    }
}
