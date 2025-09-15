package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bergi.nbang_v1.data.Post
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import java.util.*

class CreatePostActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var spinnerCategory: Spinner
    private lateinit var editTextTitle: EditText
    private lateinit var editTextContent: EditText
    private lateinit var editTextPeople: EditText
    private lateinit var editTextPlace: EditText
    private lateinit var createButton: Button
    private lateinit var selectPhotoButton: Button
    private lateinit var buttonSelectPlace: Button
    private val selectedPhotos = mutableListOf<Uri>()
    private var selectedMeetingLocation: GeoPoint? = null
    private lateinit var searchAddressLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickImagesLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
        storage = Firebase.storage

        initViews()
        initLaunchers()
        setupSpinner()
        setupClickListeners()
    }

    private fun initViews() {
        spinnerCategory = findViewById(R.id.spinnerCategory)
        editTextTitle = findViewById(R.id.editTextPostTitle)
        editTextContent = findViewById(R.id.editTextPostContent)
        editTextPeople = findViewById(R.id.editTextTotalPeople)
        editTextPlace = findViewById(R.id.editTextMeetingPlace)
        createButton = findViewById(R.id.buttonCreatePost)
        selectPhotoButton = findViewById(R.id.selectPhotoButton)
        buttonSelectPlace = findViewById(R.id.buttonSelectPlace)
    }

    private fun initLaunchers() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) launchImagePicker() else Toast.makeText(this, "갤러리 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }

        pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedPhotos.clear()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) selectedPhotos.add(clipData.getItemAt(i).uri)
                } ?: result.data?.data?.let { selectedPhotos.add(it) }
                Toast.makeText(this, "${selectedPhotos.size}장의 사진이 선택되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        searchAddressLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val placeName = data?.getStringExtra("placeName")
                val latitude = data?.getDoubleExtra("latitude", 0.0)
                val longitude = data?.getDoubleExtra("longitude", 0.0)
                editTextPlace.setText(placeName)
                if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) {
                    selectedMeetingLocation = GeoPoint(latitude, longitude)
                }
            }
        }
    }

    private fun setupClickListeners() {
        selectPhotoButton.setOnClickListener { requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
        buttonSelectPlace.setOnClickListener { searchAddressLauncher.launch(Intent(this, SelectLocationActivity::class.java)) }
        createButton.setOnClickListener { if (selectedPhotos.isEmpty()) createPost(emptyList()) else uploadPhotos() }
    }

    private fun setupSpinner() {
        val categories = listOf("음식 배달", "생필품 공동구매", "대리구매 원해요", "대리구매 해드려요", "택시 합승", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        pickImagesLauncher.launch(intent)
    }

    private fun uploadPhotos() {
        createButton.isEnabled = false
        val uploadTasks = selectedPhotos.map { uri ->
            val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")
            photoRef.putFile(uri).continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                photoRef.downloadUrl
            }
        }
        Tasks.whenAllSuccess<Uri>(uploadTasks).addOnSuccessListener { downloadUrls ->
            createPost(downloadUrls.map { it.toString() })
        }.addOnFailureListener { e ->
            Toast.makeText(this, "사진 업로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
            createButton.isEnabled = true
        }
    }

    private fun createPost(photoUrls: List<String>) {
        val currentUser = auth.currentUser ?: return
        val creatorUid = currentUser.uid
        val title = editTextTitle.text.toString().trim()
        val totalPeople = editTextPeople.text.toString().trim().toIntOrNull()
        val meetingLocation = selectedMeetingLocation

        if (title.isEmpty() || totalPeople == null || totalPeople <= 1 || meetingLocation == null) {
            Toast.makeText(this, "필수 항목(제목, 인원, 장소)을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        createButton.isEnabled = false
        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(meetingLocation.latitude, meetingLocation.longitude))
        val initialParticipants = listOf(creatorUid)
        val newPost = Post(
            title = title,
            content = editTextContent.text.toString().trim(),
            category = spinnerCategory.selectedItem.toString(),
            creatorName = currentUser.displayName ?: "익명",
            photoUrls = photoUrls,
            totalPeople = totalPeople,
            meetingPlaceName = editTextPlace.text.toString().trim(),
            meetingLocation = meetingLocation,
            geohash = geohash,
            creatorUid = creatorUid,
            participants = initialParticipants
        )

        firestore.collection("posts").add(newPost).addOnSuccessListener { postDocument ->
            val postId = postDocument.id
            val initialCompletionStatus = mapOf(creatorUid to false)
            val chatRoomData = hashMapOf(
                "postId" to postId,
                "creatorUid" to creatorUid,
                "participants" to initialParticipants,
                "postTitle" to title,
                "lastMessage" to "채팅방이 개설되었습니다.",
                "lastMessageTimestamp" to Timestamp.now(),
                "status" to "모집중",
                "completionStatus" to initialCompletionStatus,
                "isDealFullyCompleted" to false
            )
            firestore.collection("chatRooms").document(postId).set(chatRoomData).addOnSuccessListener {
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "채팅방 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                createButton.isEnabled = true
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "게시글 등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
        }
    }
}