package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.bergi.nbang_v1.data.Post
import com.bergi.nbang_v1.data.ChatRoom
import java.io.InputStream
import java.util.UUID

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

    private val TAG = "CreatePostActivity"
    private val selectedPhotos = mutableListOf<Uri>()
    private var selectedMeetingLocation: GeoPoint? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchImagePicker()
            } else {
                Toast.makeText(this, "갤러리 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedPhotos.clear()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        selectedPhotos.add(uri)
                    }
                } ?: result.data?.data?.let { uri ->
                    selectedPhotos.add(uri)
                }
                Toast.makeText(this, "${selectedPhotos.size}장의 사진이 선택되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val searchAddressLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                if (data != null) {
                    val placeName = data.getStringExtra("placeName")
                    val addressName = data.getStringExtra("addressName")
                    val latitude = data.getDoubleExtra("latitude", 0.0)
                    val longitude = data.getDoubleExtra("longitude", 0.0)

                    if (placeName != null && addressName != null) {
                        editTextPlace.setText("$placeName ($addressName)")
                        if (latitude != 0.0 || longitude != 0.0) {
                            selectedMeetingLocation = GeoPoint(latitude, longitude)
                            Log.d(TAG, "Selected place: $placeName ($addressName), Lat: $latitude, Lng: $longitude")
                        } else {
                            selectedMeetingLocation = null
                            Log.d(TAG, "Selected place (default/no coordinates): $placeName ($addressName)")
                        }
                    } else {
                        selectedMeetingLocation = null
                        editTextPlace.text = null
                        Log.d(TAG, "SearchAddressActivity result missing placeName or addressName.")
                    }
                } else {
                    selectedMeetingLocation = null
                    editTextPlace.text = null
                    Log.d(TAG, "SearchAddressActivity result data is null.")
                }
            } else {
                Log.d(TAG, "SearchAddressActivity cancelled or failed. ResultCode: ${result.resultCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
        storage = Firebase.storage

        spinnerCategory = findViewById(R.id.spinnerCategory)
        editTextTitle = findViewById(R.id.editTextPostTitle)
        editTextContent = findViewById(R.id.editTextPostContent)
        editTextPeople = findViewById(R.id.editTextTotalPeople)
        editTextPlace = findViewById(R.id.editTextMeetingPlace)
        createButton = findViewById(R.id.buttonCreatePost)
        selectPhotoButton = findViewById(R.id.selectPhotoButton)
        buttonSelectPlace = findViewById(R.id.buttonSelectPlace)

        setupSpinner()

        selectPhotoButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        buttonSelectPlace.setOnClickListener {
            Log.d(TAG, "buttonSelectPlace clicked")
            val intent = Intent(this, SelectLocationActivity::class.java)
            searchAddressLauncher.launch(intent)
        }

        createButton.setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                createPost(emptyList())
            } else {
                uploadPhotos()
            }
        }
    }

    private fun setupSpinner() {
        val categories = listOf("음식 배달", "생필품 공동구매", "대리구매 원해요", "대리구매 해드려요", "택시 합승", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        pickImagesLauncher.launch(intent)
    }

    private fun uploadPhotos() {
        createButton.isEnabled = false
        val uploadTasks = selectedPhotos.mapNotNull { uri ->
            var inputStream: InputStream? = null
            try {
                inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")
                    photoRef.putStream(inputStream).continueWithTask { task ->
                        if (!task.isSuccessful) {
                            task.exception?.let { throw it }
                        }
                        photoRef.downloadUrl
                    }
                } else {
                    null
                }
            } finally {
                inputStream?.close()
            }
        }

        if (uploadTasks.isEmpty() && selectedPhotos.isNotEmpty()) {
            Log.w(TAG, "Failed to open input stream for some or all photos.")
            Toast.makeText(this, "일부 사진을 처리하는데 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
            createButton.isEnabled = true
            return
        }

        if (uploadTasks.isEmpty() && selectedPhotos.isEmpty()) {
            createPost(emptyList())
            return
        }

        Tasks.whenAllSuccess<Uri>(uploadTasks)
            .addOnSuccessListener { downloadUrls ->
                val photoUrls = downloadUrls.map { it.toString() }
                createPost(photoUrls)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading photos", e)
                Toast.makeText(this, "사진 업로드에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }
    }

    private fun createPost(photoUrls: List<String>) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        val creatorName = currentUser.displayName ?: "익명"

        val category = spinnerCategory.selectedItem.toString()
        val title = editTextTitle.text.toString().trim()
        val content = editTextContent.text.toString().trim()
        val peopleStr = editTextPeople.text.toString().trim()
        val place = editTextPlace.text.toString().trim()

        if (title.isEmpty() || content.isEmpty() || peopleStr.isEmpty() || place.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        val totalPeople = peopleStr.toIntOrNull()

        if (totalPeople == null || totalPeople <= 1) {
            Toast.makeText(this, "인원(2명 이상)을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        var newGeohash: String? = null
        if (selectedMeetingLocation != null) {
            try {
                val geoLocation = GeoLocation(selectedMeetingLocation!!.latitude, selectedMeetingLocation!!.longitude)
                newGeohash = GeoFireUtils.getGeoHashForLocation(geoLocation)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating Geohash", e)
            }
        }

        val newPost = Post(
            creatorUid = currentUser.uid,
            title = title,
            content = content,
            category = category,
            creatorName = creatorName,
            photoUrls = photoUrls,
            totalPeople = totalPeople,
            currentPeople = 1, // 최초 작성자 포함 1명
            status = "모집중",
            participants = listOf(currentUser.uid),
            meetingPlaceName = place,
            meetingLocation = selectedMeetingLocation,
            geohash = newGeohash
        )

        firestore.collection("posts")
            .add(newPost)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Post uploaded successfully: ${documentReference.id}.")

                val postId = documentReference.id
                val postTitle = title

                val chatRoomData = ChatRoom(
                    postId = postId,
                    postTitle = postTitle,
                    participants = listOf(currentUser.uid),
                    lastMessage = "채팅방이 생성되었습니다.",
                    lastMessageTimestamp = Timestamp.now()
                )

                firestore.collection("chatRooms")
                    .document(postId)
                    .set(chatRoomData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Chat room successfully created for postId: $postId")
                        Toast.makeText(this, "게시글과 채팅방이 성공적으로 등록되었습니다.", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, PostDetailActivity::class.java)
                        intent.putExtra("POST_ID", postId)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creating chat room for postId: $postId", e)
                        Toast.makeText(this, "게시글은 등록되었으나 채팅방 생성에 실패했습니다.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, PostDetailActivity::class.java)
                        intent.putExtra("POST_ID", postId)
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading post", e)
                Toast.makeText(this, "게시글 등록에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }
    }
}