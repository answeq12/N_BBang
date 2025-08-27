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
import java.util.Date // Import java.util.Date
import java.util.Locale

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    private val currentUserUid = Firebase.auth.currentUser?.uid

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.textView_message)
        private val timestampTextView: TextView = itemView.findViewById(R.id.textView_timestamp)

        fun bind(message: Message) {
            messageTextView.text = message.message
            message.timestamp?.let { firebaseTimestamp -> // Variable name changed for clarity
                val javaUtilDate: Date = firebaseTimestamp.toDate() // Convert Firebase Timestamp to java.util.Date
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
            senderTextView.text = message.senderUid
            message.timestamp?.let { firebaseTimestamp -> // Variable name changed for clarity
                val javaUtilDate: Date = firebaseTimestamp.toDate() // Convert Firebase Timestamp to java.util.Date
                val format = SimpleDateFormat("a h:mm", Locale.KOREA)
                timestampTextView.text = format.format(javaUtilDate)
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