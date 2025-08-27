package com.bergi.nbang_v1.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.ChatRoomActivity
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.ChatRoomItem // ChatRoomItem을 사용하고 있다고 가정

// ChatRoom data class는 com.bergi.nbang_v1.data.ChatRoom에 정의되어 있고,
// unreadCount: Int = 0 필드가 포함되어 있다고 가정합니다.

class ChatRoomAdapter : ListAdapter<ChatRoomItem, ChatRoomAdapter.ChatRoomViewHolder>(DiffCallback) {

    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // XML 레이아웃 (list_item_chatroom.xml)에 정의된 ID와 일치해야 합니다.
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_chatroom_title)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.tv_last_message)
        private val unreadCountTextView: TextView = itemView.findViewById(R.id.tv_unread_count)

        fun bind(chatRoomItem: ChatRoomItem) {
            val chatRoom = chatRoomItem.chatRoom // ChatRoomItem으로부터 ChatRoom 객체를 가져옵니다.

            // 1. 채팅방 제목 설정
            titleTextView.text = chatRoom.postTitle

            // 2. 최근 메시지 설정
            if (chatRoom.lastMessage.isNullOrEmpty()) {
                lastMessageTextView.text = "채팅방에 들어왔습니다." // 기본 메시지
            } else {
                lastMessageTextView.text = chatRoom.lastMessage
            }

            // 3. 읽지 않은 메시지 수 설정
            // ChatRoom.kt에서 unreadCount는 Int 타입이고 기본값이 0이므로 null이 될 수 없습니다.
            if (chatRoom.unreadCount > 0) {
                unreadCountTextView.text = chatRoom.unreadCount.toString()
                unreadCountTextView.visibility = View.VISIBLE
            } else {
                unreadCountTextView.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_chatroom, parent, false) // 수정된 list_item_chatroom.xml 사용
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val currentChatRoomItem = getItem(position)
        holder.bind(currentChatRoomItem) // ViewHolder의 bind 함수 호출

        // 아이템 클릭 시 ChatRoomActivity로 이동
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ChatRoomActivity::class.java).apply {
                putExtra("chatRoomId", currentChatRoomItem.id) // ChatRoomItem의 id (Firestore 문서 ID)
                putExtra("chatRoomTitle", currentChatRoomItem.chatRoom.postTitle)
                // 필요하다면, 채팅방 입장 시 unreadCount를 0으로 업데이트하는 로직을
                // ChatRoomActivity 또는 여기서 Firestore에 직접 요청할 수 있습니다.
            }
            context.startActivity(intent)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ChatRoomItem>() {
            override fun areItemsTheSame(oldItem: ChatRoomItem, newItem: ChatRoomItem): Boolean {
                // 각 아이템의 고유 ID를 비교합니다.
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatRoomItem, newItem: ChatRoomItem): Boolean {
                // 아이템의 내용이 같은지 비교합니다.
                // ChatRoomItem과 내부의 ChatRoom이 data class이므로,
                // unreadCount나 lastMessage 등이 변경되면 false를 반환하여 UI가 업데이트됩니다.
                return oldItem == newItem
            }
        }
    }
}
