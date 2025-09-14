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
    var isCompleted: Boolean = false, // 거래 완료 여부
    var completingUsers: Map<String, Boolean>? = null // 거래 완료 동의한 사용자 목록
)