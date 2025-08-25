package com.bergi.nbang_v1.data // 본인의 패키지 이름으로 변경

import java.util.Date

data class ChatRoom(
    val dealId: String = "",
    val dealTitle: String = "",
    val participants: List<String> = listOf(),
    val lastMessage: String = "채팅방이 생성되었습니다.",
    val lastMessageTimestamp: Date = Date()
)