package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ParticipantReviewAdapter
import com.bergi.nbang_v1.data.Participant
import com.bergi.nbang_v1.data.Post
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class SelectRevieweeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_reviewee)

        val postId = intent.getStringExtra("POST_ID")
        val myUid = Firebase.auth.currentUser?.uid

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewParticipants)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (postId == null || myUid == null) {
            Toast.makeText(this, "정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Firebase.firestore.collection("posts").document(postId).get()
            .addOnSuccessListener { postDocument ->
                val post = postDocument.toObject(Post::class.java) ?: return@addOnSuccessListener
                val alreadyReviewedUids = post.reviewsWritten[myUid] ?: emptyList()
                val otherParticipantUids = post.participants.filter { it != myUid }

                val userFetchTasks = otherParticipantUids.map { Firebase.firestore.collection("users").document(it).get() }
                Tasks.whenAllSuccess<DocumentSnapshot>(userFetchTasks).addOnSuccessListener { userDocuments ->
                    val participants = userDocuments.mapNotNull {
                        Participant(
                            uid = it.id,
                            nickname = it.getString("nickname") ?: "알 수 없음",
                            isCreator = (it.id == post.creatorUid)
                        )
                    }
                    recyclerView.adapter = ParticipantReviewAdapter(participants, alreadyReviewedUids) { participant ->
                        val intent = Intent(this, ReviewActivity::class.java)
                        intent.putExtra("REVIEWED_USER_ID", participant.uid)
                        intent.putExtra("POST_ID", postId)
                        startActivity(intent)
                    }
                }
            }
    }
}