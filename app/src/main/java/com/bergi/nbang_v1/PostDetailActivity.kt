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
// com.bergi.nbang_v1.adapter.PhotoAdapter // PhotoAdapter import 필요
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.ktx.Firebase
import java.util.Date

class PostDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth // auth 추가
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
        const val POST_COLLECTION_NAME = "posts"
        const val FIELD_POST_PARTICIPANTS = "participants"
        const val FIELD_CURRENT_PEOPLE = "currentPeople"
        const val FIELD_TOTAL_PEOPLE = "totalPeople"

        const val CHAT_ROOM_COLLECTION_NAME = "chatRooms"
        const val FIELD_CHAT_ROOM_PARTICIPANTS = "participants" // 'chatRooms' 문서의 참여자 필드

        // 채팅방 ID를 전달하기 위한 인텐트 엑스트라 키 (ChatRoomActivity에서 받을 때 사용)
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
        setContentView(R.layout.activity_post_detail) // R.layout.activity_post_detail 필요

        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth // auth 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        postId = intent.getStringExtra("postId") ?: intent.getStringExtra("POST_ID")

        // Initialize UI components
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
            val intent = Intent(this, UserProfileActivity::class.java) // UserProfileActivity 확인
            intent.putExtra("USER_ID", post.creatorUid)
            startActivity(intent)
        }

        if (post.photoUrls.isNotEmpty()) {
            // PhotoAdapter가 필요합니다. 예시: PhotoAdapter(this, post.photoUrls)
            // photoViewPager.adapter = PhotoAdapter(post.photoUrls) // PhotoAdapter 클래스 필요
            photoViewPager.adapter = com.bergi.nbang_v1.PhotoAdapter(post.photoUrls) // 정식 경로로 PhotoAdapter 사용
            photoViewPager.visibility = View.VISIBLE
            photoCountTextView.visibility = View.VISIBLE
            photoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    photoCountTextView.text = "${position + 1} / ${post.photoUrls.size}"
                }
            })
            photoCountTextView.text = if (post.photoUrls.isNotEmpty()) "1 / ${post.photoUrls.size}" else ""
        } else {
            photoViewPager.visibility = View.GONE
            photoCountTextView.visibility = View.GONE
        }

        val currentUser = auth.currentUser // auth 사용
        deleteButton.visibility = if (currentUser != null && currentUser.uid == post.creatorUid) View.VISIBLE else View.GONE

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
                    joinButton.text = "내 N빵"
                    joinButton.isEnabled = false
                }
                else -> {
                    joinButton.text = "N빵 참여하기"
                    joinButton.isEnabled = true
                }
            }
        } else {
            joinButton.text = "로그인 후 참여"
            joinButton.isEnabled = false
        }
    }

    private fun handleJoinNbang() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserId = currentUser.uid

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        joinButton.isEnabled = false // 중복 클릭 방지

        // chatRoomId는 postId와 동일하다고 가정
        val chatRoomDocumentId = postId!!
        val postRef = firestore.collection(POST_COLLECTION_NAME).document(postId!!)
        val chatRoomRef = firestore.collection(CHAT_ROOM_COLLECTION_NAME).document(chatRoomDocumentId)

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)

            if (!postSnapshot.exists()) {
                throw FirebaseFirestoreException("게시글이 존재하지 않습니다.", FirebaseFirestoreException.Code.NOT_FOUND)
            }

            val currentPeopleInDb = postSnapshot.getLong(FIELD_CURRENT_PEOPLE) ?: 0L
            val totalPeopleInDb = postSnapshot.getLong(FIELD_TOTAL_PEOPLE) ?: Long.MAX_VALUE
            val postParticipantsInDb = postSnapshot.get(FIELD_POST_PARTICIPANTS) as? List<String> ?: emptyList()

            if (postParticipantsInDb.contains(currentUserId)) {
                throw FirebaseFirestoreException("이미 참여한 N빵입니다 (게시글 기준).", FirebaseFirestoreException.Code.ALREADY_EXISTS)
            }
            if (currentPeopleInDb >= totalPeopleInDb) {
                throw FirebaseFirestoreException("정원이 마감되었습니다.", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            // chatRooms 문서의 참여자 목록도 트랜잭션 내에서 읽을 수 있지만,
            // 보안 규칙상 아직 참여하지 않은 사용자는 chatRooms 문서를 읽지 못할 수 있으므로,
            // 여기서는 chatRooms 문서의 상태를 직접 읽기보다는 업데이트만 수행합니다.
            // 참여자 중복 추가는 FieldValue.arrayUnion이 알아서 처리합니다.

            // posts 문서 업데이트
            transaction.update(postRef, FIELD_POST_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))
            transaction.update(postRef, FIELD_CURRENT_PEOPLE, FieldValue.increment(1))

            // chatRooms 문서 업데이트
            transaction.update(chatRoomRef, FIELD_CHAT_ROOM_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))
            // 만약 chatRooms 문서가 존재하지 않을 경우를 대비하여 set(..., SetOptions.merge())를 고려할 수 있으나,
            // CreatePostActivity에서 chatRooms 문서를 postId로 생성한다고 가정합니다.
            // 혹은, 여기서 chatRoom이 없다면 생성하는 로직을 추가할 수도 있습니다.
            // (참고: set(data, SetOptions.merge())는 문서가 없으면 생성, 있으면 병합)
            // 현재 보안규칙은 create와 update가 분리되어 있으므로, 여기서는 update만 수행합니다.
            // CreatePostActivity에서 chatRoom이 postId로 반드시 생성된다고 가정.

            chatRoomDocumentId // 성공 시 채팅방 ID (postId) 반환
        }.addOnSuccessListener { joinedChatRoomDocId -> // joinedChatRoomDocId는 postId
            Toast.makeText(this, "N빵에 참여했습니다!", Toast.LENGTH_SHORT).show()

            // 로컬 UI 업데이트
            val updatedPeople = (currentPost?.currentPeople ?: 0) + 1
            peopleTextView.text = "$updatedPeople / ${currentPost?.totalPeople ?: "-"}명"
            currentPost?.currentPeople = updatedPeople
            currentPost?.participants = currentPost?.participants?.plus(currentUserId) ?: listOf(currentUserId)

            joinButton.text = "참여 중"
            joinButton.isEnabled = false

            // 채팅방으로 이동
            val intent = Intent(this, ChatRoomActivity::class.java) // ChatRoomActivity 클래스 확인
            intent.putExtra(CHAT_ROOM_ACTIVITY_EXTRA_KEY, joinedChatRoomDocId)
            startActivity(intent)
            // finish() // 선택 사항: 현재 액티비티 종료 여부

        }.addOnFailureListener { e -> // 트랜잭션 실패 시
            handleJoinFailure(e)
        }
    }

    private fun handleJoinFailure(e: Exception) {
        when (e) {
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.ALREADY_EXISTS -> {
                        Toast.makeText(this, e.message ?: "이미 참여한 N빵입니다.", Toast.LENGTH_SHORT).show()
                        joinButton.text = "참여 중"
                        joinButton.isEnabled = false
                    }
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        Toast.makeText(this, "정원이 마감되었습니다.", Toast.LENGTH_SHORT).show()
                        joinButton.text = "정원 마감"
                        joinButton.isEnabled = false
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        Toast.makeText(this, "참여 권한이 없습니다. 보안 규칙을 확인하세요.", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "참여 실패: PERMISSION_DENIED. Firestore 보안 규칙을 확인하세요.", e)
                    }
                    else ->
                        Toast.makeText(this, "참여에 실패했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, "참여 중 오류 발생: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        Log.e(TAG, "N빵 참여 실패", e)
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
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                val postGeoPoint = currentPost?.meetingLocation
                if (location != null && postGeoPoint != null) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, postGeoPoint.latitude, postGeoPoint.longitude, distance)
                    distanceTextView.text = "약 ${String.format("%.1f", distance[0] / 1000)}km 떨어져 있어요"
                    distanceTextView.visibility = View.VISIBLE
                } else {
                    distanceTextView.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                distanceTextView.visibility = View.GONE
            }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> deletePost() }
            .setNegativeButton("취소", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deletePost() {
        if (postId != null) {
            // 연관된 채팅방도 삭제할지 여부는 정책에 따라 결정
            // 예: firestore.collection(CHAT_ROOM_COLLECTION_NAME).document(postId!!).delete()
            firestore.collection(POST_COLLECTION_NAME).document(postId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "삭제에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun handleLoadError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (!isFinishing) {
            finish()
        }
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return "시간 정보 없음"
        val javaUtilDate = timestamp.toDate()
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
