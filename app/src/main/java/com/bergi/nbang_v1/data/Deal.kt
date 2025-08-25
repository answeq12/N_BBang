package com.bergi.nbang_v1.data // 본인의 패키지 이름으로 변경

import java.util.Date

data class Deal(
    val id: String = "",
    val hostUid: String = "",
    val title: String = "",
    val content: String = "",
    val maxParticipants: Int = 0,
    val location: String = "",
    val participants: List<String> = listOf(),
    val createdAt: Date = Date()
)