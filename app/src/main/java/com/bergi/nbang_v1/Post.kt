package com.bergi.nbang_v1.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
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
    var meetingPlaceName: String = "",
    var meetingLocation: GeoPoint? = null,
    var geohash: String? = null,
    @ServerTimestamp
    var timestamp: Timestamp? = null
)