package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import com.google.firebase.firestore.FieldValue
import java.util.Arrays

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
    private var currentChatRoom: ChatRoom? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        initViews()

        chatRoomId = intent.getStringExtra("chatRoomId")
        val chatRoomTitle = intent.getStringExtra("chatRoomTitle")

        if (chatRoomId != null) {
            setupToolbar(chatRoomTitle)
            setupMessagesRecyclerView()
            loadMessages()
            loadChatRoomInfo()

            sendButton.setOnClickListener { sendMessage() }
            completeDealButton.setOnClickListener { completeDeal() }
        } else {
            Toast.makeText(this, "채팅방 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
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

    private fun setupToolbar(title: String?) {
        toolbar.title = title ?: "채팅방"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chatroom, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_drawer -> {
                drawerLayout.openDrawer(GravityCompat.END)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
                    if (error != null) {
                        Log.w("ChatRoomActivity", "메시지 불러오기 실패", error)
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val messages = snapshots.toObjects(Message::class.java)
                        messageAdapter.submitList(messages)
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        val currentUser = auth.currentUser
        if (messageText.isEmpty() || currentUser == null || chatRoomId == null) return

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val senderName = document.getString("nickname") ?: "익명"
                val message = Message(
                    senderUid = currentUser.uid,
                    senderName = senderName,
                    message = messageText,
                    timestamp = Timestamp.now()
                )

                firestore.collection("chatRooms").document(chatRoomId!!)
                    .collection("messages").add(message)
                    .addOnSuccessListener {
                        messageEditText.text.clear()
                        updateLastMessage(messageText)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun updateLastMessage(message: String) {
        chatRoomId?.let {
            firestore.collection("chatRooms").document(it)
                .update("lastMessage", message, "lastMessageTimestamp", Timestamp.now())
        }
    }

    // ✅ 수정: 채팅방 정보와 게시글 정보를 동시에 불러오도록 수정
    private fun loadChatRoomInfo() {
        chatRoomId?.let { id ->
            val chatRoomRef = firestore.collection("chatRooms").document(id)
            chatRoomRef.addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Log.w("ChatRoomActivity", "채팅방 정보 불러오기 실패", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    currentChatRoom = documentSnapshot.toObject(ChatRoom::class.java)
                    currentChatRoom?.let { chatRoom ->
                        toolbar.subtitle = "참여자: ${chatRoom.participants?.size ?: 0}명"
                        setupParticipantsList(chatRoom)
                        updateDrawerButtons(chatRoom)

                        // ✅ 추가: postId를 사용하여 게시글 정보 불러오기
                        val postId = chatRoom.postId
                        if (postId != null) {
                            firestore.collection("posts").document(postId).get()
                                .addOnSuccessListener { postSnapshot ->
                                    val postStatus = postSnapshot.getString("status")
                                    updateButtonsVisibility(chatRoom, postStatus)
                                }
                                .addOnFailureListener { postError ->
                                    Log.e("ChatRoomActivity", "게시글 정보 불러오기 실패", postError)
                                }
                        } else {
                            // postId가 없는 경우 (예외 처리)
                            updateButtonsVisibility(chatRoom, null)
                        }
                    }
                }
            }
        }
    }

    private fun setupParticipantsList(chatRoom: ChatRoom) {
        val participantUids = chatRoom.participants ?: return
        val creatorUid = chatRoom.creatorUid

        val userFetchTasks = participantUids.map { firestore.collection("users").document(it).get() }

        Tasks.whenAllSuccess<DocumentSnapshot>(userFetchTasks).addOnSuccessListener { documents ->
            val participants = documents.mapNotNull {
                val uid = it.id
                val nickname = it.getString("nickname") ?: "알 수 없는 사용자"
                Participant(uid, nickname, uid == creatorUid)
            }

            val sortedParticipants = participants.sortedWith(
                compareByDescending<Participant> { it.isCreator }.thenBy { it.nickname }
            )

            val adapter = ParticipantAdapter(sortedParticipants) { participant ->
                showParticipantOptions(participant)
            }
            participantsRecyclerView.layoutManager = LinearLayoutManager(this)
            participantsRecyclerView.adapter = adapter
        }.addOnFailureListener {
            Log.e("ChatRoomActivity", "Failed to fetch all participant details", it)
        }
    }

    private fun showParticipantOptions(participant: Participant) {
        val options = mutableListOf("프로필 보기")

        if (participant.uid != auth.currentUser?.uid) {
            if (currentChatRoom?.isCompleted == true) {
                options.add("좋아요")
                options.add("후기 남기기")
            }
            options.add("신고하기")
        }

        AlertDialog.Builder(this)
            .setTitle(participant.nickname)
            .setItems(options.toTypedArray()) { _, which ->
                when (val selection = options[which]) {
                    "프로필 보기" -> {
                        val intent = Intent(this, UserProfileActivity::class.java)
                        intent.putExtra("USER_ID", participant.uid)
                        startActivity(intent)
                    }
                    "좋아요" -> {
                        likeParticipant(participant)
                    }
                    "후기 남기기" -> {
                        Toast.makeText(this, "후기 남기기 기능은 구현 예정입니다.", Toast.LENGTH_SHORT).show()
                    }
                    "신고하기" -> {
                        Toast.makeText(this, "신고하기 기능은 구현 예정입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun likeParticipant(targetUser: Participant) {
        val likerId = auth.currentUser?.uid
        if (likerId == null || chatRoomId == null) return

        val chatRoomRef = firestore.collection("chatRooms").document(chatRoomId!!)
        val targetUserRef = firestore.collection("users").document(targetUser.uid)

        firestore.runTransaction { transaction ->
            val chatRoomSnapshot = transaction.get(chatRoomRef)
            val chatRoom = chatRoomSnapshot.toObject(ChatRoom::class.java)

            val usersWhoLiked = chatRoom?.usersWhoLiked ?: emptyList()
            if (usersWhoLiked.contains(likerId)) {
                throw Exception("이미 이 채팅방에서 좋아요를 눌렀습니다.")
            }

            transaction.update(targetUserRef, "mannerScore", FieldValue.increment(0.5))
            transaction.update(chatRoomRef, "usersWhoLiked", FieldValue.arrayUnion(likerId))

            null
        }.addOnSuccessListener {
            Toast.makeText(this, "${targetUser.nickname}님에게 좋아요를 보냈습니다!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, e.message ?: "좋아요 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
            Log.w("ChatRoomActivity", "Like transaction failed.", e)
        }
    }

    // ✅ 추가: '모집 완료' 상태에 따라 버튼을 보이거나 숨기는 함수
    private fun updateButtonsVisibility(chatRoom: ChatRoom, postStatus: String?) {
        if (postStatus == "모집완료") {
            completeDealButton.visibility = View.VISIBLE
            // '거래 완료' 버튼 로직 (기존 함수 내용)
            val currentUserId = auth.currentUser?.uid
            val completingUsers = chatRoom.completingUsers ?: emptyMap()
            if (currentUserId != null && completingUsers.containsKey(currentUserId)) {
                completeDealButton.text = "거래 완료 동의함"
                completeDealButton.isEnabled = false
            } else {
                completeDealButton.text = "거래 완료"
                completeDealButton.isEnabled = true
            }

            // 거래가 이미 완료된 경우 버튼 숨기기
            if (chatRoom.isCompleted) {
                completeDealButton.visibility = View.GONE
            }

        } else {
            // '모집 완료' 상태가 아니면 '거래 완료' 버튼 숨기기
            completeDealButton.visibility = View.GONE
        }
    }


    private fun completeDeal() {
        val currentUserId = auth.currentUser?.uid ?: return
        chatRoomId?.let { id ->
            val chatRoomRef = firestore.collection("chatRooms").document(id)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(chatRoomRef)
                val chatRoom = snapshot.toObject(ChatRoom::class.java)
                if (chatRoom?.participants != null) {
                    val participants = chatRoom.participants!!
                    val completingUsers = chatRoom.completingUsers?.toMutableMap() ?: mutableMapOf()

                    if (!completingUsers.containsKey(currentUserId)) {
                        completingUsers[currentUserId] = true
                        if (completingUsers.size == participants.size) {
                            transaction.update(chatRoomRef, "isCompleted", true)
                        }
                        transaction.update(chatRoomRef, "completingUsers", completingUsers)
                    }
                }
            }.addOnSuccessListener {
                Toast.makeText(this, "거래 완료에 동의하셨습니다.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Log.e("ChatRoomActivity", "거래 완료 트랜잭션 실패", e)
            }
        }
    }

    // ✅ 추가: 드로어 버튼 상태 업데이트 함수
    private fun updateDrawerButtons(chatRoom: ChatRoom) {
        if (auth.currentUser?.uid == chatRoom.creatorUid) {
            completeRecruitmentButtonInDrawer.visibility = View.VISIBLE
            completeRecruitmentButtonInDrawer.setOnClickListener {
                firestore.collection("posts").document(chatRoom.postId!!).update("status", "모집완료")
                    .addOnSuccessListener {
                        Toast.makeText(this, "게시글이 모집 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        drawerLayout.closeDrawer(GravityCompat.END)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "모집 완료 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            completeRecruitmentButtonInDrawer.visibility = View.GONE
        }
    }
}