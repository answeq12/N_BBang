package com.bergi.nbang_v1.adapter
import android.content.Intent
import com.bergi.nbang_v1.ChatRoomActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.ChatRoom

class ChatRoomAdapter : ListAdapter<ChatRoom, ChatRoomAdapter.ChatRoomViewHolder>(DiffCallback) {

    // 목록의 각 칸에 해당하는 UI 요소들을 보관하는 클래스
    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_chatroom_title)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.tv_last_message)

        // ChatRoom 데이터를 UI에 바인딩(연결)하는 함수
        fun bind(chatRoom: ChatRoom) {
            titleTextView.text = chatRoom.dealTitle
            lastMessageTextView.text = chatRoom.lastMessage
        }
    }

    // 새로운 ViewHolder를 생성할 때 호출됨
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        // list_item_chatroom.xml 디자인을 객체로 만듦
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_chatroom, parent, false)
        return ChatRoomViewHolder(view)
    }

    // ViewHolder에 데이터를 바인딩할 때 호출됨
    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val currentChatRoom = getItem(position)
        holder.bind(currentChatRoom)
        // 각 아이템 뷰(채팅방 한 칸)에 클릭 리스너를 설정합니다.
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context

            // ChatRoomActivity로 이동하는 Intent(요청서)를 만듭니다.
            val intent = Intent(context, ChatRoomActivity::class.java).apply {
                // 클릭된 채팅방의 ID (dealId)를 Intent에 담아 전달합니다.
                // "chatRoomId" 라는 이름표를 붙여서 보냅니다.
                putExtra("chatRoomId", currentChatRoom.dealId)
                // 채팅방 이름도 함께 전달하면, 다음 화면 상단에 제목으로 표시하기 좋습니다.
                putExtra("chatRoomTitle", currentChatRoom.dealTitle)
            }

            // Intent를 실행해서 화면을 전환합니다.
            context.startActivity(intent)
        }
    }

    // 리스트의 변경 사항을 효율적으로 계산하기 위한 도구
    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ChatRoom>() {
            override fun areItemsTheSame(oldItem: ChatRoom, newItem: ChatRoom): Boolean {
                return oldItem.dealId == newItem.dealId // ID가 같으면 같은 아이템
            }

            override fun areContentsTheSame(oldItem: ChatRoom, newItem: ChatRoom): Boolean {
                return oldItem == newItem // 내용까지 모두 같으면 같은 콘텐츠
            }
        }
    }
}