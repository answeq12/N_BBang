package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var recyclerViewPosts: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private lateinit var fabCreatePost: FloatingActionButton
    private lateinit var userLocationTextView: TextView

    private val TAG = "HomeActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        recyclerViewPosts = findViewById(R.id.recyclerViewPosts)
        fabCreatePost = findViewById(R.id.fabCreatePost)
        userLocationTextView = findViewById(R.id.textViewUserLocation)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        userLocationTextView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        setupRecyclerView()
        loadPosts()

        fabCreatePost.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        recyclerViewPosts.adapter = postAdapter
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
                            post.id = doc.id // 문서 ID를 객체에 직접 할당
                            posts.add(post)
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error converting document to Post object for doc ID: ${doc.id}", ex)
                    }
                }
                postAdapter.updatePosts(posts)
                Log.d(TAG, "Posts loaded. Count: ${posts.size}")
            }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
