// com.bergi.nbang_v1.data.Message.kt
package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp

data class Message(
    val senderUid: String? = null,
    val senderName: String? = null, // <-- 이 필드를 추가하세요
    val message: String? = null,
    val timestamp: Timestamp? = null
)