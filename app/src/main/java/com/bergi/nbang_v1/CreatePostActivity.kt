package com.bergi.nbang_v1

import com.bergi.nbang_v1.data.Post
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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import androidx.activity.result.contract.ActivityResultContracts
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FieldValue
// InputStream은 uploadPhotos 함수 내에서만 사용되므로 여기서는 제거해도 무방.
// import java.io.InputStream
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
            try {
                // contentResolver.openInputStream(uri)가 null을 반환할 수 있으므로,
                // 이를 안전 호출(.?)과 let 스코프 함수로 처리합니다.
                contentResolver.openInputStream(uri)?.let { inputStream -> // inputStream이 null이 아닐 때만 실행
                    val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")
                    photoRef.putStream(inputStream).continueWithTask { task ->
                        // inputStream은 여기서 닫지 않습니다. Firebase SDK가 관리합니다.
                        if (!task.isSuccessful) {
                            task.exception?.let { throw it }
                        }
                        photoRef.downloadUrl
                    }
                } // inputStream이 null이면 mapNotNull에 의해 null이 반환되어 걸러집니다.
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing upload for URI: $uri", e)
                null // 오류 발생 시 null을 반환하여 이 URI는 업로드 목록에서 제외
            }
        }

        if (uploadTasks.isEmpty() && selectedPhotos.isNotEmpty()) {
            Log.w(TAG, "Failed to prepare any photos for upload or all input streams were null.")
            Toast.makeText(this, "사진을 업로드 준비하는데 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
            createButton.isEnabled = true
            return
        }

        if (uploadTasks.isEmpty() && selectedPhotos.isEmpty()) {
            createPost(emptyList()) // 업로드할 사진이 애초에 없었던 경우
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
        val keywords = title.split(" ").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val peopleStr = editTextPeople.text.toString().trim()
        val place = editTextPlace.text.toString().trim()

        if (title.isEmpty() || content.isEmpty() || peopleStr.isEmpty()) {
            Toast.makeText(this, "제목, 내용, 인원은 필수 항목입니다.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        if (selectedMeetingLocation == null) {
            Toast.makeText(this, "만날 장소를 선택해주세요.", Toast.LENGTH_SHORT).show()
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
                Log.d(TAG, "Calculated Geohash: $newGeohash for location: $selectedMeetingLocation")
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating Geohash", e)
            }
        } else {
            Log.d(TAG, "selectedMeetingLocation is null, Geohash will be null.")
        }

        val newPost = Post(
            title = title,
            content = content,
            category = category,
            creatorName = creatorName,
            photoUrls = photoUrls,
            totalPeople = totalPeople,
            meetingPlaceName = place,
            meetingLocation = selectedMeetingLocation,
            geohash = newGeohash,
            creatorUid = currentUser.uid,
            participants = listOf(currentUser.uid),
            keywords = keywords
        )

        firestore.collection("posts")
            .add(newPost)
            .addOnSuccessListener { documentReference ->
                val postId = documentReference.id
                Log.d(TAG, "Post uploaded successfully: $postId. Geohash: $newGeohash")
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()

                // 채팅방 생성 로직
                // ChatRoom 데이터 클래스에 맞게 수정
                val chatRoomData = hashMapOf(
                    "postId" to postId,
                    "postTitle" to newPost.title,
                    "creatorUid" to currentUser.uid,
                    "participants" to listOf(currentUser.uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "lastMessage" to "" // "lastMessageText"를 "lastMessage"로 변경
                )

                firestore.collection("chatRooms").document(postId)
                    .set(chatRoomData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Chat room created successfully for post ID: $postId")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error creating chat room for post ID: $postId", e)
                    }

                val intent = Intent(this, PostDetailActivity::class.java)
                intent.putExtra("POST_ID", postId)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading post", e)
                Toast.makeText(this, "게시글 등록에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }
    }
}
