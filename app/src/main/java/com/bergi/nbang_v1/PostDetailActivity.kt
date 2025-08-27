package com.bergi.nbang_v1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.bergi.nbang_v1.data.Post // Post.kt 경로가 맞는지 확인
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
// import com.google.firebase.firestore.ktx.toObject // 이미 Post 클래스가 있으므로 직접 사용
import com.google.firebase.ktx.Firebase
import java.util.Date

class PostDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var postId: String? = null
    private var currentPost: Post? = null // 로드된 Post 객체를 저장

    // UI 요소
    private lateinit var categoryTextView: TextView
    private lateinit var timestampTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var contentTextView: TextView
    private lateinit var peopleTextView: TextView
    private lateinit var placeTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var joinButton: Button
    private lateinit var deleteButton: Button
    private lateinit var creatorNameTextView: TextView
    private lateinit var photoViewPager: ViewPager2
    private lateinit var photoCountTextView: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val TAG = "PostDetailActivity"

    companion object {
        // 'posts' 컬렉션 필드 상수
        const val POST_COLLECTION_NAME = "posts"
        const val FIELD_POST_PARTICIPANTS = "participants" // 'posts' 문서의 참여자 필드
        const val FIELD_CURRENT_PEOPLE = "currentPeople"
        const val FIELD_TOTAL_PEOPLE = "totalPeople"

        // 'chatRooms' 컬렉션 필드 상수
        const val CHAT_ROOM_COLLECTION_NAME = "chatRooms"
        const val FIELD_CHAT_ROOM_POST_ID = "postId" // 'chatRooms' 문서에서 'posts' 문서를 가리키는 필드
        const val FIELD_CHAT_ROOM_PARTICIPANTS = "participants" // 'chatRooms' 문서의 참여자 필드

        // 채팅방 ID를 전달하기 위한 인텐트 엑스트라 키
        const val CHAT_ROOM_ACTIVITY_EXTRA_KEY = "CHAT_ROOM_ID"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                Log.d(TAG, "ACCESS_FINE_LOCATION 권한 허용됨.")
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                Log.d(TAG, "ACCESS_COARSE_LOCATION 권한 허용됨.")
                getCurrentLocation()
            } else -> {
            Log.w(TAG, "위치 권한 거부됨.")
            distanceTextView.visibility = View.GONE
        }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // postId를 인텐트에서 가져옴 (두 가지 일반적인 키 모두 확인)
        postId = intent.getStringExtra("postId") ?: intent.getStringExtra("POST_ID")

        categoryTextView = findViewById(R.id.textViewDetailCategory)
        timestampTextView = findViewById(R.id.textViewDetailTimestamp)
        titleTextView = findViewById(R.id.textViewDetailTitle)
        contentTextView = findViewById(R.id.textViewDetailContent)
        peopleTextView = findViewById(R.id.textViewDetailPeople)
        placeTextView = findViewById(R.id.textViewDetailPlace)
        distanceTextView = findViewById(R.id.textViewDistance)
        joinButton = findViewById(R.id.buttonJoin)
        deleteButton = findViewById(R.id.buttonDelete)
        creatorNameTextView = findViewById(R.id.textViewCreatorNickname)
        photoViewPager = findViewById(R.id.photoViewPager)
        photoCountTextView = findViewById(R.id.photoCountTextView)

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPostDetails()

        joinButton.setOnClickListener {
            handleJoinNbang()
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadPostDetails() {
        if (postId == null) {
            handleLoadError("게시글 ID가 없습니다.")
            return
        }
        firestore.collection(POST_COLLECTION_NAME).document(postId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentPost = document.toObject(Post::class.java)
                    if (currentPost != null) {
                        currentPost!!.id = document.id // Post 객체에 문서 ID 저장
                        updateUI(currentPost!!)
                        checkLocationPermission()
                    } else {
                        handleLoadError("게시글 데이터 변환 실패.")
                    }
                } else {
                    handleLoadError("게시글을 찾을 수 없습니다.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "게시글 로딩 오류: ", exception)
                handleLoadError("게시글 로딩 실패: ${exception.message}")
            }
    }

    private fun updateUI(post: Post) {
        titleTextView.text = post.title
        categoryTextView.text = post.category
        contentTextView.text = post.content
        peopleTextView.text = "${post.currentPeople} / ${post.totalPeople}명"
        placeTextView.text = post.meetingPlaceName
        timestampTextView.text = formatTimestamp(post.timestamp)
        creatorNameTextView.text = post.creatorName

        creatorNameTextView.setOnClickListener {
            // UserProfileActivity가 실제로 존재하는지, 이름이 맞는지 확인
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USER_ID", post.creatorUid)
            startActivity(intent)
        }

        if (post.photoUrls.isNotEmpty()) {
            // PhotoAdapter가 실제로 존재하는지, 이름이 맞는지 확인
            photoViewPager.adapter = PhotoAdapter(post.photoUrls)
            photoViewPager.visibility = View.VISIBLE
            photoCountTextView.visibility = View.VISIBLE
            photoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    photoCountTextView.text = "${position + 1} / ${post.photoUrls.size}"
                }
            })
            // 사진이 있을 때만 카운트 텍스트 설정
            photoCountTextView.text = if (post.photoUrls.isNotEmpty()) "1 / ${post.photoUrls.size}" else ""
        } else {
            photoViewPager.visibility = View.GONE
            photoCountTextView.visibility = View.GONE
        }

        val currentUser = Firebase.auth.currentUser
        deleteButton.visibility = if (currentUser != null && currentUser.uid == post.creatorUid) View.VISIBLE else View.GONE

        // 참여 버튼 상태 로직 개선
        if (currentUser != null) {
            when {
                post.participants.contains(currentUser.uid) -> {
                    joinButton.text = "참여 중"
                    joinButton.isEnabled = false
                }
                post.currentPeople >= post.totalPeople -> {
                    joinButton.text = "정원 마감"
                    joinButton.isEnabled = false
                }
                currentUser.uid == post.creatorUid -> {
                    joinButton.text = "내 N빵" // 본인 게시글일 경우
                    joinButton.isEnabled = false
                }
                else -> {
                    joinButton.text = "N빵 참여하기"
                    joinButton.isEnabled = true
                }
            }
        } else { // 로그인하지 않은 사용자
            joinButton.text = "로그인 후 참여"
            joinButton.isEnabled = false // 또는 로그인 화면으로 유도
        }
    }

    private fun handleJoinNbang() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserId = currentUser.uid

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        joinButton.isEnabled = false // 중복 클릭 방지를 위해 버튼 비활성화

        // 1. 먼저 chatRooms 컬렉션에서 해당 postId를 가진 문서를 찾음
        firestore.collection(CHAT_ROOM_COLLECTION_NAME)
            .whereEqualTo(FIELD_CHAT_ROOM_POST_ID, postId) // chatRooms 문서 내의 postId 필드로 쿼리
            .limit(1) // 하나의 채팅방만 연결되어 있다고 가정
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "이 게시글에 해당하는 채팅방을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                    joinButton.isEnabled = true
                    return@addOnSuccessListener
                }
                val chatRoomDocument = querySnapshot.documents[0] // 찾은 채팅방 문서
                val chatRoomDocumentId = chatRoomDocument.id      // 채팅방 문서의 ID

                // 2. 게시글과 채팅방 문서를 함께 업데이트하는 트랜잭션 실행
                firestore.runTransaction { transaction ->
                    val postRef = firestore.collection(POST_COLLECTION_NAME).document(postId!!)
                    val postSnapshot = transaction.get(postRef)

                    if (!postSnapshot.exists()) {
                        throw FirebaseFirestoreException("게시글이 존재하지 않습니다.", FirebaseFirestoreException.Code.NOT_FOUND)
                    }

                    // DB에서 최신 참여 인원 정보 가져오기
                    val currentPeopleInDb = postSnapshot.getLong(FIELD_CURRENT_PEOPLE) ?: 0L
                    val totalPeopleInDb = postSnapshot.getLong(FIELD_TOTAL_PEOPLE) ?: Long.MAX_VALUE
                    val postParticipantsInDb = postSnapshot.get(FIELD_POST_PARTICIPANTS) as? List<String> ?: emptyList()

                    // 게시글 참여 조건 확인
                    if (postParticipantsInDb.contains(currentUserId)) {
                        throw FirebaseFirestoreException("이미 참여한 N빵입니다 (게시글 기준).", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                    }
                    if (currentPeopleInDb >= totalPeopleInDb) {
                        throw FirebaseFirestoreException("정원이 마감되었습니다.", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                    }

                    // 트랜잭션 내에서 채팅방 문서의 참여자 목록도 확인 (데이터 일관성을 위해)
                    val chatRoomRef = firestore.collection(CHAT_ROOM_COLLECTION_NAME).document(chatRoomDocumentId)
                    val chatRoomSnapshotInTransaction = transaction.get(chatRoomRef)
                    val chatParticipantsInDb = chatRoomSnapshotInTransaction.get(FIELD_CHAT_ROOM_PARTICIPANTS) as? List<String> ?: emptyList()

                    if (chatParticipantsInDb.contains(currentUserId)) {
                        // 이 경우는 posts.participants에는 없는데 chatRooms.participants에는 이미 있는 상황 (데이터 불일치 가능성)
                        Log.w(TAG, "사용자가 이미 채팅방 참여자 목록에 있으나, 게시글 참여자 목록에는 없습니다. 동기화합니다.")
                        // 정책에 따라 여기서 참여를 허용하거나 거부할 수 있음.
                        // 현재는 posts.participants를 기준으로 참여 여부를 결정했으므로 일단 진행.
                    }

                    // posts 문서 업데이트
                    transaction.update(postRef, FIELD_POST_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))
                    transaction.update(postRef, FIELD_CURRENT_PEOPLE, FieldValue.increment(1))

                    // chatRooms 문서 업데이트
                    transaction.update(chatRoomRef, FIELD_CHAT_ROOM_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))

                    chatRoomDocumentId // 성공 시 채팅방 ID를 반환하여 다음 로직에서 사용
                }.addOnSuccessListener { joinedChatRoomDocId ->
                    Toast.makeText(this, "N빵에 참여했습니다!", Toast.LENGTH_SHORT).show()

                    // 로컬 UI 업데이트
                    val updatedPeople = (currentPost?.currentPeople ?: 0) + 1
                    peopleTextView.text = "$updatedPeople / ${currentPost?.totalPeople ?: "-"}명"
                    // 로컬 currentPost 객체도 업데이트
                    currentPost?.currentPeople = updatedPeople
                    currentPost?.participants = currentPost?.participants?.plus(currentUserId) ?: listOf(currentUserId)

                    joinButton.text = "참여 중" // 버튼 상태 변경
                    joinButton.isEnabled = false // 참여했으므로 계속 비활성화

                    // 채팅방으로 이동
                    // !!! 중요: ChatRoomActivity::class.java를 실제 채팅방 Activity 클래스로 변경하세요 !!!
                    val intent = Intent(this, ChatRoomActivity::class.java)
                    intent.putExtra(CHAT_ROOM_ACTIVITY_EXTRA_KEY, joinedChatRoomDocId) // 채팅방 문서 ID 전달
                    startActivity(intent)
                    // finish() // 선택 사항: 현재 상세 액티비티 종료

                }.addOnFailureListener { e -> // 트랜잭션 실패 시
                    handleJoinFailure(e)
                }
            }
            .addOnFailureListener { e -> // 초기 채팅방 쿼리 실패 시
                Log.e(TAG, "채팅방 찾기 실패: ", e)
                Toast.makeText(this, "채팅방 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                joinButton.isEnabled = true // 버튼 다시 활성화
            }
    }

    // N빵 참여 실패 처리 함수
    private fun handleJoinFailure(e: Exception) {
        when (e) {
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.ALREADY_EXISTS -> {
                        Toast.makeText(this, e.message ?: "이미 참여한 N빵입니다.", Toast.LENGTH_SHORT).show()
                        joinButton.text = "참여 중" // 버튼 상태 확실히 하기
                        joinButton.isEnabled = false
                    }
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        Toast.makeText(this, "정원이 마감되었습니다.", Toast.LENGTH_SHORT).show()
                        joinButton.text = "정원 마감"
                        joinButton.isEnabled = false
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    else ->
                        Toast.makeText(this, "참여에 실패했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, "참여 중 오류 발생: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        Log.e(TAG, "N빵 참여 실패", e)
        // "참여 중" 또는 "정원 마감" 상태가 아니면 버튼 다시 활성화
        if (joinButton.text != "참여 중" && joinButton.text != "정원 마감") {
            joinButton.isEnabled = true
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 아무것도 하지 않음
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                val postGeoPoint = currentPost?.meetingLocation // Post 객체에서 GeoPoint 가져오기
                if (location != null && postGeoPoint != null) {
                    val distance = FloatArray(1) // 결과를 받을 배열
                    Location.distanceBetween(location.latitude, location.longitude, postGeoPoint.latitude, postGeoPoint.longitude, distance)
                    distanceTextView.text = "약 ${String.format("%.1f", distance[0] / 1000)}km 떨어져 있어요"
                    distanceTextView.visibility = View.VISIBLE
                } else {
                    distanceTextView.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                // 위치 가져오기 실패 시
                distanceTextView.visibility = View.GONE
            }
    }

    // calculateDistance 함수는 getCurrentLocation 내부 로직으로 통합되었으므로 삭제 가능
    // private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float { ... }


    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> deletePost() }
            .setNegativeButton("취소", null)
            .setIcon(android.R.drawable.ic_dialog_alert) // 안드로이드 기본 경고 아이콘
            .show()
    }

    private fun deletePost() {
        if (postId != null) {
            firestore.collection(POST_COLLECTION_NAME).document(postId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish() // 현재 액티비티 종료
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "삭제에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun handleLoadError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (!isFinishing) { // Activity가 종료 중이 아닐 때만 finish() 호출
            finish()
        }
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return "시간 정보 없음" // null일 경우 기본값
        val javaUtilDate = timestamp.toDate() // Firebase Timestamp를 java.util.Date로 변환
        val diff = Date().time - javaUtilDate.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}일 전"
            hours > 0 -> "${hours}시간 전"
            minutes > 0 -> "${minutes}분 전"
            else -> "방금 전"
        }
    }
}
