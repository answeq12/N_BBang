package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.adapter.ChatRoomAdapter
import com.bergi.nbang_v1.data.ChatRoom // ChatRoom 데이터 클래스 경로 확인
import com.bergi.nbang_v1.data.ChatRoomItem // ChatRoomItem 데이터 클래스 경로 확인
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException // FirebaseFirestoreException 임포트
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomAdapter
    private lateinit var textViewNoChatRooms: TextView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth
    private var chatRoomsListener: ListenerRegistration? = null

    private val TAG = "ChatFragment" // 로그 태그 추가

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        recyclerView = view.findViewById(R.id.recyclerView_chat_list)
        textViewNoChatRooms = view.findViewById(R.id.textView_no_chat_rooms)

        setupRecyclerView()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Attaching chat rooms listener.")
        loadChatRooms() // 화면이 사용자에게 보이기 시작할 때 데이터 로드 및 리스너 등록
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Removing chat rooms listener.")
        chatRoomsListener?.remove() // 화면이 더 이상 보이지 않을 때 리스너 제거
        chatRoomsListener = null // 리스너 참조도 null로 설정하는 것이 좋음
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView called")
        adapter = ChatRoomAdapter() // ChatRoomAdapter가 올바른 생성자를 가지고 있는지 확인
        recyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, layoutManager.orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    private fun loadChatRooms() {
        // 기존 리스너가 있다면 제거 (onStart에서 중복 호출 방지 및 리소스 정리)
        chatRoomsListener?.remove()
        Log.d(TAG, "loadChatRooms: Previous listener removed if exists.")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "loadChatRooms: User is not logged in. Clearing UI.") // 경고 레벨로 변경
            updateUI(emptyList())
            return
        }
        val myUid = currentUser.uid
        Log.d(TAG, "loadChatRooms: Current user UID: $myUid")

        // Firestore 쿼리
        chatRoomsListener = firestore.collection("chatRooms") // "chatRooms"가 실제 컬렉션 이름인지 확인
            .whereArrayContains("participants", myUid) // "participants"가 실제 필드 이름인지 확인
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING) // "lastMessageTimestamp" 필드 존재 및 타입 확인
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed for chat rooms query.", error) // 오류는 Error 레벨로 로깅
                    // Firestore 오류의 상세 내용을 확인 (error.message, error.code 등)
                    if (error is FirebaseFirestoreException) { // 타입 캐스팅으로 더 많은 정보 접근 가능
                        Log.e(TAG, "Firestore Exception Code: ${error.code}")
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.e(TAG, "PERMISSION_DENIED: Check Firestore security rules for chatRooms collection reading.")
                        } else if (error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION && error.message?.contains("index") == true) {
                            Log.e(TAG, "INDEX_REQUIRED: Firestore query requires an index. Check Logcat for a link to create it.")
                        }
                    }
                    updateUI(emptyList()) // 오류 발생 시 빈 화면으로 처리
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d(TAG, "Snapshot listener: Received ${snapshots.size()} documents. Metadata has pending writes: ${snapshots.metadata.hasPendingWrites()}")
                    if (snapshots.isEmpty) {
                        Log.d(TAG, "Snapshot listener: Query returned no documents for UID: $myUid. Check if user is in any chat room's 'participants' list or if 'lastMessageTimestamp' field is missing/inconsistent for ordering.")
                    }

                    val chatRoomItems = mutableListOf<ChatRoomItem>()
                    for (document in snapshots.documents) {
                        // 각 문서의 ID와 전체 데이터 로깅
                        Log.d(TAG, "Document ID: ${document.id}, Data: ${document.data}")
                        try {
                            val chatRoom = document.toObject(ChatRoom::class.java)
                            if (chatRoom != null) {
                                // ChatRoom 객체 변환 후 주요 필드 값 로깅
                                // ChatRoom 클래스에 해당 필드들이 실제로 존재하는지 확인 필요
                                Log.d(TAG, "Converted ChatRoom (ID: ${document.id}): " +
                                        "PostTitle='${chatRoom.postTitle}', " +
                                        "LastMessage='${chatRoom.lastMessage}', " +
                                        "Participants=${chatRoom.participants?.joinToString()}, " + // participants 리스트 내용 로깅
                                        "LastMessageTimestamp=${chatRoom.lastMessageTimestamp}")

                                // ChatRoomItem 생성 및 리스트 추가
                                // ChatRoomItem의 생성자 및 ChatRoom 데이터 클래스 필드 확인
                                chatRoomItems.add(ChatRoomItem(document.id, chatRoom))
                            } else {
                                Log.w(TAG, "Failed to convert document ${document.id} to ChatRoom object (toObject returned null). Check ChatRoom.kt data class fields and Firestore document structure match.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${document.id} to ChatRoom: ${e.message}", e)
                        }
                    }
                    updateUI(chatRoomItems)
                    Log.d(TAG, "Snapshot listener: Submitted ${chatRoomItems.size} items to adapter.")
                } else {
                    // 이 경우는 거의 발생하지 않지만, snapshots이 null이고 error도 null인 극단적인 케이스
                    Log.d(TAG, "Snapshot listener: Snapshots are null, but no error. This case is unusual.")
                    updateUI(emptyList())
                }
            }
        Log.d(TAG, "loadChatRooms: Listener attached.")
    }

    private fun updateUI(chatRoomItems: List<ChatRoomItem>) {
        Log.d(TAG, "updateUI called with ${chatRoomItems.size} items.")
        adapter.submitList(chatRoomItems.toList()) // ListAdapter 사용 시 toList()로 불변 리스트 전달 권장
        if (chatRoomItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            textViewNoChatRooms.visibility = View.VISIBLE
            Log.d(TAG, "updateUI: No chat rooms. Showing 'no chat rooms' message.")
        } else {
            recyclerView.visibility = View.VISIBLE
            textViewNoChatRooms.visibility = View.GONE
            Log.d(TAG, "updateUI: Chat rooms available (${chatRoomItems.size}). Showing RecyclerView.")
        }
    }
}
