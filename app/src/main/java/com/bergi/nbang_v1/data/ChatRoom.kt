package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ChatRoom(
    var postId: String? = null,
    var postTitle: String? = null,
    var creatorUid: String? = null,
    var participants: List<String>? = null,
    var lastMessage: String? = null,
    @ServerTimestamp
    var lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp
    var createdAt: Timestamp? = null,
    var unreadCount: Int = 0,

    // ✅ 추가된 필드
    var status: String? = "모집중", // 모집 상태 ("모집중", "모집완료")
    var isCompleted: Boolean = false,
    var completingUsers: Map<String, Boolean>? = null
)
