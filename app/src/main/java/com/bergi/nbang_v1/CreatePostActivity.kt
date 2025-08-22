package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class CreatePostActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var spinnerCategory: Spinner
    private lateinit var editTextTitle: EditText
    private lateinit var editTextContent: EditText
    private lateinit var editTextPeople: EditText
    private lateinit var editTextPlace: EditText
    private lateinit var createButton: Button

    private val TAG = "CreatePostActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        spinnerCategory = findViewById(R.id.spinnerCategory)
        editTextTitle = findViewById(R.id.editTextPostTitle)
        editTextContent = findViewById(R.id.editTextPostContent)
        editTextPeople = findViewById(R.id.editTextTotalPeople)
        editTextPlace = findViewById(R.id.editTextMeetingPlace)
        createButton = findViewById(R.id.buttonCreatePost)

        setupSpinner()

        createButton.setOnClickListener {
            uploadPost()
        }
    }

    private fun setupSpinner() {
        val categories = listOf("음식 배달", "생필품 공동구매","대리구매 원해요","대리구매 해드려요", "택시 합승", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun uploadPost() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val category = spinnerCategory.selectedItem.toString()
        val title = editTextTitle.text.toString().trim()
        val content = editTextContent.text.toString().trim()
        val peopleStr = editTextPeople.text.toString().trim()
        val place = editTextPlace.text.toString().trim()

        if (title.isEmpty() || content.isEmpty() || peopleStr.isEmpty() || place.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val totalPeople = peopleStr.toIntOrNull()

        if (totalPeople == null || totalPeople <= 1) {
            Toast.makeText(this, "인원(2명 이상)을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val newPost = Post(
            title = title,
            content = content,
            category = category,
            totalPeople = totalPeople,
            meetingPlace = place,
            creatorUid = currentUser.uid,
            participants = listOf(currentUser.uid)
        )

        createButton.isEnabled = false // 버튼 중복 클릭 방지

        firestore.collection("posts")
            .add(newPost)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Post uploaded successfully: ${documentReference.id}")
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, PostDetailActivity::class.java)
                intent.putExtra("POST_ID", documentReference.id)
                startActivity(intent)
                finish() // 상세 화면으로 넘어간 후, 현재 화면은 종료
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading post", e)
                Toast.makeText(this, "게시글 등록에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true // 실패 시 버튼 다시 활성화
            }
    }
}