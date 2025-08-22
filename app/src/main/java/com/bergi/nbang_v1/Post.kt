package com.bergi.nbang_v1

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// @IgnoreExtraProperties: Firestore 문서에 있지만 우리 코드에는 없는 필드가 있어도 에러 없이 무시합니다.
@IgnoreExtraProperties
data class Post(
    // @get:Exclude: 이 필드는 Firestore에 저장하거나 읽어오지 않습니다.
    // 문서 ID는 코드 내에서 별도로 관리하는 것이 더 안정적입니다.
    @get:Exclude
    var id: String = "",

    var title: String = "",
    var content: String = "", // '내용' 필드가 포함되어 있는지 확인하세요.
    var category: String = "",
    var totalPeople: Int = 0,
    var currentPeople: Int = 1,
    var meetingPlace: String = "",
    var status: String = "모집중",
    var creatorUid: String = "",
    var participants: List<String> = emptyList(),

    // @ServerTimestamp: Firestore에 저장될 때 서버 시간을 자동으로 기록합니다.
    @ServerTimestamp
    var timestamp: Timestamp? = null
)