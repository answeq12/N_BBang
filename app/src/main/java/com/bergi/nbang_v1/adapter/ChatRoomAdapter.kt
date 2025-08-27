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
import com.bergi.nbang_v1.data.ChatRoom
import com.bergi.nbang_v1.data.ChatRoomItem

class ChatRoomAdapter : ListAdapter<ChatRoomItem, ChatRoomAdapter.ChatRoomViewHolder>(DiffCallback) {

    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_chatroom_title)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.tv_last_message)

        fun bind(chatRoomItem: ChatRoomItem) {
            titleTextView.text = chatRoomItem.chatRoom.postTitle
            lastMessageTextView.text = chatRoomItem.chatRoom.lastMessage
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_chatroom, parent, false)
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val currentChatRoomItem = getItem(position)
        if (holder is ChatRoomViewHolder) {
            holder.bind(currentChatRoomItem)

            holder.itemView.setOnClickListener {
                val context = holder.itemView.context

                val intent = Intent(context, ChatRoomActivity::class.java).apply {
                    putExtra("chatRoomId", currentChatRoomItem.id)
                    putExtra("chatRoomTitle", currentChatRoomItem.chatRoom.postTitle)
                }
                context.startActivity(intent)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ChatRoomItem>() {
            override fun areItemsTheSame(oldItem: ChatRoomItem, newItem: ChatRoomItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatRoomItem, newItem: ChatRoomItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}