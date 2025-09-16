package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp

data class Message(
    val senderUid: String? = null,
    // val senderName: String? = null, // <<< 이 필드를 삭제하거나 주석 처리합니다.
    val message: String? = null,
    val timestamp: Timestamp? = null
) {
    // Firestore를 위한 빈 생성자
    constructor() : this(null, null, null)
}