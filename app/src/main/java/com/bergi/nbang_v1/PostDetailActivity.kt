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
import com.bergi.nbang_v1.PhotoAdapter
import androidx.appcompat.widget.Toolbar
import com.bergi.nbang_v1.data.Post
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import java.util.Date

class PostDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private val categoryTextView: TextView by lazy { findViewById(R.id.textViewDetailCategory) }
    private val timestampTextView: TextView by lazy { findViewById(R.id.textViewDetailTimestamp) }
    private val titleTextView: TextView by lazy { findViewById(R.id.textViewDetailTitle) }
    private val contentTextView: TextView by lazy { findViewById(R.id.textViewDetailContent) }
    private val peopleTextView: TextView by lazy { findViewById(R.id.textViewDetailPeople) }
    private val placeTextView: TextView by lazy { findViewById(R.id.textViewDetailPlace) }
    private val distanceTextView: TextView by lazy { findViewById(R.id.textViewDistance) }
    private val joinButton: Button by lazy { findViewById(R.id.buttonJoin) }
    private val deleteButton: Button by lazy { findViewById(R.id.buttonDelete) }
    private val creatorNameTextView: TextView by lazy { findViewById(R.id.textViewCreatorNickname) }
    private val photoViewPager: ViewPager2 by lazy { findViewById(R.id.photoViewPager) }
    private val photoCountTextView: TextView by lazy { findViewById(R.id.photoCountTextView) }
    private val locationMapView: MapView by lazy { findViewById(R.id.mapViewLocation) }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var postId: String? = null
    private var currentPost: Post? = null
    private var naverMap: NaverMap? = null

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val TAG = "PostDetailActivity"

    companion object {
        const val POST_COLLECTION_NAME = "posts"
        const val FIELD_POST_PARTICIPANTS = "participants"
        const val FIELD_CURRENT_PEOPLE = "currentPeople"
        const val FIELD_TOTAL_PEOPLE = "totalPeople"
        const val CHAT_ROOM_COLLECTION_NAME = "chatRooms"
        const val FIELD_CHAT_ROOM_PARTICIPANTS = "participants"
        const val CHAT_ROOM_ACTIVITY_EXTRA_KEY = "CHAT_ROOM_ID"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            getCurrentLocation()
        } else {
            distanceTextView.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        val toolbar: Toolbar = findViewById(R.id.toolbarPostDetail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 뒤로가기 버튼(<) 활성화
        supportActionBar?.setDisplayShowTitleEnabled(false) // 기본 제목 비활성화

        toolbar.setNavigationOnClickListener {
            finish()
        }

        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth
        postId = intent.getStringExtra("postId") ?: intent.getStringExtra("POST_ID")

        locationMapView.onCreate(savedInstanceState)
        locationMapView.getMapAsync(this)

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val mapOverlay = findViewById<View>(R.id.mapClickOverlay)
        mapOverlay.setOnClickListener {
            currentPost?.meetingLocation?.let { geoPoint ->
                val intent = Intent(this, MapDetailActivity::class.java).apply {
                    putExtra("latitude", geoPoint.latitude)
                    putExtra("longitude", geoPoint.longitude)
                }
                startActivity(intent)
            }
        }

        loadPostDetails()
        setupClickListeners()
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        val uiSettings = naverMap.uiSettings
        uiSettings.isScrollGesturesEnabled = false
        uiSettings.isZoomGesturesEnabled = false
        uiSettings.isTiltGesturesEnabled = false
        uiSettings.isRotateGesturesEnabled = false

        currentPost?.meetingLocation?.let { geoPoint ->
            showLocationOnMap(geoPoint.latitude, geoPoint.longitude)
        }
    }

    private fun setupClickListeners() {
        joinButton.setOnClickListener { handleJoinNbang() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
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
                        currentPost!!.id = document.id
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
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USER_ID", post.creatorUid)
            startActivity(intent)
        }

        post.meetingLocation?.let { geoPoint ->
            showLocationOnMap(geoPoint.latitude, geoPoint.longitude)
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
            photoCountTextView.text = "1 / ${post.photoUrls.size}"
        } else {
            photoViewPager.visibility = View.GONE
            photoCountTextView.visibility = View.GONE
        }

        val currentUser = auth.currentUser
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

    private fun showLocationOnMap(latitude: Double, longitude: Double) {
        naverMap?.let { map ->
            val meetingPoint = LatLng(latitude, longitude)
            map.moveCamera(CameraUpdate.scrollAndZoomTo(meetingPoint, 16.0))
            Marker().apply {
                position = meetingPoint
                this.map = map
            }
        }
    }

    private fun handleJoinNbang() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserId = currentUser.uid

        if (postId == null) {
            Toast.makeText(this, "게시글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        joinButton.isEnabled = false

        val chatRoomDocumentId = postId!!
        val postRef = firestore.collection(POST_COLLECTION_NAME).document(postId!!)
        val chatRoomRef = firestore.collection(CHAT_ROOM_COLLECTION_NAME).document(chatRoomDocumentId)

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            if (!postSnapshot.exists()) throw FirebaseFirestoreException("게시글이 존재하지 않습니다.", FirebaseFirestoreException.Code.NOT_FOUND)

            val currentPeopleInDb = postSnapshot.getLong(FIELD_CURRENT_PEOPLE) ?: 0L
            val totalPeopleInDb = postSnapshot.getLong(FIELD_TOTAL_PEOPLE) ?: Long.MAX_VALUE
            val postParticipantsInDb = postSnapshot.get(FIELD_POST_PARTICIPANTS) as? List<String> ?: emptyList()

            if (postParticipantsInDb.contains(currentUserId)) throw FirebaseFirestoreException("이미 참여한 N빵입니다.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
            if (currentPeopleInDb >= totalPeopleInDb) throw FirebaseFirestoreException("정원이 마감되었습니다.", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

            transaction.update(postRef, FIELD_POST_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))
            transaction.update(postRef, FIELD_CURRENT_PEOPLE, FieldValue.increment(1))
            transaction.update(chatRoomRef, FIELD_CHAT_ROOM_PARTICIPANTS, FieldValue.arrayUnion(currentUserId))
            chatRoomDocumentId
        }.addOnSuccessListener { joinedChatRoomDocId ->
            Toast.makeText(this, "N빵에 참여했습니다!", Toast.LENGTH_SHORT).show()
            currentPost?.let {
                it.currentPeople += 1
                it.participants = it.participants.plus(currentUserId)
                updateUI(it)
            }
            val intent = Intent(this, ChatRoomActivity::class.java)
            intent.putExtra(CHAT_ROOM_ACTIVITY_EXTRA_KEY, joinedChatRoomDocId)
            startActivity(intent)
        }.addOnFailureListener { e ->
            handleJoinFailure(e)
        }
    }

    private fun handleJoinFailure(e: Exception) {
        val message = when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.ALREADY_EXISTS -> "이미 참여한 N빵입니다."
                FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "정원이 마감되었습니다."
                FirebaseFirestoreException.Code.NOT_FOUND -> "게시글 정보를 찾을 수 없습니다."
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "참여 권한이 없습니다."
                else -> "참여에 실패했습니다: ${e.localizedMessage}"
            }
            else -> "참여 중 오류 발생: ${e.localizedMessage}"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.e(TAG, "N빵 참여 실패", e)
        loadPostDetails()
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val postGeoPoint = currentPost?.meetingLocation
            if (location != null && postGeoPoint != null) {
                val distance = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, postGeoPoint.latitude, postGeoPoint.longitude, distance)
                distanceTextView.text = "약 ${String.format("%.1f", distance[0] / 1000)}km 떨어져 있어요"
                distanceTextView.visibility = View.VISIBLE
            } else {
                distanceTextView.visibility = View.GONE
            }
        }.addOnFailureListener {
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
        val diff = Date().time - timestamp.toDate().time
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}일 전"
            hours > 0 -> "${hours}시간 전"
            minutes > 0 -> "${minutes}분 전"
            else -> "방금 전"
        }
    }

    override fun onStart() {
        super.onStart()
        locationMapView.onStart()
    }
    override fun onResume() {
        super.onResume()
        locationMapView.onResume()
    }
    override fun onPause() {
        super.onPause()
        locationMapView.onPause()
    }
    override fun onStop() {
        super.onStop()
        locationMapView.onStop()
    }
    override fun onDestroy() {
        super.onDestroy()
        locationMapView.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        locationMapView.onSaveInstanceState(outState)
    }
    override fun onLowMemory() {
        super.onLowMemory()
        locationMapView.onLowMemory()
    }
}