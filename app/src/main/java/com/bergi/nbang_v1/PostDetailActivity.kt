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
import com.google.firebase.ktx.Firebase
import java.util.Date

class PostDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var postId: String? = null
    private var currentPost: Post? = null

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

    // UI 상태 관리를 위한 플래그
    private var isCreator = false
    private var isJoined = false
    private var isFull = false
    private var chatRoomExists = false

    private val TAG = "PostDetailActivity"

    companion object {
        const val POST_COLLECTION_NAME = "posts"
        const val FIELD_POST_PARTICIPANTS = "participants"
        const val FIELD_CURRENT_PEOPLE = "currentPeople"
        const val FIELD_TOTAL_PEOPLE = "totalPeople"
        const val CHAT_ROOM_COLLECTION_NAME = "chatRooms"
        const val FIELD_CHAT_ROOM_POST_ID = "postId"
        const val FIELD_CHAT_ROOM_PARTICIPANTS = "participants"
        const val CHAT_ROOM_ACTIVITY_EXTRA_KEY = "CHAT_ROOM_ID"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            } else -> {
            distanceTextView.visibility = View.GONE
        }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        postId = intent.getStringExtra("postId") ?: intent.getStringExtra("POST_ID")

        initViews()

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPostAndChatRoomDetails()

        joinButton.setOnClickListener { handleJoinNbang() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
    }

    private fun initViews() {
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
    }

    private fun loadPostAndChatRoomDetails() {
        if (postId == null) { handleLoadError("게시글 ID가 없습니다."); return }

        firestore.collection(POST_COLLECTION_NAME).document(postId!!).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentPost = document.toObject(Post::class.java)
                    if (currentPost != null) {
                        currentPost!!.id = document.id
                        // 게시글 로드 성공 후, 채팅방 존재 여부 확인
                        verifyChatRoomExists(currentPost!!)
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

    private fun verifyChatRoomExists(post: Post) {
        firestore.collection(CHAT_ROOM_COLLECTION_NAME).whereEqualTo(FIELD_CHAT_ROOM_POST_ID, post.id).limit(1).get()
            .addOnSuccessListener { chatDocs ->
                chatRoomExists = !chatDocs.isEmpty
                if (!chatRoomExists) {
                    Log.e(TAG, "데이터 불일치: 게시글(id: ${post.id})에 연결된 채팅방이 없습니다.")
                }
                updateUI(post)
                checkLocationPermission()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "채팅방 확인 중 오류 발생", e)
                chatRoomExists = false // 오류 발생 시 참여 불가 처리
                updateUI(post)
                checkLocationPermission()
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
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USER_ID", post.creatorUid)
            startActivity(intent)
        }

        if (post.photoUrls.isNotEmpty()) {
            photoViewPager.adapter = PhotoAdapter(post.photoUrls)
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

        val currentUser = Firebase.auth.currentUser
        isCreator = currentUser?.uid == post.creatorUid
        isJoined = post.participants.contains(currentUser?.uid)
        isFull = post.currentPeople >= post.totalPeople

        deleteButton.visibility = if (isCreator) View.VISIBLE else View.GONE
        updateJoinButtonState()
    }

    private fun updateJoinButtonState() {
        val currentUser = Firebase.auth.currentUser
        when {
            !chatRoomExists -> {
                joinButton.text = "참여 불가 (채팅방 없음)"
                joinButton.isEnabled = false
            }
            isJoined -> {
                joinButton.text = "채팅방으로 이동"
                joinButton.isEnabled = true
            }
            isCreator -> {
                joinButton.text = "내 N빵 (채팅방 가기)"
                joinButton.isEnabled = true
            }
            isFull -> {
                joinButton.text = "정원 마감"
                joinButton.isEnabled = false
            }
            currentUser == null -> {
                joinButton.text = "로그인 후 참여"
                joinButton.isEnabled = false
            }
            else -> {
                joinButton.text = "N빵 참여하기"
                joinButton.isEnabled = true
            }
        }
    }

    private fun handleJoinNbang() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) { Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show(); return }
        if (postId == null) { Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show(); return }

        if (isJoined || isCreator) {
            firestore.collection(CHAT_ROOM_COLLECTION_NAME).whereEqualTo(FIELD_CHAT_ROOM_POST_ID, postId).limit(1).get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val chatRoomId = documents.documents[0].id
                        val intent = Intent(this, ChatRoomActivity::class.java)
                        intent.putExtra(CHAT_ROOM_ACTIVITY_EXTRA_KEY, chatRoomId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "오류: 채팅방을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "채팅방 정보를 불러오는 데 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        joinButton.isEnabled = false

        // 1. 트랜잭션 외부에서 채팅방 문서를 먼저 찾습니다.
        firestore.collection(CHAT_ROOM_COLLECTION_NAME).whereEqualTo(FIELD_CHAT_ROOM_POST_ID, postId).limit(1).get()
            .addOnSuccessListener { chatRoomSnapshot ->
                if (chatRoomSnapshot.isEmpty) {
                    Toast.makeText(this, "오류: 이 게시글의 채팅방을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                    chatRoomExists = false
                    updateJoinButtonState()
                    return@addOnSuccessListener
                }
                val chatRoomRef = chatRoomSnapshot.documents[0].reference

                // 2. 트랜잭션 실행
                firestore.runTransaction { transaction ->
                    val postRef = firestore.collection(POST_COLLECTION_NAME).document(postId!!)
                    val postSnapshot = transaction.get(postRef)

                    if (!postSnapshot.exists()) {
                        throw FirebaseFirestoreException("게시글이 더 이상 존재하지 않습니다.", FirebaseFirestoreException.Code.NOT_FOUND)
                    }

                    val postParticipants = postSnapshot.get(FIELD_POST_PARTICIPANTS) as? List<String> ?: listOf()
                    if (postParticipants.contains(currentUser.uid)) {
                        throw FirebaseFirestoreException("이미 참여한 N빵입니다.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                    }

                    val currentPeople = postSnapshot.getLong(FIELD_CURRENT_PEOPLE) ?: 0L
                    val totalPeople = postSnapshot.getLong(FIELD_TOTAL_PEOPLE) ?: Long.MAX_VALUE
                    if (currentPeople >= totalPeople) {
                        throw FirebaseFirestoreException("정원이 마감되었습니다.", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                    }

                    // 트랜잭션 내에서 문서 업데이트
                    transaction.update(postRef, FIELD_POST_PARTICIPANTS, FieldValue.arrayUnion(currentUser.uid))
                    transaction.update(postRef, FIELD_CURRENT_PEOPLE, FieldValue.increment(1))
                    transaction.update(chatRoomRef, FIELD_CHAT_ROOM_PARTICIPANTS, FieldValue.arrayUnion(currentUser.uid))

                    null // 트랜잭션 성공 시 반환값 없음
                }.addOnSuccessListener {
                    Toast.makeText(this, "N빵에 참여했습니다!", Toast.LENGTH_SHORT).show()
                    loadPostAndChatRoomDetails() // 성공 후 데이터 다시 로드
                }.addOnFailureListener { e ->
                    handleJoinFailure(e) // 실패 처리
                }
            }
            .addOnFailureListener { e ->
                // 채팅방을 찾는 것 자체를 실패한 경우
                Toast.makeText(this, "오류: 채팅방 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_LONG).show()
                updateJoinButtonState()
            }
    }

    private fun handleJoinFailure(e: Exception) {
        when (e) {
            is FirebaseFirestoreException -> {
                Toast.makeText(this, e.message ?: "참여 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                // 트랜잭션 실패의 원인에 따라 로컬 상태를 다시 업데이트하여 버튼 상태를 정확하게 맞출 수 있습니다.
                when(e.code) {
                    FirebaseFirestoreException.Code.ALREADY_EXISTS -> isJoined = true
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> isFull = true
                    else -> { /* Do nothing */ }
                }
            }
            else -> Toast.makeText(this, "참여 중 오류 발생: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        Log.e(TAG, "N빵 참여 실패", e)
        // 실패 시, 현재 로컬 상태에 기반하여 버튼 UI를 다시 올바르게 설정합니다.
        updateJoinButtonState()
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
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 연결된 채팅방도 함께 삭제됩니다.")
            .setPositiveButton("삭제") { _, _ -> deletePost() }
            .setNegativeButton("취소", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deletePost() {
        if (postId == null) { Toast.makeText(this, "오류: 게시글 ID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show(); return }

        deleteButton.isEnabled = false
        joinButton.isEnabled = false
        Toast.makeText(this, "삭제 중...", Toast.LENGTH_SHORT).show()

        val postRef = firestore.collection(POST_COLLECTION_NAME).document(postId!!)
        val chatRoomQuery = firestore.collection(CHAT_ROOM_COLLECTION_NAME).whereEqualTo(FIELD_CHAT_ROOM_POST_ID, postId)

        chatRoomQuery.get()
            .addOnSuccessListener { chatQuerySnapshot ->
                val batch = firestore.batch()
                batch.delete(postRef)

                if (!chatQuerySnapshot.isEmpty) {
                    val chatRoomDocRef = chatQuerySnapshot.documents[0].reference
                    batch.delete(chatRoomDocRef)
                    Log.d(TAG, "연결된 채팅방(${chatRoomDocRef.id})이 삭제 목록에 추가되었습니다.")
                } else {
                    Log.w(TAG, "게시글($postId)에 연결된 채팅방을 찾지 못했습니다. 게시글만 삭제됩니다.")
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "게시글과 관련 데이터가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "삭제에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                        deleteButton.isEnabled = true
                        updateJoinButtonState()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "관련 채팅방을 찾는 데 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                deleteButton.isEnabled = true
                updateJoinButtonState()
            }
    }

    private fun handleLoadError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (!isFinishing) { finish() }
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