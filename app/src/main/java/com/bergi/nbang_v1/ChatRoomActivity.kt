package com.bergi.nbang_v1

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.MessageAdapter
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

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    private var chatRoomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        toolbar = findViewById(R.id.toolbar)
        messageEditText = findViewById(R.id.editText_message)
        sendButton = findViewById(R.id.button_send)
        messagesRecyclerView = findViewById(R.id.recyclerView_messages)

        chatRoomId = intent.getStringExtra("chatRoomId")
        val chatRoomTitle = intent.getStringExtra("chatRoomTitle")

        setupToolbar(chatRoomTitle)

        setupMessagesRecyclerView()
        loadMessages()

        sendButton.setOnClickListener {
            sendMessage()
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

        val message = Message(
            senderUid = currentUser.uid,
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

    private fun updateLastMessage(message: String) {
        chatRoomId?.let {
            firestore.collection("chatRooms").document(it)
                .update(mapOf(
                    "lastMessage" to message,
                    "lastMessageTimestamp" to Timestamp.now()
                ))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}