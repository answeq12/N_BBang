package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp

data class ChatRoom(
    val postId: String? = null,
    val creatorUid: String? = null,
    val participants: List<String>? = null,
    val postTitle: String? = null,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val usersWhoLiked: List<String>? = null,
    val status: String? = "모집중",
    val unreadCount: Int = 0,

    // ▼▼▼ 이 필드들이 누락되어 있었습니다 ▼▼▼
    val completionStatus: Map<String, Boolean>? = null,
    val isDealFullyCompleted: Boolean? = false
) {
    // Firestore의 toObject() 메서드를 위한 빈 생성자
    constructor() : this(
        postId = null,
        creatorUid = null,
        participants = null,
        postTitle = null,
        lastMessage = null,
        lastMessageTimestamp = null,
        createdAt = null,
        usersWhoLiked = null,
        status = "모집중",
        unreadCount = 0,
        completionStatus = null,
        isDealFullyCompleted = false
    )
}