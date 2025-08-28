package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ChatRoom(
    var postId: String? = null,
    var postTitle: String? = null,
    var creatorUid: String? = null, // 게시글 작성자 UID 필드 추가
    var participants: List<String>? = null,
    var lastMessage: String? = null,
    @ServerTimestamp
    var lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp
    var createdAt: Timestamp? = null, // 채팅방 생성 시간 필드 추가
    var unreadCount: Int = 0
)