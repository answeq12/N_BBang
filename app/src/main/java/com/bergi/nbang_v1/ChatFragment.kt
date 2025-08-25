package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ChatRoomAdapter
import com.bergi.nbang_v1.data.ChatRoom
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView_chat_list)
        setupRecyclerView()
        loadChatRooms()
    }

    private fun setupRecyclerView() {
        adapter = ChatRoomAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadChatRooms() {
        val currentUser = auth.currentUser
        if (currentUser == null) { return }
        val myUid = currentUser.uid

        firestore.collection("chatRooms")
            .whereArrayContains("participants", myUid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                // if문에 else를 추가해서 return을 사용하지 않도록 구조를 변경합니다.
                if (error != null) {
                    // 오류가 있으면 로그만 남기고 아무것도 하지 않음
                    Log.w("ChatFragment", "Listen failed.", error)
                } else if (snapshots != null) {
                    // 오류가 없고, snapshots 데이터가 있을 때만 아래 코드를 실행
                    val chatRooms = snapshots.toObjects(ChatRoom::class.java)
                    adapter.submitList(chatRooms)
                }
            }
    }
}