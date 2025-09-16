// SelectRevieweeActivity.kt
package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ParticipantReviewAdapter
import com.bergi.nbang_v1.data.Participant
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SelectRevieweeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_reviewee)

        val participantUids = intent.getStringArrayListExtra("PARTICIPANT_UIDS")
        val creatorUid = intent.getStringExtra("CREATOR_UID")
        val myUid = Firebase.auth.currentUser?.uid

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewParticipants)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (creatorUid == null || myUid == null) {
            Toast.makeText(this, "사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 본인을 제외한 참여자 목록만 필터링
        val otherParticipantUids = participantUids?.filter { it != myUid } ?: emptyList()

        // 각 UID로 사용자 정보(닉네임) 가져오기
        val userFetchTasks = otherParticipantUids.map { Firebase.firestore.collection("users").document(it).get() }
        Tasks.whenAllSuccess<DocumentSnapshot>(userFetchTasks).addOnSuccessListener { documents ->
            val participants = documents.mapNotNull {
                Participant(
                    uid = it.id,
                    nickname = it.getString("nickname") ?: "알 수 없음",
                    isCreator = (it.id == creatorUid)
                )
            }
            recyclerView.adapter = ParticipantReviewAdapter(participants) { participant ->
                // '후기 남기기' 버튼 클릭 시 ReviewActivity로 이동
                val intent = Intent(this, ReviewActivity::class.java)
                intent.putExtra("REVIEWED_USER_ID", participant.uid)
                startActivity(intent)
            }
        }
    }
}