package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue // Firebase ServerTimestamp 사용 시 필요

data class ChatRoom(
    val postId: String? = null,
    val postTitle: String? = null,
    val participants: List<String>? = null,
    var lastMessage: String? = null, // var로 변경하여 업데이트 가능하게 (선택 사항)
    var lastMessageTimestamp: Timestamp? = null, // var로 변경 (선택 사항)
    var unreadCount: Int = 0 // 읽지 않은 메시지 수를 위한 필드 추가, 기본값 0
)