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
import androidx.appcompat.app.AlertDialog
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
        listenToChatRoomChanges() // [수정] 채팅방 정보 로드를 실시간 리스너로 변경

        sendButton.setOnClickListener { sendMessage() }
        completeDealButton.setOnClickListener { onCompleteDealClicked() } // [수정] 새 함수로 연결
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        messageEditText = findViewById(R.id.editText_message)
        sendButton = findViewById(R.id.button_send)
        messagesRecyclerView = findViewById(R.id.recyclerView_messages)
        completeDealButton = findViewById(R.id.button_complete_deal)
        drawerLayout = findViewById(R.id.drawer_layout)
        participantsRecyclerView = findViewById(R.id.recyclerView_participants)
        completeRecruitmentButtonInDrawer = findViewById(R.id.button_complete_recruitment_drawer)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chatroom, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_drawer -> { drawerLayout.openDrawer(GravityCompat.END); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // [대체] 채팅방 정보 변화를 실시간으로 감지하고 UI를 업데이트하는 함수
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

    // [대체] 새로운 데이터 구조에 맞춰 버튼 상태를 종합적으로 업데이트하는 함수
    private fun updateUI(chatRoom: ChatRoom) {
        val myUid = auth.currentUser?.uid ?: return
        toolbar.title = chatRoom.postTitle ?: "채팅방"
        toolbar.subtitle = "참여자: ${chatRoom.participants?.size ?: 0}명"

        val isCreator = myUid == chatRoom.creatorUid
        // 드로어의 '모집 완료' 버튼 상태
        if (isCreator && chatRoom.status != "모집완료") {
            completeRecruitmentButtonInDrawer.visibility = View.VISIBLE
            completeRecruitmentButtonInDrawer.setOnClickListener { completeRecruitment(chatRoom.postId) }
        } else {
            completeRecruitmentButtonInDrawer.visibility = View.GONE
        }

        // 메인의 '거래 완료' 버튼 상태 (방장 포함 모든 참여자에게 보임)
        val myCompletionStatus = chatRoom.completionStatus?.get(myUid) ?: false
        if (chatRoom.status == "모집완료" && chatRoom.isDealFullyCompleted == false) {
            completeDealButton.visibility = View.VISIBLE
            completeDealButton.text = if (myCompletionStatus) "완료 동의함" else "거래 완료"
            completeDealButton.isEnabled = !myCompletionStatus
        } else {
            completeDealButton.visibility = View.GONE
        }
    }

    // [신규] '모집 완료' 로직
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
        }.addOnFailureListener { e ->
            Log.e(TAG, "모집 완료 처리 실패", e)
            Toast.makeText(this, "모집 완료 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCompleteDealClicked() {
        val myUid = auth.currentUser?.uid ?: return
        val roomRef = firestore.collection("chatRooms").document(chatRoomId!!)
        val postRef = firestore.collection("posts").document(chatRoomId!!) // [추가] 게시글 참조

        roomRef.update("completionStatus.$myUid", true).addOnSuccessListener {
            roomRef.get().addOnSuccessListener { document ->
                val chatRoom = document.toObject(ChatRoom::class.java)
                val completionStatus = chatRoom?.completionStatus
                val allParticipants = chatRoom?.participants

                val allCompleted = allParticipants?.all { uid -> completionStatus?.get(uid) == true } ?: false

                if (allCompleted) {
                    roomRef.update("isDealFullyCompleted", true)
                    postRef.update("status", "거래완료")
                        .addOnSuccessListener {
                            Toast.makeText(this, "모든 참여자가 거래를 완료했습니다!", Toast.LENGTH_SHORT).show()
                        }
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
                showParticipantOptions(participant)
            }
        }
    }

    private fun showParticipantOptions(participant: Participant) {
        val options = mutableListOf("프로필 보기")
        if (participant.uid != auth.currentUser?.uid) {
            options.add("신고하기")
        }

        AlertDialog.Builder(this)
            .setTitle(participant.nickname)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "프로필 보기" -> {
                        startActivity(Intent(this, UserProfileActivity::class.java).putExtra("USER_ID", participant.uid))
                    }
                    "신고하기" -> {
                        Toast.makeText(this, "신고하기 기능은 구현 예정입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    // --- (아래 함수들은 기존 기능 유지를 위해 그대로 둡니다) ---
    private fun setupMessagesRecyclerView() {
        messageAdapter = MessageAdapter()
        messagesRecyclerView.adapter = messageAdapter
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        messagesRecyclerView.layoutManager = layoutManager
    }

    private fun loadMessages() {
        chatRoomId?.let { id ->
            firestore.collection("chatRooms").document(id)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (snapshots != null) {
                        val messages = snapshots.toObjects(Message::class.java)
                        messageAdapter.submitList(messages)
                        if(messages.isNotEmpty()) messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        val currentUser = auth.currentUser
        if (messageText.isEmpty() || currentUser == null || chatRoomId == null) return

        val message = Message(
            senderUid = currentUser.uid,
            message = messageText,
            timestamp = Timestamp.now()
        )

        firestore.collection("chatRooms").document(chatRoomId!!)
            .collection("messages").add(message)
            .addOnSuccessListener {
                messageEditText.text.clear()
                updateLastMessage(messageText)
            }
    }

    private fun updateLastMessage(message: String) {
        chatRoomId?.let {
            firestore.collection("chatRooms").document(it)
                .update("lastMessage", message, "lastMessageTimestamp", Timestamp.now())
        }
    }
}