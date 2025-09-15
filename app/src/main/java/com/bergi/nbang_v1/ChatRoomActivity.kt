package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.MessageAdapter
import com.bergi.nbang_v1.adapter.ParticipantAdapter
import com.bergi.nbang_v1.data.ChatRoom
import com.bergi.nbang_v1.data.Message
import com.bergi.nbang_v1.data.Participant
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var completeDealButton: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var participantsRecyclerView: RecyclerView
    private lateinit var completeRecruitmentButtonInDrawer: Button
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth
    private var chatRoomId: String? = null
    private val TAG = "ChatRoomActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)
        initViews()

        chatRoomId = intent.getStringExtra("chatRoomId")
        if (chatRoomId == null) {
            Toast.makeText(this, "채팅방 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        setupToolbar()
        setupMessagesRecyclerView()
        loadMessages()
        listenToChatRoomChanges()

        sendButton.setOnClickListener { sendMessage() }
        completeDealButton.setOnClickListener { onCompleteDealClicked() }
    }

    private fun initViews() { /* ... findViewById 코드 ... */ }
    private fun setupToolbar() { /* ... 툴바 설정 코드 ... */ }
    private fun setupMessagesRecyclerView() { /* ... 리사이클러뷰 설정 코드 ... */ }
    private fun loadMessages() { /* ... 메시지 로딩 코드 ... */ }
    private fun sendMessage() { /* ... 메시지 전송 코드 ... */ }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean { /* ... 메뉴 생성 코드 ... */ return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { /* ... 메뉴 아이템 선택 코드 ... */ return super.onOptionsItemSelected(item) }

    private fun listenToChatRoomChanges() {
        chatRoomId?.let { id ->
            firestore.collection("chatRooms").document(id).addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    Log.w(TAG, "채팅방 정보 리스닝 실패", e); return@addSnapshotListener
                }
                val chatRoom = snapshot.toObject(ChatRoom::class.java) ?: return@addSnapshotListener
                updateUI(chatRoom)
                setupParticipantsList(chatRoom)
            }
        }
    }

    private fun updateUI(chatRoom: ChatRoom) {
        val myUid = auth.currentUser?.uid ?: return
        toolbar.title = chatRoom.postTitle ?: "채팅방"
        toolbar.subtitle = "참여자: ${chatRoom.participants?.size ?: 0}명"

        val isCreator = myUid == chatRoom.creatorUid
        if (isCreator && chatRoom.status != "모집완료") {
            completeRecruitmentButtonInDrawer.visibility = View.VISIBLE
            completeRecruitmentButtonInDrawer.setOnClickListener { completeRecruitment(chatRoom.postId) }
        } else {
            completeRecruitmentButtonInDrawer.visibility = View.GONE
        }

        val myCompletionStatus = chatRoom.completionStatus?.get(myUid) ?: false
        if (chatRoom.status == "모집완료" && chatRoom.isDealFullyCompleted == false) {
            completeDealButton.visibility = View.VISIBLE
            completeDealButton.text = if (myCompletionStatus) "완료 동의함" else "거래 완료"
            completeDealButton.isEnabled = !myCompletionStatus
        } else {
            completeDealButton.visibility = View.GONE
        }
    }

    private fun completeRecruitment(postId: String?) {
        if (postId == null || chatRoomId == null) return
        val postRef = firestore.collection("posts").document(postId)
        val chatRoomRef = firestore.collection("chatRooms").document(chatRoomId!!)
        firestore.runBatch { batch ->
            batch.update(postRef, "status", "모집완료")
            batch.update(chatRoomRef, "status", "모집완료")
        }.addOnSuccessListener {
            Toast.makeText(this, "모집이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    private fun onCompleteDealClicked() {
        val myUid = auth.currentUser?.uid ?: return
        val roomRef = firestore.collection("chatRooms").document(chatRoomId!!)

        roomRef.update("completionStatus.$myUid", true).addOnSuccessListener {
            roomRef.get().addOnSuccessListener { document ->
                val completionStatus = document.get("completionStatus") as? Map<String, Boolean>
                if (completionStatus != null && completionStatus.values.all { it }) {
                    roomRef.update("isDealFullyCompleted", true)
                        .addOnSuccessListener { Toast.makeText(this, "모든 참여자가 거래를 완료했습니다!", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, "거래 완료에 동의했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "처리 중 오류 발생", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "거래 완료 동의 실패", e)
        }
    }

    private fun setupParticipantsList(chatRoom: ChatRoom) {
        val participantUids = chatRoom.participants ?: return
        val userFetchTasks = participantUids.map { firestore.collection("users").document(it).get() }
        Tasks.whenAllSuccess<DocumentSnapshot>(userFetchTasks).addOnSuccessListener { documents ->
            val participants = documents.mapNotNull {
                Participant(it.id, it.getString("nickname") ?: "익명", it.id == chatRoom.creatorUid)
            }
            participantsRecyclerView.layoutManager = LinearLayoutManager(this)
            participantsRecyclerView.adapter = ParticipantAdapter(participants.sortedByDescending { it.isCreator }) { participant ->
                // 프로필 보기 로직
                startActivity(Intent(this, UserProfileActivity::class.java).putExtra("USER_ID", participant.uid))
            }
        }
    }
}