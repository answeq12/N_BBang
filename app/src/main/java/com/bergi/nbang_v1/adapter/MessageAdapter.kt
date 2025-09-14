package com.bergi.nbang_v1.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Message
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.firestore.FirebaseFirestore

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    private val currentUserUid = Firebase.auth.currentUser?.uid
    private val firestore = FirebaseFirestore.getInstance() // Firestore 인스턴스 추가

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.textView_message)
        private val timestampTextView: TextView = itemView.findViewById(R.id.textView_timestamp)

        fun bind(message: Message) {
            messageTextView.text = message.message
            message.timestamp?.let { firebaseTimestamp ->
                val javaUtilDate: Date = firebaseTimestamp.toDate()
                val format = SimpleDateFormat("a h:mm", Locale.KOREA)
                timestampTextView.text = format.format(javaUtilDate)
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.textView_message)
        private val senderTextView: TextView = itemView.findViewById(R.id.textView_sender)
        private val timestampTextView: TextView = itemView.findViewById(R.id.textView_timestamp)

        fun bind(message: Message) {
            messageTextView.text = message.message
            message.timestamp?.let { firebaseTimestamp ->
                val javaUtilDate: Date = firebaseTimestamp.toDate()
                val format = SimpleDateFormat("a h:mm", Locale.KOREA)
                timestampTextView.text = format.format(javaUtilDate)
            }

            // 닉네임 가져오기 및 설정
            message.senderUid?.let { uid ->
                firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        val nickname = document.getString("nickname")
                        senderTextView.text = nickname ?: "알 수 없음"
                    }
                    .addOnFailureListener {
                        senderTextView.text = "오류"
                    }
            } ?: run {
                senderTextView.text = "알 수 없음"
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderUid == currentUserUid) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.senderUid == newItem.senderUid
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}