package com.bergi.nbang_v1.data

import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class Message(
    val senderUid: String? = null,
    val message: String? = null,
    @ServerTimestamp
    val timestamp: Timestamp? = null
)