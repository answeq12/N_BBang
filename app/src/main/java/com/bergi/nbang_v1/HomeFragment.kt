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

    private val TAG = "HomeFragment_DEBUG"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        val recyclerViewPosts = view.findViewById<RecyclerView>(R.id.recyclerViewPosts)
        val fabCreatePost = view.findViewById<FloatingActionButton>(R.id.fabCreatePost)
        val userLocationTextView = view.findViewById<TextView>(R.id.textViewUserLocation)

        // setupRecyclerView, loadPosts 등은 Activity에서와 거의 동일
        setupRecyclerView(recyclerViewPosts)
        loadPosts()

        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }

        userLocationTextView.setOnClickListener {
            // 이 부분은 ProfileFragment로 이동하는 것이므로, MainActivity가 처리하도록 할 수 있습니다.
            // 지금은 임시로 Toast 메시지를 띄웁니다.
            // Toast.makeText(requireContext(), "내 정보는 하단 탭을 이용해주세요.", Toast.LENGTH_SHORT).show()
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
