package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp

data class Review(
    val reviewerUid: String? = null,
    val reviewedUserUid: String? = null,
    val rating: Float = 0.0f,
    val comment: String? = null,
    val createdAt: Timestamp? = null,    // Renamed from 'timestamp'
    val postId: String? = null,          // Added
    val reviewerNickname: String? = null // Added
)