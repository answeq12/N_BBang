package com.bergi.nbang_v1

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID
import com.bergi.nbang_v1.data.ChatRoom // 1단계에서 만든 ChatRoom.kt 파일 경로
import java.util.Date                       // Date 클래스
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

    private val TAG = "CreatePostActivity"
    private val selectedPhotos = mutableListOf<Uri>()

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
                Toast.makeText(this, "${selectedPhotos.size}장의 사진이 선택되었습니다.", Toast.LENGTH_SHORT)
                    .show()
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

        setupSpinner()

        selectPhotoButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        val uploadTasks = selectedPhotos.map { uri ->
            val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")

            val inputStream = contentResolver.openInputStream(uri)

            photoRef.putStream(inputStream!!).continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                photoRef.downloadUrl
            }
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

        // --- 변경/추가된 부분 시작 ---

        // 1. 게시물(Post)과 채팅방(ChatRoom)에 사용할 ID를 미리 생성합니다.
        val newPostRef = firestore.collection("posts").document()
        val postId = newPostRef.id

        Log.d("VALUE_CHECK", "--- 입력값 확인 ---")
        Log.d("VALUE_CHECK", "Title: '$title'")
        Log.d("VALUE_CHECK", "Post ID: '$postId'")
        Log.d("VALUE_CHECK", "User UID: '${currentUser.uid}'")

        // 2. Post 객체를 생성합니다. (기존 코드와 거의 동일)
        val newPost = Post(
            id = postId, // ID 필드를 Post 데이터 클래스에 추가해주세요.
            title = title,
            content = content,
            category = category,
            creatorName = creatorName,
            photoUrls = photoUrls,
            totalPeople = totalPeople,
            meetingPlace = place,
            creatorUid = currentUser.uid,
            participants = listOf(currentUser.uid)
        )

        // 3. ChatRoom 객체를 새로 생성합니다.
        val newChatRoom = ChatRoom(
            dealId = postId,
            dealTitle = title,
            participants = listOf(currentUser.uid),
            lastMessageTimestamp = Date()
        )

        // 4. Batch Write를 사용해 Post와 ChatRoom을 '동시에' 저장합니다.
        firestore.batch().apply {
            set(newPostRef, newPost)
            set(firestore.collection("chatRooms").document(postId), newChatRoom)
        }.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Post and ChatRoom uploaded successfully: $postId")
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, PostDetailActivity::class.java)
                intent.putExtra("POST_ID", postId) // 미리 생성한 ID를 사용합니다.
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading post", e)
                Toast.makeText(this, "게시글 등록에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }

        // --- 변경/추가된 부분 끝 ---
    }
}