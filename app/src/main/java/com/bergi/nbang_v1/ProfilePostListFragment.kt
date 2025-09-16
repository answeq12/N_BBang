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
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProfilePostListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var postAdapter: PostAdapter
    private val firestore = FirebaseFirestore.getInstance()

    private var userId: String? = null
    private var listType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString(ARG_USER_ID)
            listType = it.getString(ARG_LIST_TYPE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile_post_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerViewProfilePosts)
        emptyTextView = view.findViewById(R.id.textViewEmptyList)

        setupRecyclerView()
        loadPosts()
    }

    private fun setupRecyclerView() {
        // [수정] 변경된 PostAdapter 생성자를 사용합니다.
        postAdapter = PostAdapter(
            mutableListOf(),
            onItemClick = { post ->
                val intent = Intent(requireContext(), PostDetailActivity::class.java)
                intent.putExtra("POST_ID", post.id)
                startActivity(intent)
            },
            onReviewClick = {
                // 프로필의 게시글 목록에서는 후기 작성 기능이 필요 없으므로 비워둡니다.
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = postAdapter
    }

    private fun loadPosts() {
        if (userId == null || listType == null) {
            emptyTextView.visibility = View.VISIBLE
            return
        }

        val query: Query = when (listType) {
            "created" -> firestore.collection("posts")
                .whereEqualTo("creatorUid", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
            "participated" -> firestore.collection("posts")
                .whereArrayContains("participants", userId!!)
                .orderBy("timestamp", Query.Direction.DESCENDING)
            else -> {
                emptyTextView.visibility = View.VISIBLE
                return
            }
        }

        query.get().addOnSuccessListener { snapshots ->
            if (snapshots == null || snapshots.isEmpty) {
                emptyTextView.visibility = View.VISIBLE
                return@addOnSuccessListener
            }

            val posts = snapshots.toObjects(Post::class.java).mapIndexedNotNull { index, post ->
                if (listType == "participated" && post.creatorUid == userId) {
                    null
                } else {
                    post.apply { id = snapshots.documents[index].id }
                }
            }

            if (posts.isEmpty()) {
                emptyTextView.visibility = View.VISIBLE
            } else {
                emptyTextView.visibility = View.GONE
                postAdapter.updatePosts(posts)
            }
        }.addOnFailureListener {
            emptyTextView.text = "글을 불러오는 데 실패했습니다."
            emptyTextView.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val ARG_USER_ID = "user_id"
        private const val ARG_LIST_TYPE = "list_type"

        @JvmStatic
        fun newInstance(userId: String, listType: String) =
            ProfilePostListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                    putString(ARG_LIST_TYPE, listType)
                }
            }
    }
}