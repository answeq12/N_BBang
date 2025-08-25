package com.bergi.nbang_v1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log // Log 임포트 확인
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
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

    private val TAG = "PostDetailActivity" // 로그 태그

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                Log.d(TAG, "ACCESS_FINE_LOCATION permission granted.")
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                Log.d(TAG, "ACCESS_COARSE_LOCATION permission granted.")
                getCurrentLocation()
            }
            else -> {
                Log.w(TAG, "Location permissions denied.")
                distanceTextView.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate CALLED. Instance: ${System.identityHashCode(this)}")
        Log.d(TAG, "Intent received: $intent")
        if (intent.extras != null) {
            val extras = intent.extras
            val keys = extras?.keySet()?.joinToString(", ") ?: "null"
            Log.d(TAG, "Intent extras keys: $keys")
            // "postId" 키로 전달되는 값을 확인
            extras?.getString("postId")?.let { Log.d(TAG, "Intent extra 'postId' (from key 'postId'): $it")}
            // 혹시 다른 키로 전달될 가능성도 확인 (예: "POST_ID")
            extras?.getString("POST_ID")?.let { Log.d(TAG, "Intent extra 'POST_ID' (from key 'POST_ID'): $it")}
        } else {
            Log.d(TAG, "Intent has no extras.")
        }

        setContentView(R.layout.activity_post_detail)

        firestore = FirebaseFirestore.getInstance()

        // Intent에서 postId를 가져오는 부분 수정
        var receivedPostId: String? = intent.getStringExtra("postId") // 먼저 "postId" 키로 시도
        if (receivedPostId == null) {
            Log.w(TAG, "'postId' key not found or value is null in onCreate, trying 'POST_ID' key.")
            receivedPostId = intent.getStringExtra("POST_ID") // "postId"가 없으면 "POST_ID" 키로 다시 시도
        }
        postId = receivedPostId // 최종적으로 찾은 값을 postId 변수에 할당

        Log.d(TAG, "Final retrieved postId in onCreate (tried 'postId' then 'POST_ID'): $postId")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // UI 요소 초기화
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
            Log.w(TAG, "postId is null in onCreate after checking both keys. Showing error toast and finishing.")
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "postId is '$postId' in onCreate, proceeding to loadPostDetails.")
        loadPostDetails()

        joinButton.setOnClickListener {
            Toast.makeText(this, "N빵 참여 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent CALLED. Instance: ${System.identityHashCode(this)}")
        if (intent != null) {
            Log.d(TAG, "onNewIntent - Intent received: $intent")
            if (intent.extras != null) {
                val extras = intent.extras
                val keys = extras?.keySet()?.joinToString(", ") ?: "null"
                Log.d(TAG, "onNewIntent - Intent extras keys: $keys")
                extras?.getString("postId")?.let { Log.d(TAG, "onNewIntent - Intent extra 'postId' (from key 'postId'): $it")}
                extras?.getString("POST_ID")?.let { Log.d(TAG, "onNewIntent - Intent extra 'POST_ID' (from key 'POST_ID'): $it")}
            } else {
                Log.d(TAG, "onNewIntent - Intent has no extras.")
            }
            setIntent(intent) // 중요: 새로운 인텐트로 설정

            var receivedPostIdFromNewIntent: String? = getIntent().getStringExtra("postId")
            if (receivedPostIdFromNewIntent == null) {
                Log.w(TAG, "onNewIntent - 'postId' key not found or value is null, trying 'POST_ID' key.")
                receivedPostIdFromNewIntent = getIntent().getStringExtra("POST_ID")
            }
            postId = receivedPostIdFromNewIntent // 최종적으로 찾은 값을 postId 변수에 할당
            Log.d(TAG, "onNewIntent - Final retrieved postId (tried 'postId' then 'POST_ID'): $postId")

            if (postId == null) {
                Log.w(TAG, "onNewIntent - postId is null after checking both keys. Showing error toast and finishing.")
                Toast.makeText(this, "게시글 정보를 불러올 수 없습니다. (onNewIntent)", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            Log.d(TAG, "onNewIntent - postId is '$postId', proceeding to loadPostDetails.")
            loadPostDetails()
        } else {
            Log.d(TAG, "onNewIntent - Received null intent.")
        }
    }

    private fun loadPostDetails() {
        Log.d(TAG, "loadPostDetails CALLED for postId: $postId")
        if (postId == null) {
            Log.e(TAG, "loadPostDetails - postId is null, cannot load details.")
            if (!isFinishing) {
                handleLoadError()
            }
            return
        }
        firestore.collection("posts").document(postId!!)
            .get()
            .addOnSuccessListener { document ->
                Log.d(TAG, "loadPostDetails - Firestore get success for postId: $postId. Document exists: ${document?.exists()}")
                if (document != null && document.exists()) {
                    currentPost = document.toObject(Post::class.java)
                    if (currentPost != null) {
                        Log.d(TAG, "loadPostDetails - Successfully converted document to Post object.")
                        updateUI(currentPost!!)
                        checkLocationPermission()
                    } else {
                        Log.e(TAG, "loadPostDetails - Failed to convert document to Post object (currentPost is null).")
                        handleLoadError()
                    }
                } else {
                    Log.w(TAG, "loadPostDetails - Document does not exist for postId: $postId")
                    handleLoadError()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "loadPostDetails - Error getting document for postId: $postId", exception)
                handleLoadError()
            }
    }

    private fun updateUI(post: Post) {
        Log.d(TAG, "updateUI CALLED for post title: ${post.title}")
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
            photoCountTextView.text = "1 / ${post.photoUrls.size}"
        } else {
            photoViewPager.visibility = View.GONE
            photoCountTextView.visibility = View.GONE
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && currentUser.uid == post.creatorUid) {
            deleteButton.visibility = View.VISIBLE
        } else {
            deleteButton.visibility = View.GONE
        }
    }

    private fun checkLocationPermission() {
        Log.d(TAG, "checkLocationPermission CALLED.")
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "ACCESS_FINE_LOCATION already granted. Getting current location.")
            getCurrentLocation()
        } else {
            Log.d(TAG, "Requesting location permissions (FINE and COARSE).")
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        Log.d(TAG, "getCurrentLocation CALLED.")
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "getCurrentLocation - Location permissions not granted. Cannot get location.")
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.d(TAG, "getCurrentLocation - FusedLocationClient success. Location: $location")
                val postLocation = currentPost?.meetingLocation
                Log.d(TAG, "getCurrentLocation - currentPost meetingLocation: $postLocation")
                if (location != null && postLocation != null) {
                    val distance = calculateDistance(
                        location.latitude,
                        location.longitude,
                        postLocation.latitude,
                        postLocation.longitude
                    )
                    Log.d(TAG, "getCurrentLocation - Calculated distance: $distance meters.")
                    distanceTextView.text = "약 ${String.format("%.1f", distance / 1000)}km 떨어져 있어요"
                    distanceTextView.visibility = View.VISIBLE
                } else {
                    Log.d(TAG, "getCurrentLocation - User location or post location is null. Hiding distanceTextView.")
                    distanceTextView.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getCurrentLocation - FusedLocationClient failed.", e)
                distanceTextView.visibility = View.GONE
            }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun showDeleteConfirmationDialog() {
        Log.d(TAG, "showDeleteConfirmationDialog CALLED.")
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                Log.d(TAG, "Delete confirmation - Positive button clicked.")
                deletePost()
            }
            .setNegativeButton("취소", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deletePost() {
        Log.d(TAG, "deletePost CALLED for postId: $postId")
        if (postId != null) {
            firestore.collection("posts").document(postId!!)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "deletePost - Successfully deleted post: $postId")
                    Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "deletePost - Failed to delete post: $postId", e)
                    Toast.makeText(this, "삭제에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.w(TAG, "deletePost - postId is null. Cannot delete.")
        }
    }

    private fun handleLoadError() {
        Log.e(TAG, "handleLoadError CALLED. postId: $postId. Finishing activity.")
        Toast.makeText(this, "게시글 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
        if (!isFinishing) {
            finish()
        }
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return ""
        val diff = Date().time - timestamp.toDate().time
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
