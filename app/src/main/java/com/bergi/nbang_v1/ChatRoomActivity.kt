package com.bergi.nbang_v1

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.MessageAdapter
import com.bergi.nbang_v1.data.ChatRoom
import com.bergi.nbang_v1.data.Message
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
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

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    private var chatRoomId: String? = null
    private var isDealCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        // UI 요소 초기화
        toolbar = findViewById(R.id.toolbar)
        messageEditText = findViewById(R.id.editText_message)
        sendButton = findViewById(R.id.button_send)
        messagesRecyclerView = findViewById(R.id.recyclerView_messages)
        completeDealButton = findViewById(R.id.button_complete_deal)

        // Intent에서 chatRoomId와 chatRoomTitle 가져오기
        chatRoomId = intent.getStringExtra("chatRoomId")
        val chatRoomTitle = intent.getStringExtra("chatRoomTitle")

        // chatRoomId가 null이 아닐 때만 나머지 로직 실행
        if (chatRoomId != null) {
            setupToolbar(chatRoomTitle)
            setupMessagesRecyclerView()
            loadMessages()
            loadChatRoomInfo()

            sendButton.setOnClickListener {
                sendMessage()
            }

            completeDealButton.setOnClickListener {
                completeDeal()
            }
        } else {
            // chatRoomId가 null이면 액티비티 종료
            Toast.makeText(this, "채팅방 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar(title: String?) {
        toolbar.title = title ?: "채팅방"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        chatRoomId?.let { id ->
            firestore.collection("chatRooms").document(id).get()
                .addOnSuccessListener { document ->
                    val participants = document.get("participants") as? List<*>
                    toolbar.subtitle = "참여자: ${participants?.size ?: 0}명"
                }
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
        if (messageText.isEmpty() || currentUser == null || chatRoomId == null) {
            return
        }

        // Firestore에서 현재 사용자의 닉네임 가져오기
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
                        Log.d("ChatRoomActivity", "메시지 전송 성공")
                        messageEditText.text.clear()
                        updateLastMessage(messageText)
                    }
                    .addOnFailureListener { e ->
                        Log.w("ChatRoomActivity", "메시지 전송 실패", e)
                        Toast.makeText(this, "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.w("ChatRoomActivity", "사용자 정보 조회 실패", e)
                Toast.makeText(this, "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastMessage(message: String) {
        chatRoomId?.let {
            firestore.collection("chatRooms").document(it)
                .update(mapOf(
                    "lastMessage" to message,
                    "lastMessageTimestamp" to Timestamp.now()
                ))
        }
    }

    private fun loadChatRoomInfo() {
        chatRoomId?.let { id ->
            firestore.collection("chatRooms").document(id)
                .addSnapshotListener { documentSnapshot, e ->
                    if (e != null) {
                        Log.w("ChatRoomActivity", "채팅방 정보 불러오기 실패", e)
                        return@addSnapshotListener
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val chatRoom = documentSnapshot.toObject(ChatRoom::class.java)
                        if (chatRoom != null) {
                            isDealCompleted = chatRoom.isCompleted
                            updateCompleteDealButtonState(chatRoom)
                        }
                    }
                }
        }
    }

    private fun updateCompleteDealButtonState(chatRoom: ChatRoom) {
        if (chatRoom.isCompleted) {
            completeDealButton.visibility = View.GONE
            return
        }

        val currentUserId = auth.currentUser?.uid
        val completingUsers = chatRoom.completingUsers ?: emptyMap()

        if (currentUserId != null && completingUsers.containsKey(currentUserId)) {
            completeDealButton.text = "거래 완료 동의함"
            completeDealButton.isEnabled = false
        } else {
            completeDealButton.text = "거래 완료"
            completeDealButton.isEnabled = true
        }
    }

    private fun completeDeal() {
        val currentUserId = auth.currentUser?.uid ?: return
        chatRoomId?.let { id ->
            val chatRoomRef = firestore.collection("chatRooms").document(id)

            chatRoomRef.get().addOnSuccessListener { documentSnapshot ->
                val chatRoom = documentSnapshot.toObject(ChatRoom::class.java)
                if (chatRoom != null && chatRoom.participants != null) {
                    val participants = chatRoom.participants!!
                    val completingUsers = chatRoom.completingUsers?.toMutableMap() ?: mutableMapOf()

                    if (completingUsers.containsKey(currentUserId)) {
                        Toast.makeText(this, "이미 거래 완료에 동의하셨습니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    completingUsers[currentUserId] = true

                    if (completingUsers.size == participants.size) {
                        chatRoomRef.update(
                            "completingUsers", completingUsers,
                            "isCompleted", true
                        ).addOnSuccessListener {
                            Toast.makeText(this, "모든 참가자가 동의하여 거래가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            isDealCompleted = true
                            updateCompleteDealButtonState(chatRoom)
                        }.addOnFailureListener { e ->
                            Log.e("ChatRoomActivity", "거래 완료 상태 업데이트 실패", e)
                        }
                    } else {
                        chatRoomRef.update("completingUsers", completingUsers)
                            .addOnSuccessListener {
                                Toast.makeText(this, "거래 완료에 동의하셨습니다. 다른 참가자의 동의를 기다립니다.", Toast.LENGTH_SHORT).show()
                            }.addOnFailureListener { e ->
                                Log.e("ChatRoomActivity", "거래 완료 동의 상태 업데이트 실패", e)
                            }
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
