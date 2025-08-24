package com.bergi.nbang_v1

import android.Manifest
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

    private val TAG = "PostDetailActivity"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Fine location access granted.
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                getCurrentLocation()
            }
            else -> {
                // No location access granted.
                distanceTextView.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        firestore = FirebaseFirestore.getInstance()
        postId = intent.getStringExtra("POST_ID")
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
            Toast.makeText(this, "게시글 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPostDetails()

        joinButton.setOnClickListener {
            Toast.makeText(this, "N빵 참여 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadPostDetails() {
        firestore.collection("posts").document(postId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentPost = document.toObject(Post::class.java)
                    if (currentPost != null) {
                        updateUI(currentPost!!)
                        checkLocationPermission()
                    } else {
                        handleLoadError()
                    }
                } else {
                    handleLoadError()
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting document: ", exception)
                handleLoadError()
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null && currentPost != null) {
                    val distance = calculateDistance(
                        location.latitude,
                        location.longitude,
                        currentPost!!.latitude,
                        currentPost!!.longitude
                    )
                    distanceTextView.text = "약 ${String.format("%.1f", distance / 1000)}km 떨어져 있어요"
                    distanceTextView.visibility = View.VISIBLE
                } else {
                    distanceTextView.visibility = View.GONE
                }
            }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("정말 이 게시글을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                deletePost()
            }
            // 다른 문법으로 변경된 부분
            .setNegativeButton("취소") { _, _ ->
                // 취소 버튼을 눌렀을 때 아무것도 하지 않습니다.
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deletePost() {
        if (postId != null) {
            firestore.collection("posts").document(postId!!)
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

    private fun handleLoadError() {
        Toast.makeText(this, "게시글 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
        finish()
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