package com.bergi.nbang_v1

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint // GeoPoint import 추가
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Post(
    @get:Exclude
    var id: String = "",

    var title: String = "",
    var content: String = "",
    var category: String = "",
    var creatorName: String = "",
    var photoUrls: List<String> = emptyList(),
    var totalPeople: Int = 0,
    var currentPeople: Int = 1,
    var status: String = "모집중",
    var creatorUid: String = "",
    var participants: List<String> = emptyList(),

    // --- 이 부분을 추가/수정하여 오류를 해결합니다 ---
    var meetingPlaceName: String = "",
    var meetingLocation: GeoPoint? = null,
    var geohash: String? = null,

    @ServerTimestamp
    var timestamp: Timestamp? = null
)
