package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp

data class ChatRoom(
    val postId: String? = null,
    val postTitle: String? = null,
    val participants: List<String>? = null,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Timestamp? = null
)