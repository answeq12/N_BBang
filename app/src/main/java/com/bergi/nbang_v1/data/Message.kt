package com.bergi.nbang_v1.data

import java.util.Date

data class Message(
    val senderUid: String = "",
    val message: String = "",
    val timestamp: Date = Date()
)