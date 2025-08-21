package com.bergi.nbang_v1

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore // Firestore import 추가
import com.google.firebase.ktx.Firebase

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // Firestore 변수 추가
    private lateinit var nicknameEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance() // Firestore 초기화
        nicknameEditText = findViewById(R.id.editTextNickname)
        saveButton = findViewById(R.id.buttonSaveChanges)

        val currentUser = auth.currentUser
        nicknameEditText.setText(currentUser?.displayName)

        saveButton.setOnClickListener {
            val newNickname = nicknameEditText.text.toString().trim()

            if (newNickname.isEmpty()) {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newNickname)
                .build()

            currentUser?.updateProfile(profileUpdates)
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // --- Firestore DB 업데이트 로직 (이 부분이 핵심!) ---
                        db.collection("users").document(currentUser.uid)
                            .update("nickname", newNickname)
                            .addOnSuccessListener {
                                Toast.makeText(this, "닉네임이 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "DB 업데이트에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        // --- 여기까지 ---
                    } else {
                        Toast.makeText(this, "닉네임 변경에 실패했습니다: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}